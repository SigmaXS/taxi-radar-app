package com.example.taxiradar.throttle

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.example.taxiradar.LicenseSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Лицензия на «Отмену» — отдельная от радара.
 *
 * Сервер тот же (taxi-radar-license), но ключи и подписка свои:
 *  - пробный день один раз на устройство, отсчёт с первого «Включить»;
 *  - дальше ключ «CANC-…» из админки или оплата MIA.
 *
 * Как и у радара, часам телефона не верим: «доступ открыт» принимаем только из ответа сервера
 * с подписью (см. LicenseSignature), а время считаем от подписанного времени сервера и
 * SystemClock.elapsedRealtime() (его пользователь не переведёт). После перезагрузки телефона
 * отсчёт теряется — нужна новая проверка по сети.
 */
object CancelLicense {

    private const val BASE_URL = "https://taxi-radar-license-production.up.railway.app"
    private const val PREFS = "cancel_license_v2"
    private const val KEY_EXPIRES = "expires_ms"
    private const val KEY_SERVER_TIME = "server_ms"
    private const val KEY_ELAPSED = "elapsed_at"
    private const val KEY_BOOT = "boot_count"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    data class Status(
        val active: Boolean,
        val expiresAt: Long,
        val trialAvailable: Boolean,
        val hoursLeft: Int,
        val banned: Boolean = false,
    )

    @SuppressLint("HardwareIds")
    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun bootCount(context: Context): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    /** Текущее время по серверу; null — после перезагрузки или пока подписанного ответа не было. */
    private fun trustedNow(context: Context): Long? {
        val p = prefs(context)
        val serverMs = p.getLong(KEY_SERVER_TIME, 0L)
        if (serverMs == 0L) return null
        if (p.getInt(KEY_BOOT, Int.MIN_VALUE) != bootCount(context)) return null
        val since = SystemClock.elapsedRealtime() - p.getLong(KEY_ELAPSED, 0L)
        if (since < 0) return null
        return serverMs + since
    }

    /** Срок (мс по времени сервера) из последнего подписанного ответа. 0 — нет. */
    fun cachedExpires(context: Context): Long = prefs(context).getLong(KEY_EXPIRES, 0L)

    /** Можно ли сейчас работать: подписка идёт по серверному времени. Это единственная проверка для всех входов. */
    fun allowed(context: Context): Boolean {
        val now = trustedNow(context) ?: return false
        return cachedExpires(context) > now
    }

    fun isActiveNow(context: Context): Boolean = allowed(context)

    fun daysLeft(context: Context): Int {
        val now = trustedNow(context) ?: return 0
        val left = cachedExpires(context) - now
        if (left <= 0) return 0
        return Math.ceil(left / 86_400_000.0).toInt()
    }

    /** Миллисекунд до конца подписки по серверному времени; <= 0 — закончилась. */
    fun msLeft(context: Context): Long {
        val now = trustedNow(context) ?: return 0L
        return cachedExpires(context) - now
    }

    private fun remember(context: Context, serverMs: Long, expiresMs: Long) {
        prefs(context).edit()
            .putLong(KEY_SERVER_TIME, serverMs)
            .putLong(KEY_ELAPSED, SystemClock.elapsedRealtime())
            .putInt(KEY_BOOT, bootCount(context))
            .putLong(KEY_EXPIRES, expiresMs)
            .apply()
    }

    /** Сервер сказал «не активна» (с подписью или без — отказ нам не вредит): гасим локальный срок. */
    private fun forget(context: Context) {
        prefs(context).edit().putLong(KEY_EXPIRES, 0L).apply()
    }

    private class Reply(val json: JSONObject, val nonce: String)

    /**
     * Разбор ответа. Положительный (active=true) принимаем только с верной подписью и нашим nonce;
     * иначе считаем, что подтверждения нет. Возвращает Status или null (нет связи / подделка).
     */
    private fun parse(context: Context, reply: Reply?): Status? {
        val json = reply?.json ?: return null
        if (!json.optBoolean("ok", false)) {
            // Явный отказ сервера («заблокировано», «тест использован») — срок гасим только для бана.
            if (json.optBoolean("banned", false)) forget(context)
            return null
        }
        val active = json.optBoolean("active", false)
        val hours = json.optInt("hours_left", 0)
        val trialAvailable = json.optBoolean("trial_available", false)
        val banned = json.optBoolean("banned", false)
        if (!active) {
            forget(context)
            return Status(false, 0L, trialAvailable, 0, banned)
        }
        val v = LicenseSignature.verify(json, "cancel", deviceId(context), reply.nonce, true) ?: return null
        val expires = parseIso(v.expires)
        if (expires <= 0L) return null
        remember(context, v.ts, expires)
        return Status(true, expires, trialAvailable, hours, banned)
    }

    /** null — сервер недоступен или ответ не подписан (тогда решает allowed()). */
    suspend fun status(context: Context, running: Boolean = false, runs: Int = -1): Status? = withContext(Dispatchers.IO) {
        // running: скрипт «Отмена» сейчас включён — сервер отмечает «работает сейчас» в админке; runs — сколько раз сработал.
        val reply = post(context, "/api/cancel/status", JSONObject().apply {
            if (running) put("running", true)
            if (runs >= 0) put("runs", runs)
        })
        // ping: 1 — сервер отметил «работает», 0 — устройства нет в базе, -1 — ошибка записи.
        parse(context, reply)
    }

    suspend fun startTrial(context: Context): Status? = withContext(Dispatchers.IO) {
        parse(context, post(context, "/api/cancel/trial", JSONObject()))?.takeIf { it.active }
    }

    /** Заявка MIA на подписку «Отмена» на выбранное число дней (цену определяет сервер). */
    suspend fun buy(context: Context, phone: String, days: Int): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("product", "cancel")
            .put("days", days)
            .put("phone", phone)
        val json = post(context, "/api/pay/claim", body)?.json
            ?: return@withContext false to "Нет связи с сервером"
        if (!json.optBoolean("ok", false)) {
            return@withContext false to (json.optString("message").ifEmpty { "Не получилось отправить заявку" })
        }
        true to "Заявка отправлена"
    }

    suspend fun activate(context: Context, key: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val reply = post(context, "/api/cancel/activate", JSONObject().put("key", key.trim()))
            ?: return@withContext false to "Нет связи с сервером"
        val status = parse(context, reply)
        if (status != null && status.active) return@withContext true to "Ключ активирован"
        val message = reply.json.optString("message").ifEmpty { "Ключ не подошёл" }
        false to if (reply.json.optBoolean("ok", false)) "Ответ сервера не подтверждён. Попробуйте ещё раз." else message
    }

    private fun parseIso(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return try {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            fmt.parse(value)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun post(context: Context, path: String, body: JSONObject): Reply? {
        return try {
            val nonce = LicenseSignature.newNonce()
            body.put("device_id", deviceId(context)).put("nonce", nonce)
            val request = Request.Builder()
                .url(BASE_URL + path)
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (text.isEmpty()) return null
                // 4xx с JSON (бан, лимит) тоже разбираем: там понятное сообщение.
                Reply(JSONObject(text), nonce)
            }
        } catch (e: Exception) {
            null
        }
    }
}

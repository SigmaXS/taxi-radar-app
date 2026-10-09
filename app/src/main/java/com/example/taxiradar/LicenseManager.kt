package com.example.taxiradar

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Лицензия и пробный период. Решает всегда сервер (по ANDROID_ID устройства —
 * он не меняется при переустановке и очистке данных приложения).
 *
 * Часам телефона не доверяем: их можно перевести назад и «продлить» срок.
 * Вместо этого берём время из заголовка Date ответа сервера и дальше
 * отсчитываем его по SystemClock.elapsedRealtime() — это время с момента
 * включения телефона, пользователь его изменить не может. После перезагрузки
 * такой отсчёт теряется, и нужна новая проверка на сервере.
 */
class LicenseManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("taxi_radar_license_prefs", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    @SuppressLint("HardwareIds")
    val deviceId: String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: "unknown_device"

    /** Для админки: «Xiaomi M2007J3SG · Android 12 · v1.2». */
    private val deviceInfo: String by lazy {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) {
            Build.MODEL
        } else {
            "$manufacturer ${Build.MODEL}"
        }
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
        listOfNotNull(model, "Android ${Build.VERSION.RELEASE}", appVersion?.let { "v$it" })
            .joinToString(" · ")
    }

    private fun requestBody(vararg extra: Pair<String, String>): String =
        JSONObject().apply {
            put("device_id", deviceId)
            put("device_info", deviceInfo)
            extra.forEach { (k, v) -> put(k, v) }
        }.toString()

    companion object {
        private const val KEY_IS_ACTIVATED = "is_activated"
        private const val KEY_SAVED_LICENSE = "license_key"
        private const val KEY_EXPIRES = "license_expires"
        private const val KEY_TRIAL_ALREADY_USED = "trial_already_used"
        private const val KEY_SERVER_TIME = "server_time_ms"
        private const val KEY_ELAPSED_AT_SERVER_TIME = "elapsed_at_server_time_ms"
        private const val KEY_BOOT_COUNT = "boot_count"

        private const val BASE_URL = "https://taxi-radar-license-production.up.railway.app"

        /** Страница-приглашение: кнопка «Скачать» и код друга. */
        fun inviteUrl(code: String) = "$BASE_URL/invite/$code"
    }

    val trialAlreadyUsed: Boolean
        get() = prefs.getBoolean(KEY_TRIAL_ALREADY_USED, false)

    private fun parseDateMillis(dateStr: String?): Long {
        if (dateStr.isNullOrEmpty()) return 0L
        return try {
            // Сервер отдаёт ISO в UTC ("2026-10-16T19:20:58.902Z") или просто дату.
            val clean = dateStr.take(19)
            val pattern = if (clean.length <= 10) "yyyy-MM-dd" else "yyyy-MM-dd'T'HH:mm:ss"
            SimpleDateFormat(pattern, Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(clean)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun bootCount(): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private fun rememberServerTime(response: Response) {
        val serverMs = response.headers.getDate("Date")?.time ?: return
        prefs.edit()
            .putLong(KEY_SERVER_TIME, serverMs)
            .putLong(KEY_ELAPSED_AT_SERVER_TIME, SystemClock.elapsedRealtime())
            .putInt(KEY_BOOT_COUNT, bootCount())
            .apply()
    }

    /** Текущее время по серверу, или null, если с последней проверки была перезагрузка. */
    private fun trustedNow(): Long? {
        val serverMs = prefs.getLong(KEY_SERVER_TIME, 0L)
        if (serverMs == 0L) return null
        if (prefs.getInt(KEY_BOOT_COUNT, Int.MIN_VALUE) != bootCount()) return null
        val sinceCheck = SystemClock.elapsedRealtime() - prefs.getLong(KEY_ELAPSED_AT_SERVER_TIME, 0L)
        if (sinceCheck < 0) return null
        return serverMs + sinceCheck
    }

    private fun msSinceServerContact(): Long? {
        val now = trustedNow() ?: return null
        return now - prefs.getLong(KEY_SERVER_TIME, 0L)
    }

    /** Активна ли лицензия/триал прямо сейчас — по серверному времени, не по часам телефона. */
    fun isLicensed(): Boolean {
        if (!prefs.getBoolean(KEY_IS_ACTIVATED, false)) return false
        val now = trustedNow() ?: return false
        val isStillValid = parseDateMillis(prefs.getString(KEY_EXPIRES, null)) > now
        if (!isStillValid) {
            prefs.edit().putBoolean(KEY_IS_ACTIVATED, false).apply()
        }
        return isStillValid
    }

    fun getRemainingDays(): Int {
        val now = trustedNow() ?: return 0
        val diff = parseDateMillis(prefs.getString(KEY_EXPIRES, null)) - now
        return if (diff > 0) Math.ceil(diff / (1000.0 * 60 * 60 * 24)).toInt() else 0
    }

    /**
     * Запрос триала на 7 дней. Один раз на устройство — это решает сервер.
     * Локальную отметку «триал использован» ставим только когда сервер
     * действительно ответил: без интернета при первом запуске триал не сгорает.
     */
    suspend fun checkOrStartTrial(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (trialAlreadyUsed) {
            val valid = checkDeviceStatus()
            val days = getRemainingDays()
            return@withContext if (valid && days > 0) {
                Pair(true, context.getString(R.string.srv_trial_days_left, days))
            } else {
                Pair(false, context.getString(R.string.srv_trial_over))
            }
        }

        val jsonBody = requestBody()
        val request = Request.Builder()
            .url("$BASE_URL/api/request-trial")
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                rememberServerTime(response)
                val bodyStr = response.body?.string() ?: return@withContext Pair(false, context.getString(R.string.srv_error))
                val json = JSONObject(bodyStr)
                val isValid = json.optBoolean("valid", false)
                val msg = ServerText.localize(context, json.optString("message", ""))

                if (isValid) {
                    prefs.edit()
                        .putBoolean(KEY_IS_ACTIVATED, true)
                        .putBoolean(KEY_TRIAL_ALREADY_USED, true)
                        .putString(KEY_EXPIRES, json.optString("expires", ""))
                        .apply()
                    Pair(isLicensed(), msg)
                } else {
                    prefs.edit()
                        .putBoolean(KEY_IS_ACTIVATED, false)
                        .putBoolean(KEY_TRIAL_ALREADY_USED, true)
                        .apply()
                    Pair(false, msg)
                }
            }
        } catch (e: Exception) {
            Pair(false, context.getString(R.string.srv_no_connection))
        }
    }

    suspend fun activateKey(key: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim().uppercase()
        val jsonBody = requestBody("key" to cleanKey)

        val request = Request.Builder()
            .url("$BASE_URL/api/activate-device")
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                rememberServerTime(response)
                val bodyStr = response.body?.string() ?: return@withContext Pair(false, context.getString(R.string.srv_empty))
                val json = JSONObject(bodyStr)
                val isValid = json.optBoolean("valid", false)
                val message = ServerText.localize(context, json.optString("message", context.getString(R.string.srv_generic_error)))

                if (isValid) {
                    prefs.edit()
                        .putBoolean(KEY_IS_ACTIVATED, true)
                        .putString(KEY_SAVED_LICENSE, cleanKey)
                        .putString(KEY_EXPIRES, json.optString("expires", ""))
                        .apply()
                    Pair(true, message)
                } else {
                    Pair(false, message)
                }
            }
        } catch (e: Exception) {
            Pair(false, context.getString(R.string.srv_network_error))
        }
    }

    /**
     * Проверка на сервере. Без связи — решение по сохранённому сроку и
     * серверному времени (если телефон не перезагружался), иначе доступ закрыт.
     */
    suspend fun checkDeviceStatus(): Boolean = withContext(Dispatchers.IO) {
        val jsonBody = requestBody()
        val request = Request.Builder()
            .url("$BASE_URL/api/check-license")
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                rememberServerTime(response)
                val bodyStr = response.body?.string() ?: return@withContext isLicensed()
                val json = JSONObject(bodyStr)

                if (json.optBoolean("valid", false)) {
                    prefs.edit()
                        .putBoolean(KEY_IS_ACTIVATED, true)
                        .putString(KEY_EXPIRES, json.optString("expires", ""))
                        .apply()
                    isLicensed()
                } else {
                    prefs.edit()
                        .putBoolean(KEY_IS_ACTIVATED, false)
                        .remove(KEY_EXPIRES)
                        .apply()
                    false
                }
            }
        } catch (e: Exception) {
            isLicensed()
        }
    }

    private fun postJson(path: String, body: String): JSONObject? {
        val request = Request.Builder()
            .url("$BASE_URL$path")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return client.newCall(request).execute().use { response ->
            response.body?.string()?.let { JSONObject(it) }
        }
    }

    /** Контакты и ссылка на группу — задаются переменными в Railway. */
    suspend fun fetchAppConfig(): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$BASE_URL/api/app-config").build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string()?.let { JSONObject(it) } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Свой реферальный код и статистика; null — нет связи или доступ ещё не активирован. */
    suspend fun referralInfo(): JSONObject? = withContext(Dispatchers.IO) {
        try {
            postJson("/api/referral/me", requestBody())?.takeIf { it.optBoolean("ok", false) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun applyReferralCode(code: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val json = postJson("/api/referral/apply", requestBody("code" to code.trim().uppercase()))
                ?: return@withContext Pair(false, context.getString(R.string.srv_empty))
            Pair(json.optBoolean("ok", false), ServerText.localize(context, json.optString("message", context.getString(R.string.srv_generic_error))))
        } catch (e: Exception) {
            Pair(false, context.getString(R.string.srv_no_connection))
        }
    }

    /**
     * Для фоновых проверок (виджет): к серверу — не чаще раза в maxAgeMs,
     * в промежутке срок сверяется локально по серверному времени.
     */
    suspend fun checkDeviceStatusCached(maxAgeMs: Long): Boolean {
        val age = msSinceServerContact()
        return if (age != null && age < maxAgeMs) isLicensed() else checkDeviceStatus()
    }
}

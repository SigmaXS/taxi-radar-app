package com.example.taxiradar

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Помощь на дороге: водитель просит насос, прикурить, бензин или буксир и пишет, сколько
 * готов заплатить. Радары водителей рядом раз в 30 секунд забирают заявки (без Firebase) и
 * показывают уведомление; номер и точку получает только тот, кто нажал «Помогу».
 * «Опасность» — отдельная красная кнопка: всем в 10 км сразу, с номером и точкой.
 */
object RoadHelp {
    data class Kind(val key: String, val emoji: String, val ru: String, val ro: String)

    val KINDS = listOf(
        Kind("tire", "🛞", "Колесо / насос", "Roată / pompă"),
        Kind("jump", "🔋", "Прикурить", "Pornire de la baterie"),
        Kind("fuel", "⛽", "Кончился бензин или газ", "S-a terminat benzina sau gazul"),
        Kind("tow", "🚗", "Буксир", "Remorcare"),
        Kind("other", "✍️", "Другое", "Altceva")
    )
    val DANGER = Kind("danger", "🚨", "Опасность", "Pericol")

    fun kind(key: String) = KINDS.firstOrNull { it.key == key } ?: if (key == DANGER.key) DANGER else KINDS.last()
    fun label(c: Context, key: String) = kind(key).let { "${it.emoji} ${DriverUi.t(c, it.ru, it.ro)}" }

    data class Request(
        val id: Long, val kind: String, val price: Int, val note: String, val dist: Int,
        val helpers: Int, val accepted: Boolean, val phone: String?, val lat: Double?, val lon: Double?
    )

    data class Helper(val phone: String, val nickname: String, val dist: Int?)
    data class Mine(val id: Long, val kind: String, val price: Int, val helpers: List<Helper>)

    fun myPhone(c: Context) = DriverPreferences.text(c, "my_phone")

    suspend fun create(c: Context, kind: String, price: Int, phone: String, note: String, lat: Double, lon: Double): Pair<Boolean, String> {
        DriverPreferences.set(c, "my_phone", phone)
        val r = CommunityApi.post(c, "/api/help/create", JSONObject().put("kind", kind).put("price", price).put("phone", phone)
            .put("note", note).put("lat", lat).put("lon", lon)) ?: return false to c.getString(R.string.srv_no_connection)
        if (r.optBoolean("ok")) DriverPreferences.set(c, "help_open", true)
        return r.optBoolean("ok") to r.optString("message")
    }

    suspend fun nearby(c: Context, lat: Double, lon: Double): List<Request>? {
        val r = CommunityApi.post(c, "/api/help/nearby", JSONObject().put("lat", lat).put("lon", lon)) ?: return null
        val a = r.optJSONArray("requests") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { o ->
            Request(o.optLong("id"), o.optString("kind"), o.optInt("price"), o.optString("note"), o.optInt("dist"),
                o.optInt("helpers"), o.optBoolean("accepted"), o.optString("phone").ifBlank { null },
                if (o.has("lat")) o.optDouble("lat") else null, if (o.has("lon")) o.optDouble("lon") else null)
        }
    }

    /** «Помогу»: сервер отдаёт номер и точку заявки. */
    suspend fun accept(c: Context, id: Long, lat: Double?, lon: Double?): Request? {
        val body = JSONObject().put("id", id).put("phone", myPhone(c))
        if (lat != null && lon != null) body.put("lat", lat).put("lon", lon)
        val o = CommunityApi.post(c, "/api/help/accept", body)?.optJSONObject("request") ?: return null
        return Request(o.optLong("id"), o.optString("kind"), o.optInt("price"), o.optString("note"), 0, 0, true,
            o.optString("phone"), o.optDouble("lat"), o.optDouble("lon"))
    }

    /** Своя открытая заявка; null — нет связи, Mine с id 0 — заявки нет. */
    suspend fun mine(c: Context): Mine? {
        val r = CommunityApi.post(c, "/api/help/mine") ?: return null
        val o = r.optJSONObject("request") ?: run { DriverPreferences.set(c, "help_open", false); return Mine(0, "", 0, emptyList()) }
        val a = r.optJSONArray("helpers")
        val helpers = (0 until (a?.length() ?: 0)).mapNotNull { a?.optJSONObject(it) }.map {
            Helper(it.optString("phone"), it.optString("nickname"), if (it.isNull("dist")) null else it.optInt("dist"))
        }
        return Mine(o.optLong("id"), o.optString("kind"), o.optInt("price"), helpers)
    }

    suspend fun close(c: Context): Boolean {
        val ok = CommunityApi.post(c, "/api/help/close")?.optBoolean("ok") == true
        if (ok) DriverPreferences.set(c, "help_open", false)
        return ok
    }

    fun distance(c: Context, m: Int) = if (m < 1000) DriverUi.t(c, "$m м", "$m m") else DriverUi.t(c, "%.1f км", "%.1f km").format(m / 1000.0)
    fun priceText(c: Context, r: Request) = when {
        r.kind == DANGER.key -> DriverUi.t(c, "нужна помощь", "e nevoie de ajutor")
        r.price > 0 -> DriverUi.t(c, "готов заплатить ${r.price} L", "plătește ${r.price} L")
        else -> DriverUi.t(c, "за спасибо", "pe gratis")
    }

    // ---------- уведомления (вызывает радар) ----------

    private val notified = mutableSetOf<Long>()
    private var lastHelpers = -1

    /** Новые заявки рядом — уведомление. На заказе — без звука; «опасность» — всегда громко. */
    fun notifyNew(c: Context, list: List<Request>, busy: Boolean) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        channels(c, nm)
        for (r in list) {
            if (!notified.add(r.id)) continue
            val danger = r.kind == DANGER.key
            val open = PendingIntent.getActivity(c, r.id.toInt(), Intent(c, RoadHelpActivity::class.java).putExtra("request", r.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(c, if (danger) "help_danger" else "help")
                .setSmallIcon(R.drawable.ic_map)
                .setContentTitle("${label(c, r.kind)} · ${distance(c, r.dist)}")
                .setContentText(listOf(priceText(c, r), r.note).filter { it.isNotBlank() }.joinToString(" · "))
                .setPriority(if (danger) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
                .setCategory(if (danger) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
                .setSilent(busy && !danger)
                .setContentIntent(open)
                .addAction(0, DriverUi.t(c, if (danger) "Открыть" else "Помогу", if (danger) "Deschide" else "Ajut"), open)
                .setTimeoutAfter(60 * 60_000L)
                .setAutoCancel(true)
                .build()
            try { nm.notify(HELP_ID_BASE + (r.id % 1000).toInt(), n) } catch (_: SecurityException) {}
        }
    }

    /** Кто-то откликнулся на мою заявку — «Едет водитель». */
    fun notifyHelpers(c: Context, mine: Mine) {
        if (mine.id == 0L) { lastHelpers = -1; return }
        if (lastHelpers >= 0 && mine.helpers.size > lastHelpers) {
            val h = mine.helpers.last()
            val nm = c.getSystemService(NotificationManager::class.java) ?: return
            channels(c, nm)
            val open = PendingIntent.getActivity(c, 77, Intent(c, RoadHelpActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val who = h.nickname.ifBlank { DriverUi.t(c, "Водитель", "Un șofer") }
            val n = NotificationCompat.Builder(c, "help")
                .setSmallIcon(R.drawable.ic_map)
                .setContentTitle(DriverUi.t(c, "🙌 $who едет помочь", "🙌 $who vine să ajute"))
                .setContentText(listOfNotNull(h.dist?.let { distance(c, it) }, h.phone.ifBlank { null }).joinToString(" · "))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open).setAutoCancel(true).build()
            try { nm.notify(HELP_ID_BASE + 999, n) } catch (_: SecurityException) {}
        }
        lastHelpers = mine.helpers.size
    }

    private fun channels(c: Context, nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        nm.createNotificationChannel(NotificationChannel("help", DriverUi.t(c, "Помощь на дороге", "Ajutor pe drum"), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel("help_danger", DriverUi.t(c, "Опасность рядом", "Pericol în apropiere"), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
        })
    }

    private const val HELP_ID_BASE = 5000

    // ---------- место и действия ----------

    /** Свежая точка: последняя известная (до 3 минут) или одна новая от GPS до 10 секунд. */
    @SuppressLint("MissingPermission")
    suspend fun location(c: Context): Location? {
        val fine = c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = c.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val lm = c.getSystemService(android.location.LocationManager::class.java)
        val last = runCatching { lm?.getProviders(true)?.mapNotNull { lm.getLastKnownLocation(it) } }.getOrNull().orEmpty()
            .filter { System.currentTimeMillis() - it.time < 3 * 60_000 }.maxByOrNull { it.time }
        if (last != null) return last
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                LocationServices.getFusedLocationProviderClient(c).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
        }
    }

    fun call(c: Context, phone: String) {
        try { c.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }

    /** Точка в навигаторе: Яндекс Навигатор, Waze, Google — что стоит на телефоне. */
    fun navigate(c: Context, lat: Double, lon: Double) {
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { c.startActivity(Intent.createChooser(geo, DriverUi.t(c, "Открыть в навигаторе", "Deschide în navigator")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}

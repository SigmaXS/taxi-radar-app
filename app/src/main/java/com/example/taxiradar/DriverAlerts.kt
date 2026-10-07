package com.example.taxiradar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlin.math.cos

/** Optional checks live only for the lifetime of the radar service. */
class DriverAlerts {
    private var lastSurgeCheck = 0L
    private var lastAirportCheck = 0L
    private var flights: Set<String>? = null

    suspend fun check(c: Context, lat: Double?, lon: Double?, tariff: String) {
        val now = System.currentTimeMillis()
        if (DriverPreferences.flag(c, "surge_alert") && lat != null && lon != null && now - lastSurgeCheck >= 180000) {
            lastSurgeCheck = now
            val radius = DriverPreferences.number(c, "surge_radius", 2.0).coerceIn(0.5, 5.0)
            val threshold = DriverPreferences.number(c, "surge_threshold", 35.0).toInt()
            val dx = radius / (111 * cos(Math.toRadians(lat)).coerceAtLeast(0.1))
            val dy = radius / 111
            val points = listOf(lat to lon, (lat + dy) to lon, (lat - dy) to lon, lat to (lon + dx), lat to (lon - dx))
            var max: Int? = null
            for (point in points) {
                val value = YandexTaxiSurgeChecker.getSurgePrice(point.second, point.first, tariff)
                if (value != null && (max == null || value > max)) max = value
                kotlinx.coroutines.delay(1000)
            }
            val lastAlert = DriverPreferences.prefs(c).getLong("surge_alert_at", 0)
            if (max != null && max >= threshold && now - lastAlert >= 600000) {
                val title = DriverUi.t(c, "Надбавка рядом +$max L", "Supliment în apropiere +$max L")
                val message = DriverUi.t(c, "Проверено сейчас в радиусе $radius км. Выбранный тариф. Спрос может измениться до приезда.", "Verificat acum într-o rază de $radius km. Categoria selectată. Cererea se poate schimba până ajungeți.")
                if (notify(c, 301, title, message)) DriverPreferences.prefs(c).edit().putLong("surge_alert_at", now).apply()
            }
        }
        if (!DriverPreferences.flag(c, "surge_alert")) lastSurgeCheck = 0
        if (DriverPreferences.flag(c, "airport_alert") && now - lastAirportCheck >= 180000) {
            lastAirportCheck = now
            val status = Airport.status(c) ?: return
            val landed = status.flights.filter { it.status == "landed" }.map { "${it.flight}|${it.time}" }.toSet()
            val previous = flights
            if (previous != null) {
                val filter = DriverPreferences.text(c, "flight").replace(" ", "")
                status.flights.filter { "${it.flight}|${it.time}" in (landed - previous) && (filter.isEmpty() || it.flight.replace(" ", "").equals(filter, true)) }.take(3).forEach {
                    notify(c, 302, DriverUi.t(c, "Прилетел рейс ${it.flight}", "Zborul ${it.flight} a aterizat"), "${Airport.city(it.from)} · ${it.time}")
                }
            }
            flights = ((previous ?: emptySet()) + landed).toList().takeLast(200).toSet()
        }
        if (!DriverPreferences.flag(c, "airport_alert")) { flights = null; lastAirportCheck = 0 }
    }
    private fun notify(c: Context, id: Int, title: String, message: String): Boolean {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return false
        if (!nm.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) nm.createNotificationChannel(NotificationChannel("driver_alerts", DriverUi.t(c, "Спрос и прилёты", "Cerere și aterizări"), NotificationManager.IMPORTANCE_DEFAULT))
        val intent = if (id == 302) Intent(c, AirportActivity::class.java) else Intent(c, MainActivity::class.java)
        val pending = PendingIntent.getActivity(c, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return try {
            nm.notify(id, NotificationCompat.Builder(c, "driver_alerts").setSmallIcon(R.drawable.ic_my_location).setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message)).setContentIntent(pending).setAutoCancel(true).build()); true
        } catch (_: SecurityException) { false }
    }
}

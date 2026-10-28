package com.example.taxiradar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/** Optional checks live only for the lifetime of the radar service. */
class DriverAlerts {
    private var lastAirportCheck = 0L
    private var flights: Set<String>? = null

    // Надбавка рядом — в FloatingWidgetService (checkNearbySurge): только когда водитель стоит минуту.
    suspend fun check(c: Context) {
        val now = System.currentTimeMillis()
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

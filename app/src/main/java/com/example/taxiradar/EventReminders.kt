package com.example.taxiradar

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONObject

/**
 * Напоминания о событиях: за 15 минут до окончания — «скоро начнут выходить люди».
 * Помним, на что подписался водитель; при каждом обновлении списка переставляем время
 * (событие перенесли) или сообщаем об отмене.
 */
object EventReminders {
    const val BEFORE_MIN = 15
    private const val PREFS = "event_reminders"

    data class Event(val id: Int, val title: String, val place: String, val lat: Double?, val lon: Double?,
                     val starts: Long, val ends: Long, val people: Int?, val note: String, val status: String)

    fun parse(o: JSONObject) = Event(o.getInt("id"), o.optString("title"), o.optString("place"),
        if (o.isNull("lat")) null else o.optDouble("lat"), if (o.isNull("lon")) null else o.optDouble("lon"),
        o.optLong("starts"), o.optLong("ends"), if (o.isNull("people")) null else o.optInt("people"), o.optString("note"), o.optString("status"))

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isOn(c: Context, id: Int) = p(c).contains("e$id")

    fun set(c: Context, e: Event, on: Boolean) {
        if (on) { p(c).edit().putLong("e${e.id}", e.ends).putString("t${e.id}", e.title).apply(); schedule(c, e) }
        else { p(c).edit().remove("e${e.id}").remove("t${e.id}").apply(); cancel(c, e.id) }
    }

    /** Свежий список с сервера: перенесённые — переставить, отменённые — снять и сообщить. */
    fun sync(c: Context, events: List<Event>) {
        for (e in events) {
            if (!isOn(c, e.id)) continue
            if (e.status != "ok") {
                set(c, e, false)
                notify(c, e.id, DriverUi.t(c, "Отменено: ${e.title}", "Anulat: ${e.title}"), e.place)
            } else if (p(c).getLong("e${e.id}", 0) != e.ends) {
                p(c).edit().putLong("e${e.id}", e.ends).apply()
                schedule(c, e)
            }
        }
    }

    /** Подписан ли кто-то на события — тогда главный экран раз в полчаса сверяет список. */
    fun any(c: Context) = p(c).all.keys.any { it.startsWith("e") }

    private fun pending(c: Context, id: Int, e: Event?): PendingIntent {
        val i = Intent(c, Receiver::class.java).putExtra("id", id)
        if (e != null) i.putExtra("title", e.title).putExtra("place", e.place)
        return PendingIntent.getBroadcast(c, 4000 + id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun schedule(c: Context, e: Event) {
        val at = e.ends - BEFORE_MIN * 60_000L
        if (at < System.currentTimeMillis()) return
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        // Неточный будильник: точные требуют особого разрешения; плюс-минус пара минут не страшно.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c, e.id, e))
    }

    private fun cancel(c: Context, id: Int) {
        c.getSystemService(AlarmManager::class.java)?.cancel(pending(c, id, null))
    }

    fun notify(c: Context, id: Int, title: String, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            nm.createNotificationChannel(NotificationChannel("events", DriverUi.t(c, "События", "Evenimente"), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, 5000 + id, Intent(c, EventsActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            nm.notify(5000 + id, NotificationCompat.Builder(c, "events").setSmallIcon(R.drawable.ic_my_location)
                .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).build())
        } catch (_: SecurityException) {}
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val id = i.getIntExtra("id", 0)
            if (!isOn(c, id)) return
            notify(c, id, DriverUi.t(c, "Через $BEFORE_MIN мин заканчивается: ${i.getStringExtra("title")}", "Peste $BEFORE_MIN min se termină: ${i.getStringExtra("title")}"),
                DriverUi.t(c, "${i.getStringExtra("place")} — скоро начнут выходить люди", "${i.getStringExtra("place")} — în curând ies oamenii"))
            p(c).edit().remove("e$id").remove("t$id").apply()
        }
    }
}

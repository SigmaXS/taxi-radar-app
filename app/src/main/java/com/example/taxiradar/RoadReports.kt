package com.example.taxiradar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import org.json.JSONObject

/** Метки водителей на карте (как в Waze): типы, загрузка с сервера, значки. */
object RoadReports {

    /** color — цвет кружка метки на карте. */
    data class Type(val key: String, val emoji: String, val label: Int, val road: Boolean, val color: Int)

    val TYPES = listOf(
        Type("police", "🚓", R.string.rep_police, true, 0xFF1565C0.toInt()),
        Type("radar", "📸", R.string.rep_radar, true, 0xFF6A1B9A.toInt()),
        Type("accident", "💥", R.string.rep_accident, true, 0xFFC62828.toInt()),
        Type("closure", "⛔", R.string.rep_closure, true, 0xFF8E0000.toInt()),
        Type("jam", "🚦", R.string.rep_jam, true, 0xFFEF6C00.toInt()),
        Type("pothole", "🕳", R.string.rep_pothole, true, 0xFF6D4C41.toInt()),
        Type("addr_noshow", "🙅", R.string.rep_addr_noshow, false, 0xFF546E7A.toInt()),
        Type("addr_hard", "🚧", R.string.rep_addr_hard, false, 0xFF546E7A.toInt()),
        Type("addr_cancel", "❌", R.string.rep_addr_cancel, false, 0xFF546E7A.toInt())
    )

    fun type(key: String) = TYPES.firstOrNull { it.key == key }

    /**
     * «Предупреждать в дороге»: только при включённом переключателе радар
     * следит за GPS (метки на дороге, очередь в аэропорту). По умолчанию выкл.
     */
    fun alertsEnabled(context: Context) =
        context.getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE).getBoolean("road_alerts", false)

    fun setAlertsEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE).edit().putBoolean("road_alerts", on).apply()
    }

    data class Report(
        val id: Long, val type: String, val lat: Double, val lon: Double,
        val createdMs: Long, val mine: Boolean, val voted: Boolean
    )

    suspend fun list(context: Context, lat: Double, lon: Double): List<Report>? {
        val json = CommunityApi.post(context, "/api/reports/list", JSONObject().put("lat", lat).put("lon", lon)) ?: return null
        if (!json.optBoolean("ok")) return null
        val arr = json.optJSONArray("reports") ?: return emptyList()
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Report(
                o.getLong("id"), o.getString("type"), o.getDouble("lat"), o.getDouble("lon"),
                try { iso.parse(o.optString("created").take(19))?.time ?: 0L } catch (e: Exception) { 0L },
                o.optBoolean("mine"), o.optBoolean("voted")
            )
        }
    }

    /** null — нет связи; иначе ok и текст ошибки, если не приняли. */
    suspend fun add(context: Context, type: String, lat: Double, lon: Double): Pair<Boolean, String>? {
        val json = CommunityApi.post(
            context, "/api/reports/add", JSONObject().put("type", type).put("lat", lat).put("lon", lon)
        ) ?: return null
        return json.optBoolean("ok") to json.optString("message")
    }

    suspend fun vote(context: Context, id: Long, still: Boolean): Boolean =
        CommunityApi.post(context, "/api/reports/vote", JSONObject().put("id", id).put("still", still))
            ?.optBoolean("ok") == true

    /** Проезжаем мимо метки — «Ещё здесь?» кнопками в уведомлении, как в Waze. */
    fun askStillHere(context: Context, r: Report) {
        val type = type(r.type) ?: return
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    "road", context.getString(R.string.road_channel), android.app.NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        fun action(still: Boolean): androidx.core.app.NotificationCompat.Action {
            val intent = android.content.Intent(context, ReportVoteReceiver::class.java)
                .putExtra("id", r.id).putExtra("still", still)
            val pi = android.app.PendingIntent.getBroadcast(
                context, (r.id * 2 + if (still) 1 else 0).toInt(), intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            return androidx.core.app.NotificationCompat.Action(
                0, context.getString(if (still) R.string.map_still_here else R.string.map_not_here), pi
            )
        }
        val n = androidx.core.app.NotificationCompat.Builder(context, "road")
            .setSmallIcon(R.drawable.ic_map)
            .setContentTitle("${type.emoji} ${context.getString(type.label)}")
            .setContentText(context.getString(R.string.road_still_question))
            .setTimeoutAfter(90_000)
            .setAutoCancel(true)
            .addAction(action(true))
            .addAction(action(false))
            .build()
        try {
            nm.notify(ROAD_NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
        }
    }

    const val ROAD_NOTIFICATION_ID = 303

    /** Кружок со значком для карты. */
    fun icon(context: Context, emoji: String, sizeDp: Int = 34): Drawable {
        val d = context.resources.displayMetrics.density
        val size = (sizeDp * d).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.tr_surface) }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.tr_accent)
            style = Paint.Style.STROKE
            strokeWidth = 2 * d
        }
        c.drawCircle(size / 2f, size / 2f, size / 2f - 2 * d, bg)
        c.drawCircle(size / 2f, size / 2f, size / 2f - 2 * d, ring)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size * 0.5f }
        val x = (size - text.measureText(emoji)) / 2f
        val y = size / 2f - (text.descent() + text.ascent()) / 2
        c.drawText(emoji, x, y, text)
        return BitmapDrawable(context.resources, bmp)
    }
}

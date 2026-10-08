package com.example.taxiradar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Пока радар работает, водитель может неделями не открывать само приложение и не увидеть
 * жёлтую плашку. Раз в 3 часа сверяемся с сервером: вышла новая версия — уведомление
 * в шторке (одно на версию); нажатие открывает Taxi Radar и сразу начинает обновление.
 */
object UpdateNotifier {
    const val EXTRA_START_UPDATE = "start_update"
    private const val CHECK_MS = 3 * 3600_000L
    private const val ID = 7001

    suspend fun check(context: Context, license: LicenseManager) {
        val prefs = context.getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("update_checked_at", 0) < CHECK_MS) return
        prefs.edit().putLong("update_checked_at", now).apply()
        license.fetchAppConfig()?.let { AppConfig.save(context, it) } ?: return
        val cfg = AppConfig.load(context)
        if (!AppUpdater.available(context, cfg)) return
        if (prefs.getInt("update_notified", 0) >= cfg.latestVersionCode) return
        prefs.edit().putInt("update_notified", cfg.latestVersionCode).apply()
        show(context, cfg)
    }

    private fun show(c: Context, cfg: AppConfig) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            nm.createNotificationChannel(NotificationChannel("updates", DriverUi.t(c, "Обновления", "Actualizări"), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(c, ID,
            Intent(c, MainActivity::class.java).putExtra(EXTRA_START_UPDATE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val first = cfg.updateNotes.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val text = DriverUi.t(c, "Нажмите — скачается и установится само.", "Apăsați — se descarcă și se instalează singur.") +
            (if (first.isNotEmpty()) "\n$first" else "")
        try {
            nm.notify(ID, NotificationCompat.Builder(c, "updates").setSmallIcon(R.drawable.ic_my_location)
                .setContentTitle(DriverUi.t(c, "Вышла Taxi Radar ${cfg.latestVersionName}", "A apărut Taxi Radar ${cfg.latestVersionName}"))
                .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).build())
        } catch (_: SecurityException) {}
    }
}

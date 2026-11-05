package com.example.taxiradar.throttle

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.taxiradar.MainActivity
import com.example.taxiradar.R

/** Уведомления «Отмены»: выключилась сама (система закрыла приложение / долго не было заказов). Тап — вкладка «Отмена». */
object CancelNotices {
    private const val CHANNEL = "throttle_license"
    private const val ID = 7102

    private fun show(c: Context, title: Int, text: Int, id: Int = ID) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, c.getString(R.string.throttle_license_channel), NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            c, 1,
            Intent(c, MainActivity::class.java).putExtra("open_tab", "throttle")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_nav_throttle)
            .setContentTitle(c.getString(title))
            .setContentText(c.getString(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(c.getString(text)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { nm.notify(id, n) }
    }

    /** Процесс убит (очистка памяти, перезагрузка): «Отмена» была включена и не работает. */
    fun stoppedBySystem(c: Context) = show(c, R.string.cancel_dead_title, R.string.cancel_dead_text)

    /** Давно не было заказов — выключили, чтобы не работала вхолостую. */
    fun autoOff(c: Context) = show(c, R.string.cancel_autooff_title, R.string.cancel_autooff_text)

    /** Результат живой проверки (кнопка «Проверить»). */
    fun test(c: Context, title: Int, text: Int) = show(c, title, text, 7103)

    fun clear(c: Context) {
        runCatching { c.getSystemService(NotificationManager::class.java)?.cancel(ID) }
    }
}

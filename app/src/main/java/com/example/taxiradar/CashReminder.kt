package com.example.taxiradar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

/**
 * «Включите оплату только наличными»: в зоне без спроса Яндекс даёт на час выбрать
 * «Наличными» (до 3 раз в сутки). Радар напоминает, пока надбавка 0, и следит по экранам
 * Яндекс Про, включил ли водитель: «Оплата · Наличными · Действует до 23:48» — молчим
 * до этого времени; «Вы меняли оплату 3/3» — до конца суток не напоминаем.
 */
object CashReminder {
    private const val REPEAT_MS = 15 * 60_000L
    private const val ID = 7101
    private val untilRegex = Regex("""(?i)(действует до|valabil până la)\s*(\d{1,2}):(\d{2})""")
    private val usedRegex = Regex("""^(\d)\s*/\s*3""")

    @Volatile private var cashUntil = 0L
    @Volatile private var usedToday = 0
    @Volatile private var usedDay = -1
    @Volatile private var lastRemind = 0L

    private fun today() = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

    /** Любой экран Яндекс Про: профиль («Наличными · Действует до …») и меню «Оплата» (N/3). */
    fun noteScreen(lines: List<String>) {
        val l = lines.map { it.trim() }
        val i = l.indexOfFirst { it.equals("Наличными", true) || it.equals("Numerar", true) }
        val until = l.firstNotNullOfOrNull { untilRegex.find(it) }
        if (i >= 0 && until != null) {
            val c = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, until.groupValues[2].toInt()); set(Calendar.MINUTE, until.groupValues[3].toInt()); set(Calendar.SECOND, 0)
            }
            // «до 00:30», а сейчас 23:40 — это уже завтра.
            if (c.timeInMillis < System.currentTimeMillis() - 3600_000L) c.add(Calendar.DAY_OF_YEAR, 1)
            cashUntil = c.timeInMillis
        } else if (l.any { it.equals("Наличными или картой", true) || it.equals("Numerar sau card", true) } && l.none { untilRegex.containsMatchIn(it) }) {
            // В профиле снова «Наличными или картой» — режим наличных кончился или выключен.
            if (l.any { it.equals("Оплата", true) || it.equals("Plata", true) }) cashUntil = 0L
        }
        // «Вы меняли оплату» и рядом «0/3» (иногда «раз» отдельной строкой).
        val k = l.indexOfFirst { it.contains("меняли оплату", true) || it.contains("ați schimbat plata", true) }
        if (k >= 0) l.drop(k).take(4).firstNotNullOfOrNull { usedRegex.find(it) }?.let {
            usedToday = it.groupValues[1].toInt(); usedDay = today()
        }
    }

    /** Из цикла надбавки: [surgeHere] — надбавка в точке водителя (null — неизвестна). */
    fun check(c: Context, surgeHere: Int?) {
        if (!DriverPreferences.flag(c, "cash_reminder", true) || LiteMode.cuts(c, LiteMode.Feature.ALERTS)) return
        if (surgeHere != 0) return
        val now = System.currentTimeMillis()
        if (now < cashUntil) return
        if (usedDay == today() && usedToday >= 3) return
        if (now - lastRemind < REPEAT_MS) return
        lastRemind = now
        notify(c)
    }

    private fun notify(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            nm.createNotificationChannel(NotificationChannel("cash_mode", DriverUi.t(c, "Оплата наличными", "Plată numerar"), NotificationManager.IMPORTANCE_DEFAULT))
        // Нажатие — сразу в Яндекс Про, там Профиль → Оплата.
        val yandex = listOf("ru.yandex.taximeter", "ru.yandex.taximeter.x").firstNotNullOfOrNull { c.packageManager.getLaunchIntentForPackage(it) }
        val tap = PendingIntent.getActivity(c, ID, yandex ?: Intent(c, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val left = if (usedDay == today()) 3 - usedToday else 3
        val text = DriverUi.t(c,
            "Рядом нет спроса — можно на час включить оплату только наличными: Яндекс Про → Профиль → Оплата → «Наличными». Осталось раз сегодня: $left.",
            "Nu e cerere în apropiere — puteți activa pe o oră doar numerar: Yandex Pro → Profil → Plata → «Numerar». Rămase azi: $left.")
        try {
            nm.notify(ID, NotificationCompat.Builder(c, "cash_mode").setSmallIcon(R.drawable.ic_payments)
                .setContentTitle(DriverUi.t(c, "Включите «Только наличными»", "Activați «Doar numerar»"))
                .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(false).build())
        } catch (_: SecurityException) {}
    }
}

package com.example.taxiradar.throttle

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.taxiradar.OrderAccessibilityService

/**
 * Ловит уведомления Яндекс Про о новом заказе. Работает, даже когда приложение
 * свёрнуто, — но требует один раз выдать «Доступ к уведомлениям».
 */
class ThrottleNotificationListener : NotificationListenerService() {

    companion object {
        private const val YANDEX_PRO = "ru.yandex.taximeter"
    }

    override fun onCreate() {
        super.onCreate()
        ThrottleAuto.ensureLoaded(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if (sbn.packageName != YANDEX_PRO) return
        ThrottleAuto.ensureLoaded(this)
        if (!ThrottleAuto.enabled) return
        // Пока работает чтение экрана, уведомления не нужны: карточка точнее.
        if (OrderAccessibilityService.instance != null) return

        val extras = notification.extras
        val text = buildString {
            append(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
            append(' ')
            append(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
            append(' ')
            append(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty())
            append(' ')
            append(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty())
        }.lowercase()

        if (isOrderNotification(text)) {
            android.util.Log.i("ThrottleNotif", "order notification: $text")
            ThrottleAuto.onOrderDetected(this, "notification")
        } else {
            android.util.Log.d("ThrottleNotif", "skip notification: $text")
        }
    }

    private fun isOrderNotification(text: String): Boolean {
        if (text.isEmpty()) return false
        // Отмены, завершения, чат и оценки — не новый заказ.
        if (text.contains("отмен")) return false
        if (text.contains("заверш")) return false
        if (text.contains("чат")) return false
        if (text.contains("сообщен")) return false
        if (text.contains("оценк")) return false
        if (text.contains("поддержк")) return false
        val mentionsOrder = text.contains("заказ") || text.contains("order") ||
            text.contains("принять") || text.contains("accept")
        return mentionsOrder
    }
}

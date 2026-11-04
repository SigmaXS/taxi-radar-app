package com.example.taxiradar.throttle

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.example.taxiradar.R

/** Домашний виджет «Отмена»: статус и кнопка СТАРТ/СТОП. */
class ThrottleWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.example.taxiradar.throttle.WIDGET_TOGGLE"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { ThrottleWidget.updateOne(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            ThrottleAuto.ensureLoaded(context)
            if (!ThrottleAuto.enabled && !com.example.taxiradar.OrderAccessibilityService.isConnected) {
                android.widget.Toast.makeText(context, R.string.cancel_need_accessibility_short, android.widget.Toast.LENGTH_LONG).show()
                return
            }
            if (!ThrottleAuto.enabled && !CancelLicense.allowed(context)) {
                android.widget.Toast.makeText(context, R.string.cancel_widget_need_subscription, android.widget.Toast.LENGTH_LONG).show()
            }
            ThrottleAuto.toggle(context)
            ThrottleWidget.update(context)
        }
        super.onReceive(context, intent)
    }
}

/** Обновление всех экземпляров виджета. */
object ThrottleWidget {

    fun update(context: Context) {
        ThrottleAuto.ensureLoaded(context)
        ThrottleState.ensureLoaded(context)
        val manager = AppWidgetManager.getInstance(context) ?: return
        val component = ComponentName(context, ThrottleWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        ids.forEach { updateOne(context, manager, it) }
    }

    fun updateOne(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_throttle)

        val now = SystemClock.elapsedRealtime()
        val endsAt = ThrottleState.endsAtElapsedRealtime.value
        val burst = ThrottleState.isRunning.value && endsAt > now

        val status = when {
            burst -> context.getString(R.string.throttle_widget_burst)
            ThrottleAuto.isPaused() -> context.getString(R.string.throttle_widget_paused)
            ThrottleAuto.enabled -> context.getString(R.string.throttle_widget_auto)
            else -> context.getString(R.string.throttle_widget_off)
        }
        views.setViewVisibility(R.id.widgetProgress, if (burst) android.view.View.VISIBLE else android.view.View.GONE)
        views.setTextViewText(R.id.widgetStatus, status)
        views.setTextViewText(
            R.id.widgetToggle,
            context.getString(if (ThrottleAuto.enabled) R.string.throttle_widget_stop else R.string.throttle_widget_start),
        )

        val toggle = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ThrottleWidgetProvider::class.java).setAction(ThrottleWidgetProvider.ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widgetToggle, toggle)
        views.setOnClickPendingIntent(R.id.widgetStatus, toggle)

        manager.updateAppWidget(widgetId, views)
    }
}

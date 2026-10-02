package com.example.taxiradar

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * Корневой элемент плавающего виджета. Долгое нажатие (без перетаскивания) открывает Taxi Radar.
 * Обычное нажатие и перетаскивание работают как раньше — события дальше идут в обработчик сервиса.
 */
class WidgetPillLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong() + 150
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var fired = false

    private val openApp = Runnable {
        fired = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val intent = Intent(context, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        runCatching { context.startActivity(intent) }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                fired = false
                postDelayed(openApp, longPressMs)
            }
            MotionEvent.ACTION_MOVE ->
                if (abs(event.rawX - downX) > slop || abs(event.rawY - downY) > slop) removeCallbacks(openApp)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(openApp)
                // После долгого нажатия не обновляем данные, как при обычном нажатии
                if (fired) {
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }
}

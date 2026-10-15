package com.example.taxiradar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Отдельная карточка анализа заказа Bolt поверх экрана. Живёт внутри FloatingWidgetService
 * (то же разрешение «поверх других окон»), кружок Яндекса не трогает.
 * Сверху экрана — чтобы не закрывать кнопку принятия заказа внизу. Нажатие — закрыть, перетаскивание — по вертикали.
 */
class BoltCardOverlay(private val context: Context, private val onClosedByUser: () -> Unit) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null

    val isShown get() = view?.isAttachedToWindow == true

    private fun dp(n: Float) = (n * context.resources.displayMetrics.density).toInt()

    private fun color(tone: BoltCardFormat.Tone) = when (tone) {
        // Палитра 1H Profit (p0.month): зелёный / жёлтый / красный; белый — обычный текст.
        BoltCardFormat.Tone.GOOD -> 0xFF8BC074.toInt()
        BoltCardFormat.Tone.OK -> 0xFFF6D685.toInt()
        BoltCardFormat.Tone.BAD -> 0xFFE8778A.toInt()
        BoltCardFormat.Tone.MUTED -> 0xFFB0B8BC.toInt()
        BoltCardFormat.Tone.NORMAL -> 0xFFFFFFFF.toInt()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureView(): LinearLayout {
        view?.let { return it }
        val v = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(10f), dp(16f), dp(12f))
            background = GradientDrawable().apply {
                cornerRadius = dp(18f).toFloat()
                setColor(0xF2101A1E.toInt())
                setStroke(dp(1f).coerceAtLeast(1), 0xFF34D186.toInt()) // зелёная рамка — фирменный цвет Bolt, чтобы не спутать с Яндексом
            }
            contentDescription = "Bolt"
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = DriverPreferences.number(context, "bolt_card_y", dp(40f).toDouble()).toInt()
            horizontalMargin = 0.03f
        }
        var startY = 0; var touchY = 0f; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { startY = p.y; touchY = e.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (e.rawY - touchY).toInt()
                    if (kotlin.math.abs(dy) > dp(8f)) moved = true
                    if (moved) { p.y = (startY + dy).coerceAtLeast(0); runCatching { wm.updateViewLayout(v, p) } }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) DriverPreferences.set(context, "bolt_card_y", p.y.toDouble())
                    else { hide(); onClosedByUser() }
                    true
                }
                else -> false
            }
        }
        view = v; params = p
        return v
    }

    fun show(rows: List<BoltCardFormat.Row>) {
        val v = ensureView()
        v.removeAllViews()
        rows.forEach { r ->
            v.addView(TextView(context).apply {
                text = r.text
                textSize = if (r.big) 22f else 15f
                if (r.big) setTypeface(null, Typeface.BOLD)
                setTextColor(color(r.tone))
                setPadding(0, dp(1f), 0, dp(1f))
            })
        }
        try {
            if (!v.isAttachedToWindow) wm.addView(v, params) else wm.updateViewLayout(v, params)
        } catch (e: Exception) {
            TripLog.d("Bolt: карточку не удалось показать: ${e.message}")
        }
    }

    fun hide() {
        val v = view ?: return
        try { if (v.isAttachedToWindow) wm.removeView(v) } catch (_: Exception) {}
    }
}

package com.example.taxiradar

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Плашка сверху экрана поверх любых приложений (как всплывающее уведомление).
 * Системные уведомления на Xiaomi по умолчанию не всплывают — водитель их не видит,
 * а эту видно всегда (разрешение «поверх других окон» у радара уже есть).
 * Одна за раз: новая заменяет старую. Нажатие — [onTap], крестик или время — закрыть.
 */
object OverlayPopup {
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var hide: Runnable? = null

    data class Action(val label: String, val run: () -> Unit)

    fun show(c: Context, title: String, text: String, colorRes: Int, showMs: Long = 12_000,
             actions: List<Action> = emptyList(), onTap: (() -> Unit)? = null) {
        val app = c.applicationContext
        if (!Settings.canDrawOverlays(app)) return
        main.post {
            dismiss()
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val dp = { v: Int -> DriverUi.dp(app, v) }
            val accent = app.getColor(colorRes)
            val card = LinearLayout(app).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(12), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat(); setColor(app.getColor(R.color.tr_surface)); setStroke(dp(2), accent)
                }
                elevation = dp(8).toFloat()
            }
            val head = LinearLayout(app).apply { gravity = Gravity.CENTER_VERTICAL }
            head.addView(TextView(app).apply {
                this.text = title; textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(accent)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            head.addView(TextView(app).apply {
                this.text = "✕"; textSize = 18f; setTextColor(app.getColor(R.color.tr_text_secondary))
                setPadding(dp(12), 0, dp(4), 0); setOnClickListener { dismiss() }
            })
            card.addView(head)
            if (text.isNotBlank()) card.addView(TextView(app).apply {
                this.text = text; textSize = 15f; setTextColor(app.getColor(R.color.tr_text)); setPadding(0, dp(4), 0, 0)
            })
            if (actions.isNotEmpty()) {
                val row = LinearLayout(app).apply { setPadding(0, dp(8), 0, 0) }
                actions.forEach { a ->
                    row.addView(TextView(app).apply {
                        this.text = a.label; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(accent)
                        setPadding(dp(10), dp(8), dp(10), dp(8))
                        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setStroke(dp(1), accent) }
                        setOnClickListener { dismiss(); a.run() }
                    }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
                }
                card.addView(row)
            }
            if (onTap != null) card.setOnClickListener { dismiss(); onTap() }
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val lp = WindowManager.LayoutParams(-1, -2, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP; y = dp(36)
                horizontalMargin = 0.03f
            }
            try { wm.addView(card, lp); view = card } catch (_: Exception) { return@post }
            hide = Runnable { dismiss() }.also { main.postDelayed(it, showMs) }
        }
    }

    fun dismiss() {
        hide?.let { main.removeCallbacks(it) }; hide = null
        val v = view ?: return
        view = null
        try { (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) {}
    }
}

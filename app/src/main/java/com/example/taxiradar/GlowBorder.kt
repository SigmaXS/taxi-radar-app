package com.example.taxiradar

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Бегущая градиентная подсветка по рамке («shine border»): светлая дуга обходит
 * карточку по кругу. Только Android 14+, не в лёгком режиме и не при выключенных
 * системных анимациях; ставится как foreground, поэтому нажатия не перехватывает.
 */
class GlowBorder(
    private val colors: IntArray,
    private val radius: Float,
    private val stroke: Float
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke }
    private val rect = RectF()
    private val rotation = Matrix()
    private var angle = 0f
    private var shader: SweepGradient? = null
    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 3600
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { angle = it.animatedValue as Float; invalidateSelf() }
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        rect.set(bounds); rect.inset(stroke / 2, stroke / 2)
        shader = SweepGradient(rect.centerX(), rect.centerY(), colors, null)
    }

    override fun draw(canvas: Canvas) {
        val s = shader ?: return
        rotation.setRotate(angle, rect.centerX(), rect.centerY())
        s.setLocalMatrix(rotation)
        paint.shader = s
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    fun start() { if (!animator.isStarted) animator.start() }
    fun stop() = animator.cancel()

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        fun allowed(v: View) = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ValueAnimator.areAnimatorsEnabled() && !LiteMode.cuts(v.context, LiteMode.Feature.EFFECTS)

        /** Включить/выключить подсветку на view; [accent] — основной цвет дуги. */
        fun apply(v: View, accent: Int, second: Int, radiusDp: Int) {
            (v.foreground as? GlowBorder)?.stop()
            if (!allowed(v)) { if (v.foreground is GlowBorder) v.foreground = null; return }
            val clear = accent and 0x00FFFFFF
            val d = v.resources.displayMetrics.density
            val glow = GlowBorder(intArrayOf(clear, accent, second, clear, clear), radiusDp * d, 2.5f * d)
            v.foreground = glow
            glow.start()
        }

        fun stop(v: View) { (v.foreground as? GlowBorder)?.stop() }
    }
}

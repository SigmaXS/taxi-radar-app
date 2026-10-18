package com.example.taxiradar

import android.content.Context
import android.graphics.*

/** Outline and coloured text gradient keep the map visible behind the figures. */
class MapMetricText(c: Context) : androidx.appcompat.widget.AppCompatTextView(c) {
    override fun onDraw(canvas: Canvas) {
        val base = currentTextColor
        val light = android.graphics.Color.rgb((Color.red(base) + 255) / 2, (Color.green(base) + 255) / 2, (Color.blue(base) + 255) / 2)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = resources.displayMetrics.density * 2.5f
        paint.colorFilter = PorterDuffColorFilter(0xFF102036.toInt(), PorterDuff.Mode.SRC_IN)
        super.onDraw(canvas)
        paint.colorFilter = null; paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat().coerceAtLeast(1f), intArrayOf(light, base), null, Shader.TileMode.CLAMP)
        paint.setShadowLayer(resources.displayMetrics.density * 2, 0f, resources.displayMetrics.density, 0xDD102036.toInt())
        super.onDraw(canvas)
        paint.shader = null; paint.clearShadowLayer()
    }
}

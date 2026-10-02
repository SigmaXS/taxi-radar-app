package com.example.taxiradar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

/** Значки карты в стиле навигатора: цветные кружки с белой обводкой и тенью. */
object MapIcons {

    private fun shadowPaint(d: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000
        maskFilter = BlurMaskFilter(3 * d, BlurMaskFilter.Blur.NORMAL)
    }

    private fun drawEmoji(c: Canvas, emoji: String, cx: Float, cy: Float, size: Float) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size }
        c.drawText(emoji, cx - text.measureText(emoji) / 2f, cy - (text.descent() + text.ascent()) / 2, text)
    }

    /** Метка на дороге: круг цвета типа, белая обводка, значок внутри. */
    fun circle(context: Context, emoji: String, color: Int, sizeDp: Int = 38): Drawable {
        val d = context.resources.displayMetrics.density
        val size = (sizeDp * d).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = size / 2f - 4 * d
        c.drawCircle(size / 2f, size / 2f + 1.5f * d, r, shadowPaint(d))
        c.drawCircle(size / 2f, size / 2f, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE })
        c.drawCircle(size / 2f, size / 2f, r - 2.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        drawEmoji(c, emoji, size / 2f, size / 2f, r * 1.05f)
        return BitmapDrawable(context.resources, bmp)
    }

    /** Место водителей: «булавка» — скруглённый квадрат с хвостиком вниз. */
    fun pin(context: Context, emoji: String, color: Int, sizeDp: Int = 40): Drawable {
        val d = context.resources.displayMetrics.density
        val w = (sizeDp * d).toInt()
        val tail = 8 * d
        val h = (w + tail).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val box = RectF(4 * d, 4 * d, w - 4 * d, w - 4 * d)
        val radius = 10 * d
        val shape = Path().apply {
            addRoundRect(box, radius, radius, Path.Direction.CW)
            moveTo(w / 2f - tail, box.bottom - 1)
            lineTo(w / 2f + tail, box.bottom - 1)
            lineTo(w / 2f, h.toFloat() - 1)
            close()
        }
        c.save(); c.translate(0f, 1.5f * d); c.drawPath(shape, shadowPaint(d)); c.restore()
        c.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE })
        val inner = RectF(box.left + 2.5f * d, box.top + 2.5f * d, box.right - 2.5f * d, box.bottom - 2.5f * d)
        c.drawRoundRect(inner, radius - 2 * d, radius - 2 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        drawEmoji(c, emoji, w / 2f, box.centerY(), inner.width() * 0.55f)
        return BitmapDrawable(context.resources, bmp)
    }

    /** «Я здесь»: стрелка навигатора в круге с ореолом; bearing — куда едем (null — без направления). */
    fun me(context: Context, bearing: Float?): Drawable {
        val d = context.resources.displayMetrics.density
        val size = (56 * d).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val accent = ContextCompat.getColor(context, R.color.tr_accent)
        c.drawCircle(cx, cx, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (accent and 0x00FFFFFF) or 0x33000000 })
        c.drawCircle(cx, cx + 1.5f * d, 15 * d, shadowPaint(d))
        c.drawCircle(cx, cx, 15 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        c.drawCircle(cx, cx, 12.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        c.save()
        c.rotate(bearing ?: 0f, cx, cx)
        val arrow = Path().apply {
            moveTo(cx, cx - 8 * d)
            lineTo(cx + 6 * d, cx + 6 * d)
            lineTo(cx, cx + 3 * d)
            lineTo(cx - 6 * d, cx + 6 * d)
            close()
        }
        c.drawPath(arrow, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A1A1A.toInt() })
        c.restore()
        return BitmapDrawable(context.resources, bmp)
    }
}

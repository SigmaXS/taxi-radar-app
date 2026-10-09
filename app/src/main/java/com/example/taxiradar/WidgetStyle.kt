package com.example.taxiradar

import android.content.Context
import android.graphics.Color

/**
 * Внешний вид плавающего кружка (экран «Вид виджета»): прозрачность, компоновка,
 * размеры букв тарифа / надбавки / цены заказа, тема, цвет надбавки, кнопка «+»,
 * приглушение без спроса. Размер кружка целиком — [WidgetSize].
 */
object WidgetStyle {
    private fun p(c: Context) = c.getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE)
    private fun int(c: Context, k: String, d: Int) = p(c).getInt("ws_$k", d)
    fun set(c: Context, k: String, v: Int) = p(c).edit().putInt("ws_$k", v).apply()
    fun set(c: Context, k: String, v: Boolean) = p(c).edit().putBoolean("ws_$k", v).apply()
    fun set(c: Context, k: String, v: String) = p(c).edit().putString("ws_$k", v).apply()

    /** Непрозрачность фона, %. */
    fun bgAlpha(c: Context) = int(c, "alpha", 92).coerceIn(30, 100)
    /** «column» — столбиком (Я / Б / Д), «row» — в одну строку. */
    fun row(c: Context) = p(c).getString("ws_layout", "column") == "row"
    fun separator(c: Context) = if (row(c)) "  " else "\n"
    /** Буквы тарифа (Э, К, К+, Д, Я, Б) относительно цифр. */
    fun labelScale(c: Context) = int(c, "label", 70).coerceIn(40, 100) / 100f
    /** Цифры надбавки и цена заказа, множители. */
    fun valueScale(c: Context) = int(c, "value", 100).coerceIn(70, 150) / 100f
    fun orderScale(c: Context) = int(c, "order", 100).coerceIn(70, 150) / 100f
    fun theme(c: Context) = p(c).getString("ws_theme", "dark") ?: "dark"
    fun surgeChoice(c: Context) = p(c).getString("ws_surge", "purple") ?: "purple"
    fun showPlus(c: Context) = p(c).getBoolean("ws_plus", true)
    /** Размер кнопки «+» (метки), %. */
    fun plusScale(c: Context) = int(c, "plus_size", 100).coerceIn(60, 160) / 100f
    /** Буквы тарифа (Э, К, Д, Я, Б) перед надбавкой. */
    fun showLabels(c: Context) = p(c).getBoolean("ws_labels", true)
    /** Знак «+» перед надбавкой («+15» или «15»). */
    fun showSign(c: Context) = p(c).getBoolean("ws_sign", true)
    fun value(c: Context, v: Int) = if (showSign(c)) "+$v" else "$v"
    fun label(c: Context, l: String) = if (showLabels(c)) "$l " else ""
    fun dim(c: Context) = p(c).getBoolean("ws_dim", false)
    /** Насколько виден кружок без спроса, %. */
    fun dimAlpha(c: Context) = int(c, "dim_alpha", 45).coerceIn(20, 90)

    /** Цвет фона с учётом темы и прозрачности. */
    fun bgColor(c: Context): Int {
        val base = when (theme(c)) { "light" -> 0xF5F5F7; "contrast" -> 0x000000; else -> 0x121216 }
        return (bgAlpha(c) * 255 / 100 shl 24) or base
    }
    /** Цвет спокойных цифр (нет спроса). */
    fun calmColor(c: Context) = when (theme(c)) { "light" -> 0xFF1A1A1A.toInt(); "contrast" -> 0xFFFFE000.toInt(); else -> c.getColor(R.color.tr_accent) }
    /** Цвет надбавки, когда есть спрос. */
    fun surgeColor(c: Context) = when (surgeChoice(c)) {
        "red" -> 0xFFFF4D4D.toInt(); "green" -> 0xFF22C55E.toInt()
        else -> if (theme(c) == "light") 0xFF8B3FD9.toInt() else c.getColor(R.color.tr_surge)
    }
    /** Обычный текст на кружке (строка «чистыми» и т.п.). */
    fun textColor(c: Context) = if (theme(c) == "light") 0xFF1A1A1A.toInt() else c.getColor(R.color.tr_text)
    fun strokeColor(c: Context) = if (theme(c) == "light") 0x33000000 else 0x33FFFFFF
    fun subColor(c: Context) = if (theme(c) == "light") Color.parseColor("#4A4A55") else c.getColor(R.color.tr_text_secondary)

    fun reset(c: Context) {
        val e = p(c).edit()
        p(c).all.keys.filter { it.startsWith("ws_") }.forEach { e.remove(it) }
        e.apply()
        WidgetSize.save(c, 100)
    }
}

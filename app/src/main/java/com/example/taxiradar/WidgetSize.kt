package com.example.taxiradar

import android.content.Context

/** Размер плавающего виджета (ползунок в «Профиле»): 70–160 %, по умолчанию 100 %. */
object WidgetSize {
    private const val PREFS = "taxi_radar_prefs"
    private const val KEY = "widget_scale_percent"
    const val MIN = 70
    const val MAX = 160

    fun percent(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 100).coerceIn(MIN, MAX)

    fun scale(context: Context): Float = percent(context) / 100f

    fun save(context: Context, percent: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY, percent.coerceIn(MIN, MAX)).apply()
    }
}

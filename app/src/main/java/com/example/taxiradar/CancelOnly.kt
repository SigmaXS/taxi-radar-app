package com.example.taxiradar

import android.content.Context
import android.content.Intent

/**
 * Режим «Только Отмена»: радар (цена, надбавка, карта, Bolt, журнал поездок) полностью выключен,
 * работает одно — «Отмена» по карточке заказа. Телефон почти не нагружается.
 * Специальные возможности остаются включёнными: без них «Отмена» не видит заказ.
 */
object CancelOnly {
    private const val KEY = "cancel_only"

    fun on(c: Context) = c.getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(c: Context, value: Boolean) {
        c.getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
        if (value) {
            // Радар сразу гасим: плавающее окно, обновление надбавки, чтение Bolt.
            c.stopService(Intent(c, FloatingWidgetService::class.java))
            c.stopService(Intent(c, ScreenReaderService::class.java))
        }
    }
}

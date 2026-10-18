package com.example.taxiradar

import android.content.Context
import android.content.Intent

/** Service selection does not disable Android Accessibility, which is shared by both readers. */
object RadarServices {
    fun selection(c: Context) = DriverPreferences.text(c, "radar_services", "both")
    fun yandex(c: Context) = selection(c) != "bolt"
    fun bolt(c: Context) = selection(c) != "yandex"
    fun method(c: Context) = DriverPreferences.text(c, "bolt_read_method", "accessibility")
    fun accessibility(c: Context) = bolt(c) && method(c) == "accessibility"
    fun ocr(c: Context) = bolt(c) && method(c) == "ocr"
    fun hideMs(c: Context) = DriverPreferences.number(c, "bolt_hide_seconds", 8.0).toLong().coerceIn(6, 10) * 1000
    fun select(c: Context, value: String) {
        require(value in listOf("yandex", "bolt", "both"))
        DriverPreferences.set(c, "radar_services", value)
        if (!bolt(c)) c.stopService(Intent(c, ScreenReaderService::class.java))
        FloatingWidgetService.clearOrder()
        FloatingWidgetService.refreshSurge()
    }
    fun method(c: Context, value: String) {
        require(value in listOf("accessibility", "ocr"))
        DriverPreferences.set(c, "bolt_read_method", value)
        if (value != "ocr") c.stopService(Intent(c, ScreenReaderService::class.java))
    }
    fun corrections(c: Context) = AppConfig.load(c).boltOcrCorrections.ifBlank { DriverPreferences.text(c, "ocr_words") }
}

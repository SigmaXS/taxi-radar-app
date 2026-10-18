package com.example.taxiradar

import android.content.Context

/** Bolt appearance is independent of Yandex widget preferences. */
object BoltWidgetStyle {
    fun scale(c: Context) = (DriverPreferences.number(c, "bolt_widget_size", 100.0) / 100).toFloat().coerceIn(.75f, 1.3f)
    fun priceScale(c: Context) = (DriverPreferences.number(c, "bolt_widget_price", 100.0) / 100).toFloat().coerceIn(.75f, 1.4f)
    fun background(c: Context): Int = ((DriverPreferences.number(c, "bolt_widget_alpha", 92.0).toInt().coerceIn(30, 100) * 255 / 100) shl 24) or 0x121C2A
    fun plusOnly(c: Context) = !RadarServices.yandex(c) && DriverPreferences.flag(c, "bolt_plus_only", true)
    fun alreadyNet(c: Context, offer: ScreenOfferParser.Offer) = offer.fare != null && (offer.afterCommission || DriverPreferences.flag(c, "ocr_net_fare"))
    fun netValue(c: Context, fare: Double, alreadyNet: Boolean, km: Double): Double {
        val commission = DriverPreferences.number(c, "ocr_commission", DriverPreferences.costs(c).commission).coerceIn(0.0, 100.0)
        val costs = DriverPreferences.flag(c, "ocr_with_costs") && DriverPreferences.costsReady(c)
        return fare * (1 - (if (alreadyNet) 0.0 else commission) / 100) - if (costs) km * DriverPreferences.costs(c).perKm else 0.0
    }
    fun netLine(c: Context, fare: Double?, alreadyNet: Boolean, km: Double): String {
        if (fare == null || !DriverPreferences.flag(c, "bolt_show_net", true)) return ""
        val commission = DriverPreferences.number(c, "ocr_commission", DriverPreferences.costs(c).commission).coerceIn(0.0, 100.0)
        val costs = DriverPreferences.flag(c, "ocr_with_costs") && DriverPreferences.costsReady(c)
        val left = netValue(c, fare, alreadyNet, km)
        val caption = if (alreadyNet) DriverUi.t(c, if (costs) "После расходов ≈ " else "Чистыми ≈ ", if (costs) "După costuri ≈ " else "Net ≈ ")
            else DriverUi.t(c, if (costs) "После комиссии и расходов ≈ " else "После комиссии ≈ ", if (costs) "După comision și costuri ≈ " else "După comision ≈ ")
        return caption + String.format(java.util.Locale.getDefault(), "%.0f L", left)
    }
}

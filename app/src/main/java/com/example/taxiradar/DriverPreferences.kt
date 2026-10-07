package com.example.taxiradar

import android.content.Context

object DriverPreferences {
    fun prefs(c: Context) = c.getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE)
    fun flag(c: Context, key: String, default: Boolean = false) = prefs(c).getBoolean("driver_$key", default)
    fun number(c: Context, key: String, default: Double = 0.0) = prefs(c).getFloat("driver_$key", default.toFloat()).toDouble()
    fun text(c: Context, key: String, default: String = "") = prefs(c).getString("driver_$key", default).orEmpty()
    fun set(c: Context, key: String, value: Boolean) { prefs(c).edit().putBoolean("driver_$key", value).apply() }
    fun set(c: Context, key: String, value: Double) { prefs(c).edit().putFloat("driver_$key", value.toFloat()).apply() }
    fun set(c: Context, key: String, value: String) { prefs(c).edit().putString("driver_$key", value).apply() }
    fun costs(c: Context): OrderEconomics.Costs {
        val s = NetEarnings.load(c)
        return OrderEconomics.Costs(s.commissionPercent, s.consumptionPer100Km, s.fuelPrice, number(c, "maintenance"))
    }
    fun costsReady(c: Context) = NetEarnings.load(c).let { it.fuelPrice > 0 && it.consumptionPer100Km > 0 }
    fun selectedTariff(c: Context): String {
        val p = prefs(c)
        return when { p.getBoolean("show_econom", true) -> "econom"; p.getBoolean("show_comfort", false) -> "comfort"; else -> "comfortplus" }
    }
}

package com.example.taxiradar

import android.content.Context
import kotlin.math.roundToInt

/**
 * «Чистыми»: цена заказа минус комиссия парка и Яндекса и минус топливо
 * на всю дорогу — подача к клиенту плюс сама поездка.
 */
object NetEarnings {
    private const val PREFS = "taxi_radar_prefs"

    data class Settings(
        val enabled: Boolean,
        val consumptionPer100Km: Double,
        val fuelPrice: Double,
        val commissionPercent: Double
    )

    fun load(context: Context): Settings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(
            enabled = p.getBoolean("net_enabled", false),
            consumptionPer100Km = p.getFloat("net_consumption", 7f).toDouble(),
            fuelPrice = p.getFloat("net_fuel_price", 0f).toDouble(),
            commissionPercent = p.getFloat("net_commission", 20f).toDouble()
        )
    }

    fun save(context: Context, s: Settings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("net_enabled", s.enabled)
            .putFloat("net_consumption", s.consumptionPer100Km.toFloat())
            .putFloat("net_fuel_price", s.fuelPrice.toFloat())
            .putFloat("net_commission", s.commissionPercent.toFloat())
            .apply()
    }

    /** null — если «чистыми» выключено или не заполнены данные. */
    fun compute(context: Context, price: Int, tripKm: Double, pickupKm: Double): Int? {
        val s = load(context)
        if (!s.enabled || s.fuelPrice <= 0 || s.consumptionPer100Km <= 0) return null
        val fuelCost = (tripKm + pickupKm) * s.consumptionPer100Km / 100.0 * s.fuelPrice
        return (price * (1 - s.commissionPercent / 100.0) - fuelCost).roundToInt()
    }
}

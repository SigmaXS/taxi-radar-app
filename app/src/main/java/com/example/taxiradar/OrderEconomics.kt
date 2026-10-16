package com.example.taxiradar

import kotlin.math.roundToInt

/** Arithmetic only; UI always calls these figures a forecast, not actual earnings. */
object OrderEconomics {
    data class Costs(val commission: Double, val consumption: Double, val energyPrice: Double, val maintenanceKm: Double = 0.0) {
        val perKm get() = consumption.coerceAtLeast(0.0) / 100 * energyPrice.coerceAtLeast(0.0) + maintenanceKm.coerceAtLeast(0.0)
    }
    data class Result(val net: Int, val perKm: Double?, val perHour: Int?, val perMinute: Double? = null)

    fun calculate(price: Int, tripKm: Double, pickupKm: Double, minutes: Int, pickupMinutes: Double, costs: Costs, returnKm: Double = 0.0): Result {
        val distance = tripKm.coerceAtLeast(0.0) + pickupKm.coerceAtLeast(0.0) + returnKm.coerceAtLeast(0.0)
        val net = price * (1 - costs.commission.coerceIn(0.0, 100.0) / 100) - distance * costs.perKm
        // Минуты поездки не прочитались (0) — оцениваем по км (~25 км/ч по городу); без км — «в час» не считаем:
        // иначе в час делилась бы одна подача и выходило «1019 L/час».
        val tripMin = if (minutes > 0) minutes.toDouble() else if (tripKm > 0) tripKm / CITY_KMH * 60 else return Result(net.roundToInt(), if (distance > 0) net / distance else null, null)
        val time = tripMin + pickupMinutes.coerceAtLeast(0.0) + returnKm.coerceAtLeast(0.0) * 2
        val perHour = if (time > 0) (net * 60 / time).roundToInt() else null
        // Больше MAX_PER_HOUR — это ошибка данных, а не заказ: не показываем.
        val validPerHour = perHour?.takeIf { it <= MAX_PER_HOUR }
        return Result(net.roundToInt(), if (distance > 0) net / distance else null, validPerHour,
            if (validPerHour != null) net / time else null)
    }

    private const val CITY_KMH = 25.0
    private const val MAX_PER_HOUR = 400

    /** User-selected tolerance, not a statistically validated confidence interval. */
    fun range(price: Int, percent: Int): IntRange {
        val delta = (price * percent.coerceIn(1, 30) / 100.0).roundToInt().coerceAtLeast(1)
        return (price - delta).coerceAtLeast(0)..(price + delta)
    }
}

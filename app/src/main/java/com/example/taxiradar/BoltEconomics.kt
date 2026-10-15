package com.example.taxiradar

/**
 * Расчёт карточки Bolt. Формулы — как в 1H Profit (p0.k.beta и p0.u.scanner), но без выдуманных значений:
 *
 *  выручка        = цена × (1 − комиссия Bolt % / 100)      — комиссия вычитается ОДИН раз и только если
 *                                                              водитель указал, что цена на карточке — до комиссии;
 *  после расходов = выручка − стоимость 1 км × (подача км + поездка км)   — только если расходы машины заполнены;
 *  за км          = база / (подача км + поездка км);
 *  за час         = база / ((подача мин + поездка мин + обслуживание мин) / 60) — только если известно время поездки;
 *  за минуту      = база / (подача мин + поездка мин + обслуживание мин);
 *  база           = «после расходов», если расходы заполнены, иначе «выручка» (тогда это НЕ прибыль, так и подписываем).
 *
 * Подача входит и в километры, и во время (как в 1H Profit: totalKm/totalMin — сумма всех отрезков).
 */
object BoltEconomics {

    data class Settings(
        /** % комиссии, если цена на карточке — до комиссии. 0 — цена уже «на руки» (так считает 1H Profit по умолчанию). */
        val commissionPercent: Double = 0.0,
        /** Стоимость 1 км (топливо + обслуживание); null — расходы машины не заполнены. */
        val costPerKm: Double? = null,
        /** Ожидание, посадка, высадка — минут на заказ (1H Profit: service_time = 2). */
        val serviceMin: Int = 2,
        /** Пороги; 0 — не проверять. */
        val minPerKm: Double = 0.0,
        val minPerHour: Double = 0.0,
        val minPerMin: Double = 0.0,
        val minRating: Double = 0.0,
        val shortPickupKm: Double = 2.0
    )

    enum class Grade { GOOD, OK, BAD }

    data class Metrics(
        val revenue: Double,
        val afterCosts: Double?,
        /** true — база для «за км/час» уже за вычетом расходов машины. */
        val baseIsAfterCosts: Boolean,
        val totalKm: Double?,
        val totalMin: Int?,
        val perKm: Double?,
        val perHour: Double?,
        val perMin: Double?,
        val perKmGrade: Grade?,
        val perHourGrade: Grade?,
        val perMinGrade: Grade?,
        val shortPickup: Boolean,
        val lowRating: Boolean,
        /** Чего не хватило для расчёта — показываем «нет данных», а не ноль. */
        val missing: List<String>
    ) {
        val overall: Grade?
            get() {
                val grades = listOfNotNull(perKmGrade, perHourGrade, perMinGrade)
                if (grades.isEmpty()) return null
                return when {
                    grades.any { it == Grade.BAD } -> Grade.BAD
                    grades.all { it == Grade.GOOD } -> Grade.GOOD
                    else -> Grade.OK
                }
            }
    }

    /** Цвет как в 1H Profit (p0.month.scanner): ≥115% порога — хорошо, ≥100% — норма, ниже — плохо. */
    fun grade(value: Double?, threshold: Double): Grade? {
        if (value == null || threshold <= 0) return null
        return when {
            value >= threshold * 1.15 -> Grade.GOOD
            value >= threshold -> Grade.OK
            else -> Grade.BAD
        }
    }

    fun calculate(o: BoltOfferParser.Offer, s: Settings): Metrics {
        val missing = mutableListOf<String>()
        val revenue = o.price * (1 - s.commissionPercent.coerceIn(0.0, 100.0) / 100)

        val totalKm: Double? = if (o.pickup == null && o.trip == null) null
            else (o.pickup?.km ?: 0.0) + (o.trip?.km ?: 0.0)
        if (o.trip == null) missing += "trip"
        if (o.pickup == null) missing += "pickup"

        val tripMinKnown = o.trip?.min
        val totalMin: Int? = if (o.trip != null && tripMinKnown == null) null
            else if (o.pickup == null && o.trip == null) null
            else (o.pickup?.min ?: 0) + (tripMinKnown ?: 0)
        if (o.trip != null && tripMinKnown == null) missing += "trip_time"

        val costPerKm = s.costPerKm?.takeIf { it >= 0 }
        val afterCosts = if (costPerKm != null && totalKm != null) revenue - costPerKm * totalKm else null
        if (costPerKm == null) missing += "costs"
        val base = afterCosts ?: revenue

        // Без поездки «за км/час» по одной подаче — это не показатель заказа (1H Profit для такого случая
        // достраивал маршрут через TomTom; у нас этого нет — честно пишем «нет данных»).
        val tripKnown = o.trip != null
        val perKm = if (tripKnown && totalKm != null && totalKm > 0.1) base / totalKm else null
        val minutes = totalMin?.let { it + s.serviceMin.coerceAtLeast(0) }
        val perMin = if (tripKnown && minutes != null && minutes > 0) base / minutes else null
        val perHour = perMin?.let { it * 60 }

        return Metrics(
            revenue = revenue,
            afterCosts = afterCosts,
            baseIsAfterCosts = afterCosts != null,
            totalKm = totalKm,
            totalMin = totalMin,
            perKm = perKm,
            perHour = perHour,
            perMin = perMin,
            perKmGrade = grade(perKm, s.minPerKm),
            perHourGrade = grade(perHour, s.minPerHour),
            perMinGrade = grade(perMin, s.minPerMin),
            shortPickup = o.pickup != null && s.shortPickupKm > 0 && o.pickup.km <= s.shortPickupKm,
            lowRating = o.rating != null && s.minRating > 0 && o.rating < s.minRating,
            missing = missing
        )
    }
}

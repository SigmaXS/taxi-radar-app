package com.example.taxiradar

/** Driver-supplied Moldova table. An estimate, never a binding quote from Bolt. */
object BoltFareEstimator {
    data class Tariff(val start: Double, val minimum: Double, val perKm: Double, val perMinute: Double)
    val tariffs = mapOf(
        "econom_cash" to Tariff(35.0, 35.0, 4.04, 1.30),
        "bolt" to Tariff(35.0, 35.0, 4.30, 1.38),
        "pet" to Tariff(40.25, 40.0, 4.95, 1.59),
        "comfort" to Tariff(43.0, 43.0, 5.60, 1.83),
        "child" to Tariff(43.0, 43.0, 5.60, 1.83),
        "premium" to Tariff(60.0, 60.0, 5.59, 1.80),
        "xl" to Tariff(49.0, 49.0, 6.02, 1.94)
    )
    fun category(text: String): String? = when {
        Regex("(?iu)(детск|кресл|scaun|child)").containsMatchIn(text) -> "child"
        Regex("(?iu)(питом|pet\\b|animale)").containsMatchIn(text) -> "pet"
        Regex("(?iu)(premium|премиум)").containsMatchIn(text) -> "premium"
        Regex("(?iu)\\bXL\\b").containsMatchIn(text) -> "xl"
        Regex("(?iu)(комфорт|comfort)\\s*\\+").containsMatchIn(text) -> null
        Regex("(?iu)(комфорт|comfort)").containsMatchIn(text) -> "comfort"
        Regex("(?iu)(эконом|econom)").containsMatchIn(text) -> "econom_cash"
        Regex("(?iu)\\bBolt\\b").containsMatchIn(text) -> "bolt"
        else -> null
    }
    fun estimate(category: String?, km: Double, minutes: Double, multiplier: Double): Double? {
        val t = tariffs[category] ?: return null
        if (!km.isFinite() || !minutes.isFinite() || !multiplier.isFinite() || km < 0 || minutes < 0 || multiplier < 1) return null
        return maxOf(t.minimum, t.start + km * t.perKm + minutes * t.perMinute) * multiplier
    }
}

package com.example.taxiradar

/** Keeps readings of one proposal separate from subsequent offers and moving GPS. */
class BoltOfferSnapshot {
    var current: ScreenOfferParser.Offer? = null; private set
    var generation = 0L; private set
    fun accept(next: ScreenOfferParser.Offer): Boolean {
        val previous = current
        if (previous == null || !same(previous, next)) {
            current = next; generation++; return true
        }
        current = previous.copy(
            fare = previous.fare ?: next.fare,
            addresses = (0..1).map { previous.addresses.getOrElse(it) { "" }.ifBlank { next.addresses.getOrElse(it) { "" } } },
            tariff = previous.tariff ?: next.tariff, boltCategory = previous.boltCategory ?: next.boltCategory,
            multiplier = if (previous.multiplierKnown) previous.multiplier else next.multiplier,
            multiplierKnown = previous.multiplierKnown || next.multiplierKnown,
            afterCommission = previous.afterCommission || next.afterCommission)
        return false
    }
    fun clear() { current = null; generation++ }
    private fun near(a: Double, b: Double, tolerance: Double) = kotlin.math.abs(a - b) <= tolerance
    private fun same(a: ScreenOfferParser.Offer, b: ScreenOfferParser.Offer): Boolean {
        if (!near(a.pickupKm, b.pickupKm, .5) || !near(a.tripKm, b.tripKm, 1.0) ||
            !near(a.pickupMinutes, b.pickupMinutes, 2.0) || !near(a.tripMinutes, b.tripMinutes, 3.0)) return false
        if (a.fare != null && b.fare != null && !near(a.fare, b.fare, 2.0)) return false
        if (a.boltCategory != null && b.boltCategory != null && a.boltCategory != b.boltCategory) return false
        // House numbers are a stronger identity signal than small OCR spelling changes.
        for (i in 0..1) {
            val x = a.addresses.getOrElse(i) { "" }; val y = b.addresses.getOrElse(i) { "" }
            val nx = Regex("\\d+").findAll(x).map { it.value }.toList()
            val ny = Regex("\\d+").findAll(y).map { it.value }.toList()
            if (nx.isNotEmpty() && ny.isNotEmpty() && nx != ny) return false
        }
        return true
    }
}

package com.example.taxiradar

/** Most recent offer; memory only, never interpreted as a completed ride. */
object OrderPreview {
    data class Offer(val price: Int, val km: Double, val minutes: Int, val pickup: Double, val bonus: Int, val route: List<String>, val yandex: Boolean, val at: Long = System.currentTimeMillis())
    @Volatile var latest: Offer? = null
    @Volatile var destination: Pair<Double, Double>? = null
    fun current() = latest?.takeIf { System.currentTimeMillis() - it.at < 30 * 60000 }
}

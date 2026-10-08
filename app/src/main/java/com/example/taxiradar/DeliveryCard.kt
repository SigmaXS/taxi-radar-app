package com.example.taxiradar

/**
 * Карточка «Доставка» в Яндекс Про: цена фиксированная, считать по км не нужно.
 * Цена = старт доставки + все строки «+N L» (высокий спрос, платная подача,
 * «от двери до двери» и любые другие услуги). При наличной оплате Яндекс сам пишет
 * полную цену («168 L» без плюса) — тогда берём её.
 */
object DeliveryCard {
    data class Item(val label: String, val value: Double)
    data class Result(val price: Int, val items: List<Item>, val base: Int, val fromCard: Boolean, val km: Double?, val minutes: Int?)

    private val titles = setOf("доставка", "livrare", "delivery")
    private val plusRegex = Regex("""^\+\s*(\d{1,4}(?:[.,]\d{1,2})?)\s*(?:L|MDL|lei|лей)\.?$""", RegexOption.IGNORE_CASE)
    private val totalRegex = Regex("""^(\d{2,4}(?:[.,]\d{1,2})?)\s*(?:L|MDL|lei|лей)\.?$""", RegexOption.IGNORE_CASE)
    private val routeRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(км|km|м|m)\s*[•·]\s*(\d+)\s*(мин|min)""", RegexOption.IGNORE_CASE)

    fun isDelivery(lines: List<String>): Boolean =
        lines.any { it.trim().lowercase() in titles } &&
            lines.any { it.contains("получени", true) || it.contains("вручени", true) || it.equals("Откуда", true) || it.equals("De unde", true) || it.contains("ridicare", true) || it.contains("predare", true) }

    /** null — на карточке не нашли ни одной суммы. */
    fun parse(lines: List<String>, base: Int): Result? {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val items = mutableListOf<Item>()
        var total: Double? = null
        for (i in clean.indices) {
            val l = clean[i]
            plusRegex.matchEntire(l)?.let { m ->
                val v = m.groupValues[1].replace(',', '.').toDouble()
                // Подпись — ближайшая строка выше, которая не сумма.
                val label = (i - 1 downTo maxOf(0, i - 2)).map { clean[it] }.firstOrNull { plusRegex.matchEntire(it) == null && it.any(Char::isLetter) } ?: ""
                items += Item(label, v)
                return@let
            }
            if (total == null) totalRegex.matchEntire(l)?.let { total = it.groupValues[1].replace(',', '.').toDouble() }
        }
        // Если оба числа — одна и та же строка «+N», сумма без плюса не считается полной ценой.
        if (items.isEmpty() && total == null) return null
        val route = clean.firstNotNullOfOrNull { routeRegex.find(it) }
        val km = route?.let { m -> m.groupValues[1].replace(',', '.').toDouble().let { if (m.groupValues[2].lowercase() in setOf("км", "km")) it else it / 1000 } }
        val min = route?.groupValues?.get(3)?.toIntOrNull()
        val sum = base + items.sumOf { it.value }
        val price = total?.takeIf { it > sum * 0.5 } ?: sum
        return Result(Math.round(price).toInt(), items, base, total != null && price == total, km, min)
    }

    /** Коротко, из чего цена: «старт 25 + спрос 95 + подача 17.8 + дверь 30». */
    fun explain(r: Result, ru: Boolean): String {
        if (r.fromCard) return if (ru) "Доставка · цена с карточки" else "Livrare · prețul de pe ofertă"
        fun short(l: String) = when {
            l.contains("спрос", true) || l.contains("cerer", true) -> if (ru) "спрос" else "cerere"
            l.contains("подач", true) || l.contains("prelu", true) -> if (ru) "подача" else "preluare"
            l.contains("двер", true) || l.contains("ușă", true) || l.contains("usa", true) -> if (ru) "до двери" else "la ușă"
            else -> l.split(' ').firstOrNull()?.lowercase()?.take(10) ?: ""
        }
        fun n(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
        return (if (ru) "Доставка: старт ${r.base}" else "Livrare: start ${r.base}") + r.items.joinToString("") { " + ${short(it.label)} ${n(it.value)}" }
    }
}

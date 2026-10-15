package com.example.taxiradar

import kotlin.math.abs

/**
 * Разбор текста карточки заказа Bolt Driver (строки из Accessibility).
 *
 * Правила взяты из разбора 1H Profit (p0.k.beta / p0.calc):
 *  - цена — число с валютой; если на карточке несколько РАЗНЫХ цен — это не одна карточка, пропускаем;
 *  - отрезки «N мин · X км» (или «X км · N мин»): первый — подача, следующие — поездка;
 *    скорость отрезка 3…135 км/ч, иначе это не отрезок (ошибка чтения);
 *  - отдельная строка «X км» без минут — длина поездки без времени (время не выдумываем);
 *  - рейтинг пассажира — «4.9★» / «★ 4,93».
 * Чего нет на карточке — остаётся null, ничего не дорисовываем.
 */
object BoltOfferParser {

    data class Segment(val km: Double, val min: Int?)

    data class Offer(
        /** Сумма с карточки (как есть, без вычетов). Для вилки «120–140» — нижняя граница. */
        val price: Double,
        val priceIsRange: Boolean,
        /** Валюта с карточки в нормальном виде: «L» для MDL/lei/лей, иначе как написано. */
        val currency: String,
        val pickup: Segment?,
        val trip: Segment?,
        /** Сколько отрезков поездки сложено в trip (заезды). */
        val tripSegments: Int,
        val rating: Double?,
        /** «1.5x Высокий спрос» / «Cerere mare 1.2x» — множитель спроса с карточки (уже в цене). */
        val surge: Double? = null,
        /** CARD / CASH, если на карточке есть «По карте» / «Наличные» / «Numerar» / «Card». */
        val payment: String? = null,
        /** «Bolt», «Комфорт», «Comfort»… — как на карточке. */
        val tariff: String? = null
    ) {
        /** Ключ для отсева повторных событий одной и той же карточки. */
        val key: String
            get() = listOf(price, priceIsRange, currency, pickup?.km, pickup?.min, trip?.km, trip?.min, rating).joinToString("|")
    }

    sealed class Result {
        data class Ok(val offer: Offer) : Result()
        data class NotOffer(val reason: String) : Result()
    }

    // Валюта до или после числа. «L» и «Л» — только отдельной буквой (не «Lux», не «Ленина»).
    private const val CUR = """(MDL|mdl|lei|LEI|Lei|лей|леев|L|Л|RON|ron|EUR|eur|€|UAH|грн|₴|USD|\$|PLN|zł|zl)"""
    // «206,00», «1 733,00» (тысячи через пробел / неразрывный пробел), «1733.5».
    private const val NUM = """(\d{1,3}(?:[ \u00A0\u202F\u2009]\d{3})+(?:[.,]\d{1,2})?|\d{1,6}(?:[.,]\d{1,2})?)"""
    private val priceAfter = Regex("""(?<![\d+\-])$NUM\s*$CUR(?![\p{L}\d])""")
    private val priceBefore = Regex("""(?<![\p{L}])$CUR\s*$NUM(?![\d])""")
    private val priceRange = Regex("""(?<![\d+])$NUM\s*[-–—]\s*$NUM\s*$CUR(?![\p{L}\d])|(?<![\p{L}])$CUR\s*$NUM\s*[-–—]\s*$NUM(?![\d])""")

    private const val DIST = """(\d{1,4}(?:[.,]\d{1,2})?)\s*(km|км|m|м)\.?"""
    private const val TIME = """(?:(\d{1,2})\s*(?:h|ч|ч\.)\s*)?(\d{1,3})\s*(?:min|мин|мин\.|min\.)"""
    private const val SEP = """[^\n\d]{0,6}?"""
    private val distThenTime = Regex("""(?<![\d.,])$DIST(?![\p{L}])$SEP$TIME(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val timeThenDist = Regex("""(?<![\d.,])$TIME(?![\p{L}])$SEP$DIST(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val soloKm = Regex("""^\s*(\d{1,4}(?:[.,]\d{1,2})?)\s*(km|км)\.?\s*$""", RegexOption.IGNORE_CASE)
    // Распознанный со скриншота текст (OCR без кириллицы): «7 MNH • 4.1 km» — единица минут нечитаема,
    // но за ней разделитель и километры. Используется, только если строгие шаблоны в строке ничего не нашли.
    private val ocrTimeThenKm = Regex("""(?<![\d.,])(\d{1,3})\s*\p{L}{1,4}\.?\s*[•·∙]?\s*(\d{1,4}(?:[.,]\d{1,2})?)\s*(km|км)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    // Цена со скриншота, когда «MDL» распознан криво («MOL», «MDI»): отдельная строка «206,00» + до 4 знаков мусора.
    // Bolt всегда пишет копейки «,00», поэтому без двух знаков после запятой не берём.
    private val ocrPrice = Regex("""^\s*(\d{1,3}(?:[ \u00A0\u202F\u2009]\d{3})*[.,]\d{2})\s*[^\s\d]{0,4}\s*$""")
    private val surgeRegex = Regex("""(?<![\d.,])([1-9](?:[.,]\d{1,2})?)\s*[xх×](?![\p{L}\d])""", RegexOption.IGNORE_CASE)
    private val cashRegex = Regex("""(?iu)\b(наличн\S*|numerar|cash)\b""")
    private val cardRegex = Regex("""(?iu)(по\s+карте|картой|\bcard\b|\bcardul\b|\bcu\s+cardul\b)""")
    private val tariffRegex = Regex("""(?iu)^(bolt|комфорт|comfort|confort|эконом|economy|econom|xl|premium|green|women)$""")
    private val rating = Regex("""[★⭐*]\s*([345](?:[.,]\d{1,2})?)(?!\d)|(?<![\d.,])([345](?:[.,]\d{1,2})?)\s*[★⭐*]""")

    private fun num(s: String) = s.replace(Regex("""[ \u00A0\u202F\u2009]"""), "").replace(',', '.').toDoubleOrNull()

    private fun normCurrency(c: String): String = when (c.lowercase()) {
        "mdl", "lei", "лей", "леев", "l", "л" -> "L"
        "eur", "€" -> "€"
        "uah", "грн", "₴" -> "₴"
        "usd", "$" -> "$"
        "pln", "zł", "zl" -> "zł"
        else -> c.uppercase()
    }

    private fun km(value: Double, unit: String) = if (unit.lowercase() == "m" || unit.lowercase() == "м") value / 1000.0 else value

    /** Правдоподобный отрезок: средняя скорость 3…135 км/ч; 0 мин — только для очень короткой подачи. */
    private fun plausible(km: Double, min: Int): Boolean {
        if (km < 0 || km > 500 || min < 0 || min > 600) return false
        if (min == 0) return km <= 0.5
        val speed = km / min * 60
        return speed in 3.0..135.0
    }

    private data class Found(val pos: Int, val seg: Segment)

    fun parse(rawLines: List<String>): Result {
        val lines = rawLines.flatMap { it.split('\n') }.map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return Result.NotOffer("пустой экран")
        val text = lines.joinToString("\n")

        // ---- цена ----
        val prices = mutableListOf<Pair<Double, String>>()
        var range: Pair<Double, String>? = null
        for (line in lines) {
            if (line.trimStart().startsWith("+")) continue // «+10 MDL» — надбавка, а не цена заказа
            priceRange.find(line)?.let { m ->
                val g = m.groupValues
                val (a, b, c) = if (g[1].isNotEmpty()) Triple(g[1], g[2], g[3]) else Triple(g[5], g[6], g[4])
                val lo = num(a); val hi = num(b)
                if (lo != null && hi != null && lo > 0 && hi >= lo) range = minOf(lo, hi) to normCurrency(c)
                return@let
            }
            if (priceRange.containsMatchIn(line)) continue
            priceAfter.findAll(line).forEach { m -> num(m.groupValues[1])?.let { prices += it to normCurrency(m.groupValues[2]) } }
            priceBefore.findAll(line).forEach { m -> num(m.groupValues[2])?.let { prices += it to normCurrency(m.groupValues[1]) } }
        }
        val distinct = prices.filter { it.first > 0 }.distinctBy { it.first }
        val price: Pair<Double, String>
        val isRange: Boolean
        when {
            distinct.size > 1 -> return Result.NotOffer("несколько разных цен: ${distinct.joinToString { "${it.first}" }}")
            distinct.size == 1 && range == null -> { price = distinct[0]; isRange = false }
            distinct.isEmpty() && range != null -> { price = range!!; isRange = true }
            distinct.size == 1 && range != null -> return Result.NotOffer("и цена, и вилка цены")
            else -> {
                val ocr = lines.mapNotNull { l -> ocrPrice.find(l)?.let { num(it.groupValues[1]) } }.filter { it > 0 }.distinct()
                if (ocr.size != 1) return Result.NotOffer(if (ocr.isEmpty()) "нет цены" else "несколько разных цен: ${ocr.joinToString()}")
                price = ocr[0] to "L"; isRange = false
            }
        }
        if (price.first > 100_000) return Result.NotOffer("цена вне диапазона")

        // ---- отрезки мин·км ----
        val found = mutableListOf<Found>()
        var offset = 0
        val used = mutableListOf<IntRange>()
        for (line in lines) {
            val local = mutableListOf<Found>()
            distThenTime.findAll(line).forEach { m ->
                val g = m.groupValues
                val d = num(g[1]) ?: return@forEach
                val min = (g[3].toIntOrNull() ?: 0) * 60 + (g[4].toIntOrNull() ?: return@forEach)
                val k = km(d, g[2])
                if (plausible(k, min)) { local += Found(offset + m.range.first, Segment(k, min)); used += (offset + m.range.first)..(offset + m.range.last) }
            }
            timeThenDist.findAll(line).forEach { m ->
                val g = m.groupValues
                val start = offset + m.range.first
                if (used.any { start in it }) return@forEach
                val min = (g[1].toIntOrNull() ?: 0) * 60 + (g[2].toIntOrNull() ?: return@forEach)
                val d = num(g[3]) ?: return@forEach
                val k = km(d, g[4])
                if (plausible(k, min)) { local += Found(start, Segment(k, min)); used += start..(offset + m.range.last) }
            }
            if (local.isEmpty()) ocrTimeThenKm.findAll(line).forEach { m ->
                val min = m.groupValues[1].toIntOrNull() ?: return@forEach
                val k = num(m.groupValues[2]) ?: return@forEach
                if (plausible(k, min)) local += Found(offset + m.range.first, Segment(k, min))
            }
            found += local
            offset += line.length + 1
        }
        found.sortBy { it.pos }
        // Подпись маршрута на карте («9.4 km · 18 мин») повторяет отрезок карточки. Оставляем
        // последнее появление каждого отрезка — порядок «подача, поездка» берём с самой карточки.
        val deduped = found.filterIndexed { i, f -> found.drop(i + 1).none { it.seg == f.seg } }
        found.clear(); found += deduped

        // Отдельное «X км» без минут — длина поездки, если отрезок поездки не найден.
        val soloKmValue = lines.firstNotNullOfOrNull { l -> soloKm.find(l)?.let { num(it.groupValues[1]) } }?.takeIf { it > 0 && it <= 500 }

        val pickup = found.firstOrNull()?.seg
        val tripParts = found.drop(1).map { it.seg }
        val trip = when {
            tripParts.isNotEmpty() -> Segment(tripParts.sumOf { it.km }, tripParts.sumOf { it.min ?: 0 })
            soloKmValue != null && (pickup == null || abs(soloKmValue - pickup.km) > 0.05) -> Segment(soloKmValue, null)
            else -> null
        }
        if (pickup == null && trip == null) return Result.NotOffer("цена есть, но нет ни подачи, ни поездки")

        // ---- рейтинг ----
        val r = rating.findAll(text).mapNotNull { m -> num(m.groupValues[1].ifEmpty { m.groupValues[2] }) }
            .firstOrNull { it in 3.0..5.0 }

        val surge = lines.firstNotNullOfOrNull { l -> surgeRegex.find(l)?.let { num(it.groupValues[1]) } }?.takeIf { it in 1.0..9.9 }
        val payment = when {
            lines.any { cashRegex.containsMatchIn(it) } -> "CASH"
            lines.any { cardRegex.containsMatchIn(it) } -> "CARD"
            else -> null
        }
        val tariff = lines.firstOrNull { tariffRegex.matches(it.trim()) }?.trim()

        return Result.Ok(Offer(price.first, isRange, price.second, pickup, trip, tripParts.size.coerceAtLeast(if (trip != null) 1 else 0), r, surge, payment, tariff))
    }
}

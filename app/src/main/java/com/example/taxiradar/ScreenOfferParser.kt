package com.example.taxiradar

/** A structural taxi card: provider/category + two travel legs + rating or accept action. */
object ScreenOfferParser {
    data class Offer(val fare: Double?, val pickupKm: Double, val tripKm: Double,
        val pickupMinutes: Double, val tripMinutes: Double, val addresses: List<String>,
        val afterCommission: Boolean, val tariff: String? = null, val multiplier: Double = 1.0,
        val boltCategory: String? = null, val multiplierKnown: Boolean = true) {
        // Fare can appear a frame later; this must not reopen a card dismissed by the driver.
        val identity get() = "$pickupKm|$tripKm|$pickupMinutes|$tripMinutes"
    }
    private val money = Regex("""(?iu)(\d{1,5}(?:[.,]\d{1,2})?)\s*(?:MDL|lei|лей|L)(?![\p{L}])""")
    private val leg = Regex("""(?iu)(\d{1,3}(?:[.,]\d+)?)\s*(?:min(?:ute)?|мин(?:ут[ыа]?)?)\.?[\s·•.*eе‹®+|:—–_\-]{0,12}(\d{1,3}(?:[.,]\d+)?)\s*(?:km|км)""")
    private val category = Regex("(?iu)(\\bBolt\\b|\\bUber\\b|комфорт|comfort|эконом|econom|standard|premium|премиум|питомец|pet\\b|\\bXL\\b|детское|scaun)")
    private val rating = Regex("""\d[.,]\d\s*[*★☆хx·‹]?\s*\(\d+\)|\d[.,]\d\s*[*★☆]""")
    private val accept = Regex("(?iu)(accept[ăa]\\b|\\baccept\\b|принять)")
    // Надписи карточки и карты Bolt, которые OCR подмешивает в адрес: спрос, отказ, оплата, «вне радиуса», «31.5 km · 41 min».
    private val notAddress = Regex("(?iu)(cerere|спрос|respingerea|отказ|rata de|numerar|наличн|по карте|afara razei|вне радиус|^\\d+[.,]?\\d*\\s*(m|km|км|м)\\b)")
    /** Строка «3 мин • 1.6 km» — подача или поездка (для разбора по положению на экране). */
    fun isLeg(line: String) = leg.containsMatchIn(normalize(line))
    fun normalize(text: String): String = text
        .replace(Regex("(?iu)(?<=[0-9Зз])\\s*[mм][uиimм][hнn](?=\\s|[·•.])"), " min")
        .replace(Regex("(?iu)(?<![\\p{L}0-9])[ЗзZz](?=\\s*(?:мин|min))"), "3")
        .replace(Regex("(?iu)\\b[кk][мm]\\b"), "km")
        .replace('\u00a0', ' ')
    fun parse(text: String): Offer? {
        val lines = normalize(text).lines().map { it.trim().trim('|', '[', ']') }.filter { it.isNotEmpty() }
        val legs = lines.mapIndexedNotNull { i, s -> leg.find(s)?.let { i to it } }
        if (legs.size != 2) return null
        val headerEnd = legs.first().first
        // Category replaces the missing brand badge on the user's Comfort screenshots.
        if (lines.take(headerEnd + 1).none { category.containsMatchIn(it) }) return null
        if (lines.none { accept.containsMatchIn(it) || rating.containsMatchIn(it) }) return null
        val header = lines.take(headerEnd).joinToString(" ")
        if (Regex("(?iu)\\d+(?:[.,]\\d+)?\\s*(?:EUR|PLN|zł|€|USD)").containsMatchIn(header)) return null
        val notPrice = Regex("(?iu)(bonus|supliment|серия|надбав|\\+|restante|^\\s*≈|^\\s*(?:после|dup[ăa]|after)(?=\\s|$)|\\bL\\s*/)")
        // Обычно цена над строками подачи и поездки, но OCR иногда читает её ниже — тогда ищем по всей карточке.
        val priceIndex = lines.indices.firstOrNull { i -> i < headerEnd && !notPrice.containsMatchIn(lines[i]) && money.containsMatchIn(lines[i]) }
            ?: lines.indices.firstOrNull { i -> !notPrice.containsMatchIn(lines[i]) && money.containsMatchIn(lines[i]) && !leg.containsMatchIn(lines[i]) }
        val price = priceIndex?.let { money.find(lines[it])!!.groupValues[1].replace(',', '.').toDoubleOrNull() }
        val values = legs.map { (_, m) -> m.groupValues[1].replace(',', '.').toDouble() to m.groupValues[2].replace(',', '.').toDouble() }
        if ((price != null && price !in 0.01..10000.0) || values.any { it.first !in 0.1..720.0 || it.second !in 0.01..1000.0 }) return null
        val addresses = legs.mapIndexed { j, (i, _) ->
            val end = if (j == 0) legs[1].first else lines.size
            val parts = mutableListOf<String>()
            for (raw in lines.subList(i + 1, end)) {
                val line = raw.trim().trim('|', '[', ']', '=', '*', ' ')
                if (accept.containsMatchIn(line)) break
                // Строка с ценой («357,00 MDL») — не адрес.
                if (line.length < 5 || !line.any { it.isLetter() } || rating.containsMatchIn(line) || money.containsMatchIn(line) ||
                    Regex("(?iu)(bonus|supliment|comision|commission|комисси|серия|restante|google|openstreetmap)").containsMatchIn(line) ||
                    notAddress.containsMatchIn(line)) continue
                parts += line
                // Join wrapped POI/street lines instead of geocoding just the POI name.
                if (parts.joinToString(" ").any { it.isDigit() } &&
                    (Regex("(?iu)(chi[șs]in[ăa]u|кишин[её]в)").containsMatchIn(line) ||
                        (line.contains(',') && !line.endsWith(',') && line.any { it.isDigit() }))) break
                if (parts.size == 4) break
            }
            // A complete city/POI is a valid destination even without a house number.
            parts.joinToString(" ").take(260).takeIf {
                it.any(Char::isDigit) || Regex("(?iu)(shopping|malldova|mall\\b|главн\\S*\\s+вход|intrare|молдов|moldova|[șşs]tefan|штефан|strada|улица|bulevard|clinica|spital|aeroport)").containsMatchIn(it)
            }.orEmpty()
        }
        val caption = priceIndex?.let { lines.subList(it, minOf(it + 2, headerEnd.coerceAtLeast(it + 1))).joinToString(" ") }.orEmpty()
        val after = Regex("(?iu)(dup[ăa] (?:deducerea )?comision|after commission|net fare|после (?:вычета )?комиссии)").containsMatchIn(caption)
        val tariff = when {
            Regex("(?iu)(комфорт|comfort)\\s*\\+").containsMatchIn(header) -> "комфорт+"
            Regex("(?iu)(комфорт|comfort)").containsMatchIn(header) -> "комфорт"
            Regex("(?iu)(эконом|econom|standard)").containsMatchIn(header) -> "эконом"
            else -> null
        }
        // «1.2x», а если OCR потерял точку — «12x» (это 1.2: спрос у Bolt от 1.1 до 3.0). Сначала в шапке, потом во всей карточке.
        val mult = Regex("""(?iu)(?<![\d.,])([1-9](?:[.,]\d{1,2})?|[1-3]\d)\s*[xх×](?!\s*\()""")
        val parsedMultiplier = (mult.find(header) ?: mult.find(lines.joinToString(" ")))?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            ?.let { if (it >= 10) it / 10 else it }?.takeIf { it in 1.0..3.0 }
        val multiplier = parsedMultiplier ?: 1.0
        val multiplierKnown = parsedMultiplier != null || !Regex("(?iu)(спрос|cerere|demand)").containsMatchIn(header)
        val boltCategory = if (Regex("(?iu)\\bUber\\b").containsMatchIn(header)) null else BoltFareEstimator.category(header)
        return Offer(price, values[0].second, values[1].second, values[0].first, values[1].first, addresses, after, tariff, multiplier, boltCategory, multiplierKnown)
    }
    data class Metrics(val net: Double, val distance: Double, val minutes: Double) {
        val perKm get() = net / distance
        val perMinute get() = net / minutes
    }
    fun metrics(o: Offer, costs: OrderEconomics.Costs, extraMinutes: Double): Metrics {
        val fare = requireNotNull(o.fare) { "Resolve hidden fare before computing profit" }
        val distance = o.pickupKm + o.tripKm
        val commission = if (o.afterCommission) 0.0 else costs.commission.coerceIn(0.0, 100.0)
        return Metrics(fare * (1 - commission / 100) - distance * costs.perKm,
            distance, o.pickupMinutes + o.tripMinutes + extraMinutes.coerceAtLeast(0.0))
    }
    /** Literal replacements, longest first. No regex supplied by users. */
    fun correct(text: String, dictionary: String): String {
        var result = text
        dictionary.lines().mapNotNull { line ->
            val parts = line.split("=>", limit = 2)
            if (parts.size == 2 && parts[0].trim().isNotEmpty()) parts[0].trim() to parts[1].trim() else null
        }.sortedByDescending { it.first.length }.take(100).forEach { (from, to) -> result = result.replace(from, to, ignoreCase = true) }
        return result
    }
}

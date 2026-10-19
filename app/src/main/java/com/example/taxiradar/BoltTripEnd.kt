package com.example.taxiradar

import android.content.Context

/**
 * Конец заказа Bolt — экран «Подтвердить стоимость» / «Confirmă tariful»: записываем поездку в смену,
 * как в Яндексе по «Заказ завершён». Цена — «Итог / Total», если он есть (наличные + компенсация Bolt
 * на баланс), иначе сумма на экране. Оплата — наличные, если клиент платит наличными, иначе карта.
 */
object BoltTripEnd {
    /** [balance] — компенсация Bolt на баланс водителя (скидка пассажиру), входит в [total]. */
    data class End(val total: Int, val cash: Int, val payment: String, val balance: Int = 0)

    private val title = Regex("""(?iu)(подтверди\p{L}*\s+стоимость|confirm[ăa]\s+tariful|confirm\p{L}*\s+(the\s+)?(fare|price|tariff))""")
    private val money = Regex("""(\d{1,5})(?:[.,](\d{1,2}))?\s*(?:MDL|lei|L)\b""")
    // \b в Kotlin не видит границу русского слова — поэтому «не буква» явно.
    private val totalWord = Regex("""(?iu)^\s*(итог|итого|total)(?!\p{L})""")
    private val cashWord = Regex("""(?iu)(наличн|numerar|cash)""")
    private val cardWord = Regex("""(?iu)(безналич|fără numerar|fara numerar|cashless|card)""")
    private val balanceWord = Regex("""(?iu)(на ваш баланс|начислит|în contul tău|in contul tau|adaugă|to your balance)""")

    private fun amount(line: String) = money.find(line)?.let { m ->
        val whole = m.groupValues[1].toInt()
        if (m.groupValues[2].isNotEmpty() && m.groupValues[2].padEnd(2, '0').toInt() >= 50) whole + 1 else whole
    }

    /** Разбор текста экрана Bolt; null — это не экран завершения. */
    fun parse(text: String): End? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.none { title.containsMatchIn(it) }) return null
        // «Итог 284,00 MDL» — в одной строке или на следующей.
        val total = lines.indices.firstNotNullOfOrNull { i ->
            if (!totalWord.containsMatchIn(lines[i])) null else amount(lines[i]) ?: lines.getOrNull(i + 1)?.let { amount(it) }
        }
        // Сумма наличными — строка с суммой прямо над подписью «Клиент оплатит наличными» / «Colectează numerar».
        val cashIndex = lines.indexOfFirst { cashWord.containsMatchIn(it) && !cardWord.containsMatchIn(it) }
        val cash = if (cashIndex < 0) 0 else (amount(lines[cashIndex]) ?: (cashIndex - 1 downTo maxOf(0, cashIndex - 2)).firstNotNullOfOrNull { amount(lines[it]) } ?: 0)
        // Компенсация Bolt — сумма над «Bolt начислит на ваш баланс» / «Bolt adaugă în contul tău».
        val balanceIndex = lines.indexOfFirst { balanceWord.containsMatchIn(it) }
        val balance = if (balanceIndex < 0) 0 else (amount(lines[balanceIndex]) ?: (balanceIndex - 1 downTo maxOf(0, balanceIndex - 2)).firstNotNullOfOrNull { amount(lines[it]) } ?: 0)
        val first = lines.firstNotNullOfOrNull { amount(it) } ?: return null
        val sum = total ?: first
        if (sum <= 0) return null
        return End(sum, cash, if (cash > 0) "cash" else "card", balance)
    }

    private var lastKey = ""
    private var lastAt = 0L

    /**
     * Экран Bolt (из спец. возможности). [offer] — последняя карточка этого заказа (откуда-куда, км),
     * [offerAt] — когда её видели (≈ взяли заказ): время заказа = до экрана завершения.
     */
    fun onScreen(c: Context, text: String, offer: ScreenOfferParser.Offer?, offerAt: Long, km: Double?) {
        val end = parse(text) ?: return
        val now = System.currentTimeMillis()
        // Экран висит, пока водитель не нажмёт «Подтвердить», — одна запись на заказ.
        val key = "${end.total}|${offer?.identity.orEmpty()}"
        if (key == lastKey && now - lastAt < 15 * 60_000L) return
        lastKey = key; lastAt = now
        val minutes = if (offer != null && offerAt > 0 && now - offerAt in 0..4 * 3600_000L) ((now - offerAt) / 60_000).toInt().coerceAtLeast(1) else 1
        DriverJournal.autoStart(c)
        DriverJournal.add(c, end.total, offer?.fare?.toInt() ?: 0, km ?: offer?.let { it.tripKm } ?: 0.0, offer?.pickupKm ?: 0.0, minutes, "",
            confirmed = true, from = offer?.addresses?.getOrNull(0).orEmpty(), to = offer?.addresses?.getOrNull(1).orEmpty(),
            payment = end.payment, service = "bolt", balance = end.balance)
        TripLog.d("Bolt: поездка в смену — ${end.total} L (${if (end.payment == "cash") "наличными ${end.cash}" else "картой"}" +
            (if (end.balance > 0) ", компенсация Bolt ${end.balance}" else "") + "), $minutes мин")
    }
}

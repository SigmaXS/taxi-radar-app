package com.example.taxiradar

import java.util.Locale

/**
 * Тексты карточки Bolt (без Android — проверяются тестами). Цвет строки — тег, саму палитру выбирает оверлей.
 * Слова «прибыль»/«чистыми» не используем: «Выручка» — после комиссии, «После расходов» — минус машина.
 */
object BoltCardFormat {
    enum class Tone { NORMAL, MUTED, GOOD, OK, BAD }
    data class Row(val text: String, val tone: Tone = Tone.NORMAL, val big: Boolean = false)

    private fun f1(v: Double) = if (v < 10) String.format(Locale.US, "%.1f", v) else Math.round(v).toString()
    private fun money(v: Double) = Math.round(v).toString()
    private fun tone(g: BoltEconomics.Grade?) = when (g) {
        BoltEconomics.Grade.GOOD -> Tone.GOOD
        BoltEconomics.Grade.OK -> Tone.OK
        BoltEconomics.Grade.BAD -> Tone.BAD
        null -> Tone.NORMAL
    }

    fun rows(o: BoltOfferParser.Offer, m: BoltEconomics.Metrics, s: BoltEconomics.Settings, ru: Boolean): List<Row> {
        fun t(r: String, o2: String) = if (ru) r else o2
        val cur = o.currency
        val out = mutableListOf<Row>()

        // Главная строка: цена с карточки и рейтинг.
        val price = (if (o.priceIsRange) t("от ", "de la ") else "") + money(o.price) + " " + cur
        val rating = o.rating?.let { "  ★" + String.format(Locale.US, "%.2f", it).trimEnd('0').trimEnd('.') } ?: ""
        out += Row("Bolt · $price$rating", if (m.lowRating) Tone.BAD else Tone.NORMAL, big = true)
        val tags = listOfNotNull(
            o.tariff,
            o.surge?.let { "×" + String.format(Locale.US, "%.1f", it) + t(" спрос (уже в цене)", " cerere (inclus)") },
            when (o.payment) { "CASH" -> t("💵 наличные", "💵 numerar"); "CARD" -> t("💳 картой", "💳 card"); else -> null }
        )
        if (tags.isNotEmpty()) out += Row(tags.joinToString(" · "), Tone.MUTED)

        // Главная метрика — за час (если нет — за км), цвет по порогу.
        when {
            m.perHour != null -> out += Row("≈ ${money(m.perHour)} $cur/" + t("час", "oră"), tone(m.perHourGrade), big = true)
            m.perKm != null -> out += Row("≈ ${f1(m.perKm)} $cur/km", tone(m.perKmGrade), big = true)
            else -> out += Row(t("За час и за км: нет данных о поездке", "Pe oră și pe km: lipsesc datele cursei"), Tone.MUTED)
        }
        if (m.perHour != null && m.perKm != null) out += Row("${f1(m.perKm)} $cur/km", tone(m.perKmGrade))
        if (m.perMin != null && s.minPerMin > 0) out += Row("${String.format(Locale.US, "%.2f", m.perMin)} $cur/" + t("мин", "min"), tone(m.perMinGrade))

        // Подача и поездка — как на карточке; чего нет — так и пишем.
        out += o.pickup?.let { Row(t("Подача ", "Preluare ") + "${f1(it.km)} km · ${it.min ?: "?"} " + t("мин", "min"), if (m.shortPickup) Tone.GOOD else Tone.NORMAL) }
            ?: Row(t("Подача: нет данных", "Preluare: lipsesc datele"), Tone.MUTED)
        out += o.trip?.let {
            Row(t("Поездка ", "Cursă ") + "${f1(it.km)} km · " + (it.min?.let { mn -> "$mn " + t("мин", "min") } ?: t("время неизвестно", "durată necunoscută")) +
                (if (o.tripSegments > 1) t(" (${o.tripSegments} отрезка)", " (${o.tripSegments} segmente)") else ""))
        } ?: Row(t("Поездка: нет данных", "Cursă: lipsesc datele"), Tone.MUTED)
        if (m.totalKm != null && o.pickup != null && o.trip != null) {
            out += Row(t("Всего ", "Total ") + "${f1(m.totalKm)} km" + (m.totalMin?.let { " · ${it + s.serviceMin} " + t("мин", "min") + (if (s.serviceMin > 0) t(" (с обслуж. ${s.serviceMin})", " (cu deservire ${s.serviceMin})") else "") } ?: ""), Tone.MUTED)
        }

        // Деньги: выручка и после расходов.
        val commission = if (s.commissionPercent > 0) t(" (−${f1(s.commissionPercent)}% комиссии)", " (−${f1(s.commissionPercent)}% comision)") else ""
        out += Row(t("Выручка ", "Venit ") + "${money(m.revenue)} $cur$commission")
        out += if (m.afterCosts != null) Row(t("После расходов машины ≈ ", "După costurile mașinii ≈ ") + "${money(m.afterCosts)} $cur")
            else Row(t("Расходы машины не заполнены — «за км/час» по выручке", "Costurile mașinii lipsesc — «pe km/oră» din venit"), Tone.MUTED)

        // Итог по порогам.
        val misses = mutableListOf<String>()
        if (m.perHourGrade == BoltEconomics.Grade.BAD) misses += t("в час < ${money(s.minPerHour)}", "pe oră < ${money(s.minPerHour)}")
        if (m.perKmGrade == BoltEconomics.Grade.BAD) misses += t("за км < ${f1(s.minPerKm)}", "pe km < ${f1(s.minPerKm)}")
        if (m.perMinGrade == BoltEconomics.Grade.BAD) misses += t("за мин < ${f1(s.minPerMin)}", "pe min < ${f1(s.minPerMin)}")
        if (m.lowRating) misses += t("рейтинг < ${f1(s.minRating)}", "rating < ${f1(s.minRating)}")
        when {
            misses.isNotEmpty() -> out += Row(t("Ниже порога: ", "Sub prag: ") + misses.joinToString(", "), Tone.BAD)
            m.overall != null -> out += Row(if (m.overall == BoltEconomics.Grade.GOOD) t("✓ Выше ваших порогов", "✓ Peste pragurile dvs.") else t("✓ На уровне порогов", "✓ La nivelul pragurilor"), tone(m.overall))
        }
        return out
    }
}

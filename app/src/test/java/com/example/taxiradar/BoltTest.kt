package com.example.taxiradar

import org.junit.Assert.*
import org.junit.Test

/**
 * ВНИМАНИЕ: строки карточек ниже — синтетические, составлены по форматам, которые разбирает 1H Profit
 * (регулярные выражения p0.calc: «N min · X km», «X km · N min», цена с валютой, «4.9★»).
 * Это НЕ снятые с телефона экраны Bolt Driver. После первых реальных карточек (диагностика Bolt
 * в журнале радара) их нужно добавить сюда отдельными тестами.
 */
class BoltTest {
    private fun ok(lines: List<String>) = (BoltOfferParser.parse(lines) as BoltOfferParser.Result.Ok).offer

    // ---------- разбор ----------

    @Test fun pickupThenTripEnglish() {
        val o = ok(listOf("Economy", "4.92 ★", "3 min · 1.2 km", "Strada Ismail 21", "18 min · 9.4 km", "bd. Dacia 5", "MDL 125", "Accept"))
        assertEquals(125.0, o.price, 0.001)
        assertEquals("L", o.currency)
        assertFalse(o.priceIsRange)
        assertEquals(1.2, o.pickup!!.km, 0.001); assertEquals(3, o.pickup!!.min)
        assertEquals(9.4, o.trip!!.km, 0.001); assertEquals(18, o.trip!!.min)
        assertEquals(4.92, o.rating!!, 0.001)
    }

    @Test fun russianUnitsCommaDecimalsAndKmFirst() {
        val o = ok(listOf("125,50 лей", "1,3 км · 4 мин", "12,8 км · 25 мин"))
        assertEquals(125.5, o.price, 0.001)
        assertEquals(1.3, o.pickup!!.km, 0.001); assertEquals(4, o.pickup!!.min)
        assertEquals(12.8, o.trip!!.km, 0.001); assertEquals(25, o.trip!!.min)
        assertNull(o.rating)
    }

    @Test fun metersAndHours() {
        val o = ok(listOf("80 L", "600 m · 2 min", "1 h 5 min · 62 km"))
        assertEquals(0.6, o.pickup!!.km, 0.001)
        assertEquals(65, o.trip!!.min)
        assertEquals(62.0, o.trip!!.km, 0.001)
    }

    @Test fun minutesWordIsNotMeters() {
        // «5 min» не должно читаться как «5 m».
        val o = ok(listOf("60 MDL", "5 min · 2.0 km", "10 min · 4.5 km"))
        assertEquals(2.0, o.pickup!!.km, 0.001)
        assertEquals(5, o.pickup!!.min)
    }

    @Test fun multiStopTripIsSummed() {
        val o = ok(listOf("200 MDL", "2 min · 0.8 km", "10 min · 4 km", "12 min · 6 km"))
        assertEquals(10.0, o.trip!!.km, 0.001); assertEquals(22, o.trip!!.min); assertEquals(2, o.tripSegments)
    }

    @Test fun standaloneKmGivesTripWithoutInventedTime() {
        val o = ok(listOf("150 MDL", "4 min · 1.5 km", "14.2 km"))
        assertEquals(14.2, o.trip!!.km, 0.001)
        assertNull(o.trip!!.min)
    }

    @Test fun twoDifferentPricesAreNotOneOffer() {
        assertTrue(BoltOfferParser.parse(listOf("125 MDL", "3 min · 1 km", "98 MDL", "7 min · 3 km")) is BoltOfferParser.Result.NotOffer)
    }

    @Test fun samePriceTwiceIsFine() {
        assertEquals(125.0, ok(listOf("125 MDL", "3 min · 1 km", "Accept 125 MDL", "10 min · 5 km")).price, 0.001)
    }

    @Test fun bonusLineIsNotPrice() {
        val o = ok(listOf("+15 MDL", "140 MDL", "3 min · 1 km", "10 min · 5 km"))
        assertEquals(140.0, o.price, 0.001)
    }

    @Test fun priceRangeUsesLowerBoundAndIsMarked() {
        val o = ok(listOf("120–140 MDL", "3 min · 1 km", "10 min · 5 km"))
        assertTrue(o.priceIsRange)
        assertEquals(120.0, o.price, 0.001)
    }

    @Test fun implausibleSpeedIsRejected() {
        // 50 км за 2 минуты — ошибка чтения, а не отрезок.
        val r = BoltOfferParser.parse(listOf("90 MDL", "2 min · 50 km"))
        assertTrue(r is BoltOfferParser.Result.NotOffer)
    }

    @Test fun noPriceIsNotOffer() {
        assertTrue(BoltOfferParser.parse(listOf("Go online", "3 min · 1 km")) is BoltOfferParser.Result.NotOffer)
        assertTrue(BoltOfferParser.parse(emptyList()) is BoltOfferParser.Result.NotOffer)
    }

    @Test fun addressWithLetterLIsNotPrice() {
        // «L» внутри слов и номер дома рядом с буквой не должны стать ценой.
        val o = ok(listOf("Lux", "str. Liviu Deleanu 7", "110 L", "3 min · 1 km", "9 min · 4 km"))
        assertEquals(110.0, o.price, 0.001)
    }

    // ---------- реальные карточки Bolt Driver (скриншоты водителя, 15.10.2026, текст переписан построчно) ----------

    private val realFar = listOf("Отклонить", "Комфорт", "По карте", "1.5x Высокий спрос", "Вне радиуса", "1 733,00 MDL",
        "Отказ не повлияет на процент принятия заказов", "Костя • 5.0 ★ (3)", "7 мин • 4.1 km", "Главный вход, Shopping Malldova",
        "102 мин • 96.6 km", "Штефан-Водэ, Молдова", "Принять")
    private val realCity = listOf("9.4 km · 18 мин", "Отклонить", "Комфорт", "По карте", "1.6x Высокий спрос", "206,00 MDL",
        "Victor • 5.0 ★ (20)", "3 мин • 1.2 km", "Strada Miron Costin 11/4, Chișinău 2068", "18 мин • 9.4 km",
        "Ciavdar Grup Clinica Veterinară, Strada Nicolae H. Costin 61, Chișinău", "Принять")
    private val realTiraspol = listOf("71.8", "Отклонить", "Bolt", "По карте", "1.1x Высокий спрос", "758,00 MDL", "Zdenek • 4.9 ★ (119)",
        "4 мин • 1.3 km", "Strada Lev Tolstoi 24/1, Chișinău", "93 мин • 71.8 km", "пер. Бочковского 2, Tiraspol 3300", "Принять")
    private val realRomanian = listOf("31.5 km · 41 min", "Refuză", "Bolt", "Numerar", "Cerere mare 1.2x", "În afara razei", "357,00 MDL",
        "Respingerea cursei nu va afecta rata de acceptare", "Andreea • 5.0 ★ (140)", "5 min • 1.5 km", "Pușkin, Blocul Pușkin 44",
        "41 min • 31.5 km", "Bogdan Petriceicu Hasdeu 5, Peresecina", "Acceptă")

    @Test fun realThousandsSeparatorPrice() {
        val o = ok(realFar)
        assertEquals(1733.0, o.price, 0.001)
        assertEquals(4.1, o.pickup!!.km, 0.001); assertEquals(7, o.pickup!!.min)
        assertEquals(96.6, o.trip!!.km, 0.001); assertEquals(102, o.trip!!.min)
        assertEquals(5.0, o.rating!!, 0.001); assertEquals(1.5, o.surge!!, 0.001)
        assertEquals("CARD", o.payment); assertEquals("Комфорт", o.tariff)
    }

    @Test fun realMapLabelIsNotPickup() {
        val o = ok(realCity)
        assertEquals(206.0, o.price, 0.001)
        assertEquals(1.2, o.pickup!!.km, 0.001); assertEquals(3, o.pickup!!.min)
        assertEquals(9.4, o.trip!!.km, 0.001); assertEquals(18, o.trip!!.min)
        assertEquals(1, o.tripSegments)
        assertEquals(1.6, o.surge!!, 0.001)
    }

    @Test fun realTiraspol() {
        val o = ok(realTiraspol)
        assertEquals(758.0, o.price, 0.001)
        assertEquals(1.3, o.pickup!!.km, 0.001); assertEquals(71.8, o.trip!!.km, 0.001); assertEquals(93, o.trip!!.min)
        assertEquals(4.9, o.rating!!, 0.001); assertEquals(1.1, o.surge!!, 0.001); assertEquals("Bolt", o.tariff)
    }

    @Test fun realRomanianCash() {
        val o = ok(realRomanian)
        assertEquals(357.0, o.price, 0.001)
        assertEquals(1.5, o.pickup!!.km, 0.001); assertEquals(5, o.pickup!!.min)
        assertEquals(31.5, o.trip!!.km, 0.001); assertEquals(41, o.trip!!.min)
        assertEquals(1.2, o.surge!!, 0.001); assertEquals("CASH", o.payment)
    }

    @Test fun realScreenshotOcrWithoutCyrillic() {
        // Так текст приходит из распознавания скриншота без кириллицы: «мин» → мусор, «•» остаётся.
        val o = ok(listOf("Komфopt", "1.6x", "206,00 MDL", "Victor • 5.0 ★ (20)", "3 MNH • 1.2 km", "Strada Miron Costin 11/4", "18 MNH • 9.4 km"))
        assertEquals(1.2, o.pickup!!.km, 0.001); assertEquals(3, o.pickup!!.min)
        assertEquals(9.4, o.trip!!.km, 0.001); assertEquals(18, o.trip!!.min)
    }

    @Test fun realCardMetrics() {
        val o = ok(realCity)
        val m = BoltEconomics.calculate(o, BoltEconomics.Settings(costPerKm = 1.5, serviceMin = 2))
        assertEquals(10.6, m.totalKm!!, 0.001); assertEquals(21, m.totalMin)
        assertEquals((206 - 1.5 * 10.6) / 23 * 60, m.perHour!!, 0.01)
    }

    // ---------- расчёт ----------

    private val base = BoltOfferParser.Offer(125.0, false, "L", BoltOfferParser.Segment(1.2, 3), BoltOfferParser.Segment(9.4, 18), 1, 4.9)

    @Test fun formulasMatch1HProfitAndIncludePickup() {
        val m = BoltEconomics.calculate(base, BoltEconomics.Settings(commissionPercent = 0.0, costPerKm = 2.0, serviceMin = 2))
        assertEquals(125.0, m.revenue, 0.001)
        assertEquals(10.6, m.totalKm!!, 0.001)
        assertEquals(21, m.totalMin)
        val after = 125.0 - 2.0 * 10.6
        assertEquals(after, m.afterCosts!!, 0.001)
        assertEquals(after / 10.6, m.perKm!!, 0.001)
        assertEquals(after / 23 * 60, m.perHour!!, 0.001)
        assertEquals(after / 23, m.perMin!!, 0.001)
        assertTrue(m.baseIsAfterCosts)
    }

    @Test fun commissionIsSubtractedOnceAndOnlyFromPrice() {
        val m = BoltEconomics.calculate(base, BoltEconomics.Settings(commissionPercent = 20.0, costPerKm = 0.0))
        assertEquals(100.0, m.revenue, 0.001)
        assertEquals(100.0, m.afterCosts!!, 0.001) // расходы 0 — второй раз комиссия не вычитается
    }

    @Test fun withoutCostsBaseIsRevenueAndMarked() {
        val m = BoltEconomics.calculate(base, BoltEconomics.Settings(costPerKm = null))
        assertNull(m.afterCosts)
        assertFalse(m.baseIsAfterCosts)
        assertEquals(125.0 / 10.6, m.perKm!!, 0.001)
        assertTrue("costs" in m.missing)
    }

    @Test fun unknownTripTimeGivesNoHourlyButKeepsPerKm() {
        val o = base.copy(trip = BoltOfferParser.Segment(14.2, null))
        val m = BoltEconomics.calculate(o, BoltEconomics.Settings(costPerKm = 1.0))
        assertNull(m.totalMin); assertNull(m.perHour); assertNull(m.perMin)
        assertNotNull(m.perKm)
        assertTrue("trip_time" in m.missing)
    }

    @Test fun pickupOnlyGivesNoRatios() {
        val o = base.copy(trip = null, tripSegments = 0)
        val m = BoltEconomics.calculate(o, BoltEconomics.Settings(costPerKm = 1.0))
        assertNull(m.perKm); assertNull(m.perHour)
        assertTrue("trip" in m.missing)
    }

    @Test fun gradesUse115PercentLikeOriginal() {
        assertEquals(BoltEconomics.Grade.GOOD, BoltEconomics.grade(115.0, 100.0))
        assertEquals(BoltEconomics.Grade.OK, BoltEconomics.grade(100.0, 100.0))
        assertEquals(BoltEconomics.Grade.BAD, BoltEconomics.grade(99.9, 100.0))
        assertNull(BoltEconomics.grade(50.0, 0.0))
        assertNull(BoltEconomics.grade(null, 10.0))
    }

    @Test fun shortPickupAndRatingFlags() {
        val m = BoltEconomics.calculate(base, BoltEconomics.Settings(shortPickupKm = 2.0, minRating = 4.95))
        assertTrue(m.shortPickup); assertTrue(m.lowRating)
    }

    // ---------- карточка ----------

    @Test fun cardSaysNoDataInsteadOfZero() {
        val o = base.copy(trip = null, tripSegments = 0)
        val s = BoltEconomics.Settings(costPerKm = null)
        val rows = BoltCardFormat.rows(o, BoltEconomics.calculate(o, s), s, ru = true).map { it.text }
        assertTrue(rows.any { it.contains("нет данных") })
        assertFalse(rows.any { it.contains("0 L/час") })
        assertFalse(rows.any { it.contains("прибыль", true) || it.contains("чистыми", true) })
    }

    @Test fun cardShowsThresholdMiss() {
        val s = BoltEconomics.Settings(costPerKm = 2.0, minPerKm = 20.0)
        val rows = BoltCardFormat.rows(base, BoltEconomics.calculate(base, s), s, ru = true)
        assertTrue(rows.any { it.tone == BoltCardFormat.Tone.BAD && it.text.contains("за км") })
    }

    // ---------- источник ----------

    @Test fun sourceDetection() {
        val bolt = setOf(TaxiServices.BOLT_DRIVER_PACKAGE)
        assertEquals(TaxiServices.Source.BOLT, TaxiServices.sourceOf("ee.mtakso.driver", "md.taxiradar.app", bolt))
        assertEquals(TaxiServices.Source.YANDEX, TaxiServices.sourceOf("ru.yandex.taximeter", "md.taxiradar.app", bolt))
        assertNull(TaxiServices.sourceOf("ee.mtakso.client", "md.taxiradar.app", bolt)) // пассажирский Bolt — не наш
        assertNull(TaxiServices.sourceOf("md.taxiradar.app", "md.taxiradar.app", bolt))
        assertNull(TaxiServices.sourceOf(null, "md.taxiradar.app", bolt))
        assertEquals(setOf("com.example.bolt"), TaxiServices.parsePackages("com.example.bolt, not a package"))
    }

    // ---------- повторы, исчезновение, смена приложения, устаревшие результаты ----------

    @Test fun repeatedEventsDoNotRedraw() {
        val t = BoltOfferTracker()
        val d1 = t.onBoltScreen(base, 0)
        assertTrue(d1 is BoltOfferTracker.Decision.Show)
        assertSame(BoltOfferTracker.Decision.Keep, t.onBoltScreen(base.copy(), 200))
        assertSame(BoltOfferTracker.Decision.Keep, t.onBoltScreen(base, 400))
    }

    @Test fun newOfferGetsNewGenerationAndOldOneIsStale() {
        val t = BoltOfferTracker()
        val g1 = (t.onBoltScreen(base, 0) as BoltOfferTracker.Decision.Show).generation
        val g2 = (t.onBoltScreen(base.copy(price = 99.0), 500) as BoltOfferTracker.Decision.Show).generation
        assertTrue(g2 > g1)
        assertFalse(t.isCurrent(g1))
        assertTrue(t.isCurrent(g2))
    }

    @Test fun disappearedOfferIsHiddenAfterGrace() {
        val t = BoltOfferTracker(goneGraceMs = 1500)
        val g = (t.onBoltScreen(base, 0) as BoltOfferTracker.Decision.Show).generation
        assertSame(BoltOfferTracker.Decision.Keep, t.onBoltScreen(null, 100))   // мигнуло — ещё держим
        assertTrue(t.onBoltScreen(null, 1700) is BoltOfferTracker.Decision.Hide)
        assertFalse(t.isCurrent(g))
    }

    @Test fun flickerDoesNotHide() {
        val t = BoltOfferTracker(goneGraceMs = 1500)
        t.onBoltScreen(base, 0)
        t.onBoltScreen(null, 100)
        assertSame(BoltOfferTracker.Decision.Keep, t.onBoltScreen(base, 600))
        assertSame(BoltOfferTracker.Decision.Keep, t.tick(2000)) // счётчик исчезновения сброшен
    }

    @Test fun otherAppHidesImmediately() {
        val t = BoltOfferTracker()
        val g = (t.onBoltScreen(base, 0) as BoltOfferTracker.Decision.Show).generation
        assertTrue(t.onOtherApp(10) is BoltOfferTracker.Decision.Hide)
        assertFalse(t.isCurrent(g))
        assertSame(BoltOfferTracker.Decision.Keep, t.onOtherApp(20))
    }

    @Test fun maxShowTimeHidesAndSameOfferDoesNotReturn() {
        val t = BoltOfferTracker(maxShowMs = 10_000)
        t.onBoltScreen(base, 0)
        assertTrue(t.tick(10_000) is BoltOfferTracker.Decision.Hide)
        assertSame(BoltOfferTracker.Decision.Keep, t.onBoltScreen(base, 10_500)) // та же карточка — не возвращаем
        assertTrue(t.onBoltScreen(base.copy(price = 130.0), 11_000) is BoltOfferTracker.Decision.Show) // новая — показываем
    }

    @Test fun userDismissInvalidatesPendingRender() {
        val t = BoltOfferTracker()
        val g = (t.onBoltScreen(base, 0) as BoltOfferTracker.Decision.Show).generation
        t.dismissedByUser(100)
        assertFalse(t.isCurrent(g))
        assertSame(BoltOfferTracker.Decision.Keep, t.onBoltScreen(base, 200))
    }
}

package com.example.taxiradar

import org.junit.Assert.*
import org.junit.Test

class ScreenOfferParserTest {
    // Transcribed structure from the user video at 26 s; fare/currency adapted to Moldova.
    private fun card(caption: String = "după deducerea comisionului") = """
        Bolt
        33,00 MDL ($caption)
        Leon · 5.0
        3 min · 1.7 km
        Strada Pușkin 4, Chișinău
        20 min · 13.5 km
        Strada Independenței 77, Chișinău
        Bonus +24 MDL
        Acceptă
    """.trimIndent()
    @Test fun readsPickupSeparatelyAndIgnoresBonus() {
        val o = ScreenOfferParser.parse(card())!!
        assertEquals(33.0, o.fare!!, 0.001)
        assertEquals(1.7, o.pickupKm, 0.001)
        assertEquals(13.5, o.tripKm, 0.001)
        assertEquals(2, o.addresses.size)
        assertTrue(o.afterCommission)
        val m = ScreenOfferParser.metrics(o, OrderEconomics.Costs(20.0, 0.0, 0.0), 2.0)
        assertEquals(33.0, m.net, 0.001)
        assertEquals(33.0 / 15.2, m.perKm, 0.001)
        assertEquals(25.0, m.minutes, 0.001)
        assertEquals(1.32, m.perMinute, 0.001)
    }
    @Test fun grossFareSubtractsCommissionAndAllTravelCosts() {
        val o = ScreenOfferParser.parse(card("fare"))!!
        val m = ScreenOfferParser.metrics(o, OrderEconomics.Costs(20.0, 8.0, 25.0, 1.0), 0.0)
        assertEquals(33 * 0.8 - 15.2 * 3, m.net, 0.001)
        assertEquals(23.0, m.minutes, 0.001)
        assertTrue(m.perMinute < 0) // losses stay visible
    }
    @Test fun rejectsForeignCurrencyAndUnrelatedScreens() {
        assertNull(ScreenOfferParser.parse(card().replace("MDL", "zł")))
        assertNull(ScreenOfferParser.parse(card().replace("Bolt", "Bank")))
        assertNull(ScreenOfferParser.parse(card().replace("Acceptă", "")))
    }
    @Test fun incompleteOrMultiStopOfferIsNotGuessed() {
        assertNull(ScreenOfferParser.parse(card().replace("20 min · 13.5 km", "")))
        assertNull(ScreenOfferParser.parse(card() + "\n5 min · 2 km"))
    }
    // Так прочитал телефон 16.10: цена оказалась ниже строки подачи, под карточкой — надписи карты, точка в «1.2x» потерялась.
    @Test fun priceReadBelowLegsIsStillThePriceNotAnAddress() {
        val o = ScreenOfferParser.parse("""
            Bolt Numerar
            Cerere mare 12x În afara razei
            Andreea 5.0 ★ (140)
            5 min · 1.5 km
            357,00 MDL
            Pușkin, Blocul Pușkin 44
            41 min · 31.5 km
            Crždieta
            Acceptă
        """.trimIndent())!!
        assertEquals(357.0, o.fare!!, 0.001)
        assertEquals("Pușkin, Blocul Pușkin 44", o.addresses[0])
        assertEquals("", o.addresses[1])
        assertEquals(1.2, o.multiplier, 0.001)
        assertTrue(o.multiplierKnown)
    }
    @Test fun dictionaryUsesLiteralReplacementAndRomanianCommaIsAccepted() {
        val input = card().replace("1.7", "1,7").replace("Pușkin", "Puskm")
        val o = ScreenOfferParser.parse(ScreenOfferParser.correct(input, "Puskm => Pușkin\nwrong entry"))!!
        assertTrue(o.addresses.first().contains("Pușkin"))
        assertEquals(1.7, o.pickupKm, 0.001)
    }
}

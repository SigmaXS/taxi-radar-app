package com.example.taxiradar

import org.junit.Assert.*
import org.junit.Test

/** Exact OCR output from the three user screenshots, not idealized hand-written inputs. */
class ScreenOfferRegressionTest {
    private fun offer(i: Int) = ScreenOfferParser.parse(javaClass.getResource("/screen-offers/offer-$i.txt")!!.readText())!!
    @Test fun comfortWithoutPriceIsAnOfferNotAnError() {
        val o = offer(1)
        assertNull(o.fare); assertEquals("комфорт", o.tariff)
        assertEquals(1.6, o.pickupKm, .001); assertEquals(12.2, o.tripKm, .001)
        assertEquals(3.0, o.pickupMinutes, .001); assertEquals(32.0, o.tripMinutes, .001)
        assertTrue(o.addresses.first().contains("Zelinski"))
        assertTrue(o.addresses.last().contains("Bucovinei"))
    }
    @Test fun romanianPricedOfferWorksWithoutReadableAcceptButton() {
        val o = offer(2)
        assertEquals(357.0, o.fare!!, .001)
        assertEquals(31.5, o.tripKm, .001)
        assertTrue(o.addresses.last().contains("Peresecina"))
    }
    @Test fun cyrillicComfortBadgeAndCyrillicDigitConfusionAreSupported() {
        val o = offer(3)
        assertEquals(206.0, o.fare!!, .001)
        assertEquals(3.0, o.pickupMinutes, .001)
        assertEquals(9.4, o.tripKm, .001)
        assertTrue(o.addresses.last().contains("Costin 61"))
    }
    @Test fun unreadableSurgeIsNotSilentlyAssumedToBeOne() {
        val o = offer(1)
        assertEquals("comfort", o.boltCategory)
        assertFalse(o.multiplierKnown)
        val raw = javaClass.getResource("/screen-offers/offer-1.txt")!!.readText()
        val corrected = ScreenOfferParser.parse(raw.replace("А1ахвысокий спрос", "1.2x высокий спрос"))!!
        assertTrue(corrected.multiplierKnown)
        assertEquals(1.2, corrected.multiplier, .001)
    }
    @Test fun minimizingDoesNotStopOrReopenTheSameOffer() {
        val state = ScreenOfferVisibility()
        state.update("one", 1000); assertTrue(state.expanded)
        state.minimize(); state.update("one", 2000); assertFalse(state.expanded)
        state.missing(3000); state.update("one", 4000); assertFalse(state.expanded)
        state.update("two", 5000); assertTrue(state.expanded)
    }
    @Test fun idleCollapsesWithoutDiscardingAnIntermittentReading() {
        val state = ScreenOfferVisibility()
        state.update("one", 1000); state.missing(2000); assertTrue(state.expanded)
        state.missing(8000); assertFalse(state.expanded)
        state.missing(15000); assertNull(state.current)
        state.update("one", 16000); assertTrue(state.expanded)
    }
    @Test fun mixedScriptMinuteUnitFromNativeAndroidOcrIsNormalized() {
        val o = offer(3)
        val text = javaClass.getResource("/screen-offers/offer-3.txt")!!.readText().replace("Змин", "3 MuH")
        val parsed = ScreenOfferParser.parse(text)!!
        assertEquals(o.tripKm, parsed.tripKm, .001)
        assertEquals(3.0, parsed.pickupMinutes, .001)
    }

}

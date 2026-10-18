package com.example.taxiradar

import org.junit.Assert.*
import org.junit.Test

class BoltOfferSnapshotTest {
    private fun offer() = ScreenOfferParser.Offer(206.0, 1.2, 9.4, 3.0, 18.0,
        listOf("Strada Miron Costin 11/4", "Strada Nicolae Costin 61"), false,
        boltCategory = "comfort", multiplier = 1.6, multiplierKnown = true)
    @Test fun incompleteFrameCannotEraseKnownPriceOrChangeDistance() {
        val state = BoltOfferSnapshot(); assertTrue(state.accept(offer()))
        assertFalse(state.accept(offer().copy(fare = null, pickupKm = 1.3, tripMinutes = 19.0,
            addresses = listOf("", ""), boltCategory = null, multiplierKnown = false)))
        assertEquals(offer(), state.current)
    }
    @Test fun actualPriceCanCompleteAHiddenOfferWithoutStartingANewOne() {
        val state = BoltOfferSnapshot(); state.accept(offer().copy(fare = null))
        val generation = state.generation
        assertFalse(state.accept(offer()))
        assertEquals(generation, state.generation); assertEquals(206.0, state.current!!.fare!!, .001)
    }
    @Test fun differentDestinationIsANewOfferEvenWithSameTravelMetrics() {
        val state = BoltOfferSnapshot(); state.accept(offer())
        assertTrue(state.accept(offer().copy(addresses = listOf("Strada Miron Costin 11/4", "Strada Nicolae Costin 80"))))
        assertEquals(2L, state.generation)
    }
    @Test fun missingFieldsAreFilledBeforeFreezingRoutes() {
        val state = BoltOfferSnapshot(); state.accept(offer().copy(addresses = listOf("", ""), multiplierKnown = false))
        assertFalse(state.accept(offer()))
        assertEquals(offer(), state.current)
    }
    @Test fun laterFareForDifferentOrderIsNotReplacedByOldFare() {
        val state = BoltOfferSnapshot(); state.accept(offer())
        assertTrue(state.accept(offer().copy(fare = 300.0)))
        assertEquals(300.0, state.current!!.fare!!, .001)
    }
    @Test fun repeatedMissingMultiplierCannotClearTheKnownDemand() {
        val state = BoltOfferSnapshot()
        val hidden = offer().copy(fare = null, multiplier = 1.2)
        state.accept(hidden)
        repeat(40) {
            assertFalse(state.accept(hidden.copy(multiplierKnown = false, multiplier = 1.0, boltCategory = null)))
            assertEquals(1.2, state.current!!.multiplier, .001)
            assertTrue(state.current!!.multiplierKnown)
        }
        assertEquals(1L, state.generation)
    }
}

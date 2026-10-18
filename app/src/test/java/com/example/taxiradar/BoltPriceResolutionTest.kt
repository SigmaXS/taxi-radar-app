package com.example.taxiradar

import org.junit.Assert.*
import org.junit.Test

class BoltPriceResolutionTest {
    private val hidden = ScreenOfferParser.Offer(null, 1.6, 12.2, 3.0, 32.0, listOf("A", "B"), false,
        multiplier = 1.2, boltCategory = "comfort")
    @Test fun hiddenFareReturnsThePreviouslySupportedCalculation() {
        assertEquals(195.6036, BoltFareEstimator.resolve(hidden, 12.9, 26.1)!!, .001)
    }
    @Test fun missingRouteOrUnknownTariffMustNotOpenAPriceCard() {
        assertNull(BoltFareEstimator.resolve(hidden, null, null))
        assertNull(BoltFareEstimator.resolve(hidden.copy(boltCategory = null), 12.9, 26.1))
        assertNull(BoltFareEstimator.resolve(hidden.copy(multiplierKnown = false), 12.9, 26.1))
    }
    @Test fun originalPriceSurvivesAnUnavailableRoute() {
        assertEquals(758.0, BoltFareEstimator.resolve(hidden.copy(fare = 758.0), null, null)!!, .001)
    }
}

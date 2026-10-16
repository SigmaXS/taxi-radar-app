package com.example.taxiradar

import org.junit.Assert.*
import org.junit.Test

class BoltFareEstimatorTest {
    @Test fun multiplierAppliesToEntirePassengerFare() {
        assertEquals((43 + 12.2 * 5.60 + 36 * 1.83) * 1.2,
            BoltFareEstimator.estimate("comfort", 12.2, 36.0, 1.2)!!, .001)
    }
    @Test fun boltAndCashEconomyRemainDifferentCategories() {
        assertEquals(91.8, BoltFareEstimator.estimate("bolt", 10.0, 10.0, 1.0)!!, .001)
        assertEquals(88.4, BoltFareEstimator.estimate("econom_cash", 10.0, 10.0, 1.0)!!, .001)
        assertEquals("bolt", BoltFareEstimator.category("Bolt Numerar"))
        assertEquals("econom_cash", BoltFareEstimator.category("Эконом Наличные"))
        assertEquals("comfort", BoltFareEstimator.category("Bolt Комфорт"))
        assertEquals("premium", BoltFareEstimator.category("Bolt Premium"))
        assertNull(BoltFareEstimator.category("Uber"))
        assertNull(BoltFareEstimator.category("Комфорт+"))
    }
    @Test fun unknownTariffsAndInvalidInputsAreNotGuessed() {
        assertNull(BoltFareEstimator.estimate(null, 10.0, 10.0, 1.0))
        assertNull(BoltFareEstimator.estimate("comfort", Double.NaN, 10.0, 1.0))
        assertNull(BoltFareEstimator.estimate("comfort", 1.0, -1.0, 1.0))
        assertEquals(60.0, BoltFareEstimator.estimate("premium", 0.0, 0.0, 1.0)!!, .001)
    }
    @Test fun independentRouteReplacesCardMetricsAndDoesNotMultiplyVisibleFareAgain() {
        val offer = ScreenOfferParser.Offer(206.0, 1.2, 9.4, 3.0, 18.0, emptyList(), false, "комфорт", 1.6)
        val route = ScreenTrafficRoute.Result(ScreenTrafficRoute.Leg(2.0, 6.0, emptyList()), ScreenTrafficRoute.Leg(10.0, 30.0, emptyList()), 0)
        val routed = route.applyTo(offer)
        val metrics = ScreenOfferParser.metrics(routed, OrderEconomics.Costs(20.0, 10.0, 20.0, 0.0), 2.0)
        assertEquals(206.0, routed.fare!!, .001)
        assertEquals(12.0, metrics.distance, .001); assertEquals(38.0, metrics.minutes, .001)
        assertEquals(140.8, metrics.net, .001)
    }
    @Test fun googlePolylineIsDecodedAndMalformedInputFails() {
        val points = ScreenTrafficRoute.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, points.size); assertEquals(38.5, points.first().first, .00001)
        assertEquals(-126.453, points.last().second, .00001)
        assertThrows(IllegalArgumentException::class.java) { ScreenTrafficRoute.decodePolyline("~~~~") }
    }
}

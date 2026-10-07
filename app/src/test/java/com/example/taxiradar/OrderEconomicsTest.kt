package com.example.taxiradar

import org.junit.Assert.*
import org.junit.Test

class OrderEconomicsTest {
    @Test fun includesPickupAndMaintenanceBeforeHourlyEstimate() {
        val result = OrderEconomics.calculate(200, 10.0, 2.0, 20, 10.0, OrderEconomics.Costs(20.0, 8.0, 25.0, 1.0))
        assertEquals(124, result.net)
        assertEquals(124.0 / 12, result.perKm!!, 0.001)
        assertEquals(248, result.perHour)
    }
    @Test fun emptyReturnCostsTimeAndEnergy() {
        val costs = OrderEconomics.Costs(20.0, 8.0, 25.0)
        val result = OrderEconomics.calculate(200, 10.0, 2.0, 20, 10.0, costs, 5.0)
        assertEquals(126, result.net)
        assertEquals(189, result.perHour)
    }
    @Test fun noTimeOrDistanceDoesNotInventRatios() {
        val result = OrderEconomics.calculate(100, 0.0, 0.0, 0, 0.0, OrderEconomics.Costs(20.0, 7.0, 25.0))
        assertEquals(80, result.net)
        assertNull(result.perHour)
        assertNull(result.perKm)
    }
    @Test fun lossIsKeptVisibleAndCommissionIsBounded() {
        assertEquals(-200, OrderEconomics.calculate(100, 100.0, 0.0, 60, 0.0, OrderEconomics.Costs(300.0, 8.0, 25.0)).net)
    }
    @Test fun priceRangeCannotBecomeNegativeAndMarginIsBounded() {
        assertEquals(90..110, OrderEconomics.range(100, 10))
        assertEquals(0..2, OrderEconomics.range(1, 30))
        assertEquals(70..130, OrderEconomics.range(100, 100))
    }
}

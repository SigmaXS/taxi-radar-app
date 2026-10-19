package com.example.taxiradar

import org.junit.Assert.*
import org.junit.Test

/** Экраны завершения Bolt со скриншотов водителей 19–20.10. */
class BoltTripEndTest {
    @Test fun cardPayment() {
        val e = BoltTripEnd.parse("Подтвердить стоимость\n100,00 MDL\nБезналичный платёж. Не берите деньги на руки.\nПодтвердить стоимость")!!
        assertEquals(100, e.total); assertEquals("card", e.payment); assertEquals(0, e.balance)
    }
    @Test fun cashWithBoltCompensationTakesTotal() {
        val e = BoltTripEnd.parse("Подтвердить стоимость\nПоездка с компенсацией\n239,00 MDL\nКлиент оплатит наличными\n+\n45,00 MDL\nBolt начислит на ваш баланс\nИтог\n284,00 MDL\nПодтвердить стоимость")!!
        assertEquals(284, e.total); assertEquals("cash", e.payment); assertEquals(239, e.cash); assertEquals(45, e.balance)
    }
    @Test fun fullDiscountIsBalanceOnly() {
        val e = BoltTripEnd.parse("Подтвердите стоимость\nПоездка с компенсацией\n0,00 MDL\nКлиент получает скидку на всю стоимость\n+\n57,00 MDL\nBolt начислит на ваш баланс\nИтог\n57,00 MDL")!!
        assertEquals(57, e.total); assertEquals("card", e.payment); assertEquals(57, e.balance)
    }
    @Test fun romanian() {
        val e = BoltTripEnd.parse("Confirmă tariful\nCursă cu compensare\n275,00 MDL\nColectează numerar de la pasager\n+\n45,00 MDL\nBolt adaugă în contul tău\nTotal\n320,00 MDL\nConfirmă tariful")!!
        assertEquals(320, e.total); assertEquals("cash", e.payment); assertEquals(275, e.cash); assertEquals(45, e.balance)
    }
    @Test fun offerCardIsNotAnEndScreen() {
        assertNull(BoltTripEnd.parse("Bolt Numerar\n357,00 MDL\n5 min · 1.5 km\nAcceptă"))
    }
}

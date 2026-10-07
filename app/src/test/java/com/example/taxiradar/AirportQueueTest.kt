package com.example.taxiradar

import org.junit.Assert.assertEquals
import org.junit.Test

class AirportQueueTest {
    @Test
    fun readsQueueScreen() {
        // Как на экране Яндекс Про в аэропорту (скриншот водителя).
        val r = AirportQueue.parse(listOf("Кишинев", "Вы в очереди, ожидайте заказ", "Ожидание в очереди", "Комфорт", "3 ч", "31 - 35"))
        assertEquals(listOf(AirportQueue.Reading("comfort", 180, 31, 35)), r)
    }

    @Test
    fun readsSeveralTariffsAndMinutes() {
        val r = AirportQueue.parse(listOf("Ожидание в очереди", "Эконом", "45 мин", "12 - 16", "Комфорт+", "1 ч 20 мин", "3 - 5"))
        assertEquals(listOf(AirportQueue.Reading("econom", 45, 12, 16), AirportQueue.Reading("comfortplus", 80, 3, 5)), r)
    }

    @Test
    fun bubbleUsesLastTariff() {
        AirportQueue.parse(listOf("Ожидание в очереди", "Комфорт", "3 ч", "31 - 35"))
        assertEquals(listOf(AirportQueue.Reading("comfort", 120, null, null)), AirportQueue.parse(listOf("Ожидание в очереди ~2 ч")))
    }

    @Test
    fun ignoresOtherScreens() {
        assertEquals(emptyList<AirportQueue.Reading>(), AirportQueue.parse(listOf("Комфорт", "3 ч", "31 - 35")))
    }
}

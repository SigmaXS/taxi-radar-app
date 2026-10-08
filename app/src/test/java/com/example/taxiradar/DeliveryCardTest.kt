package com.example.taxiradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryCardTest {
    // Карточка со скриншота водителя (безналичная оплата: полной цены на карточке нет).
    private val card = listOf("Доставка", "1.3 км • 8 мин", "1 получение, 1 вручение", "Высокий спрос", "+95 L",
        "Платная подача", "+17.8 L", "От двери до двери", "+30 L", "Откуда", "сектор Ботаника, улица Буребиста, 17А", "Принять")

    @Test
    fun recognisesDelivery() = assertTrue(DeliveryCard.isDelivery(card))

    @Test
    fun sumsAllAdditions() {
        val r = DeliveryCard.parse(card, 25)!!
        assertEquals(168, r.price)              // 25 + 95 + 17.8 + 30 = 167.8
        assertEquals(3, r.items.size)
        assertEquals(1.3, r.km!!, 0.001)
        assertEquals(8, r.minutes)
        assertEquals("Доставка: старт 25 + спрос 95 + подача 17.8 + до двери 30", DeliveryCard.explain(r, true))
    }

    @Test
    fun takesFullPriceWhenCardShowsIt() {
        val r = DeliveryCard.parse(card + listOf("Наличные", "172 L"), 25)!!
        assertEquals(172, r.price)
        assertTrue(r.fromCard)
    }
}

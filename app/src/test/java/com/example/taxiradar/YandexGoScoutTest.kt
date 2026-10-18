package com.example.taxiradar

import org.junit.Assert.*
import org.junit.Test

/** Тексты экрана Яндекс Go, прочитанные с телефона 16.10 (Индепенденцей 42/2 и Дурлешты). */
class YandexGoScoutTest {
    @Test fun readsTariffPricesAndAddress() {
        val s = YandexGoScout.parse(listOf(
            "индекс спроса: выше среднего ", "Ваш адрес, Индепенденцей 42/2, ", "Индепенденцей 42/2",
            "Тариф «Самый быстрый», От 45 L, Подача 3 мин", "от 45 L",
            "Тариф «Эконом», Высокий спрос, От 45 L, Подача 5 мин", "Эконом", " от 45 L",
            "Тариф «Комфорт», От 54 L, Подача 6 мин", "Тариф «Комфорт+», От 72 L, Подача 3 мин",
            "Заказать, от 45 L"), 1L)
        assertEquals(mapOf("econom" to 45, "comfort" to 54, "comfortplus" to 72), s.prices)
        assertEquals("Индепенденцей 42/2", s.address)
        assertTrue(s.noCars.isEmpty())
    }
    @Test fun tariffWithoutCarsIsNoCarsNotZero() {
        val s = YandexGoScout.parse(listOf(
            "Ваш адрес, Михая Эминеску 88, ", "Тариф «Самый быстрый», От 108 L, Подача 7 мин",
            "Тариф «Эконом»", "Эконом", "—", "Тариф «Комфорт»", "—",
            "Тариф «Комфорт+», От 108 L, Подача 7 мин", "Нет свободных машин"), 1L)
        assertEquals(mapOf("comfortplus" to 108), s.prices)
        assertEquals(setOf("econom", "comfort"), s.noCars)
    }
}

package com.example.taxiradar

import org.junit.Assert.*
import org.junit.Test

class BoltAddressNormalizerTest {
    @Test fun keepsSettlementAndHouseNumber() {
        assertEquals("Bogdan Petriceicu Hasdeu 5, Peresecina, Moldova", BoltAddressNormalizer.normalize("Bogdan Petriceicu Hasdeu 5, Пересечина"))
        assertEquals("пер. Бочковского 2, Tiraspol 3300, Moldova", BoltAddressNormalizer.normalize("пер. Бочковского 2, Тирасполь 3300"))
        assertEquals("Pușkin 44, Moldova", BoltAddressNormalizer.normalize("Pușkin, Blocul Pușkin 44"))
    }
}

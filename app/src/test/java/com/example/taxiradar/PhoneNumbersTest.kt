package com.example.taxiradar

import org.junit.Assert.*
import org.junit.Test

class PhoneNumbersTest {
    @Test fun manualAndDialerFormatsIdentifySameClient() {
        for (input in listOf("078 12 34 56", "+373 (78) 123-456", "00373 78 123456", "\u202A+37378123456\u202C")) {
            assertEquals("+37378123456", PhoneNumbers.normalize(input))
        }
    }
    @Test fun invalidEntryIsNotRememberedAsPhone() {
        assertNull(PhoneNumbers.normalize("123"))
        assertNull(PhoneNumbers.normalize(""))
    }
}

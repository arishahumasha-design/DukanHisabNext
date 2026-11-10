package com.example.dukanhisabnext.ui.customers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The keyboard rule ("is the typed mobile complete?") and the phonebook number clean-up. */
class MobileRulesTest {

    // ---- isCompleteMobile: when the keyboard may close ----

    @Test
    fun theExamplesFromTheRequestAreComplete() {
        assertTrue(isCompleteMobile("01711000000"))
        assertTrue(isCompleteMobile("+8801711000000"))
        assertTrue(isCompleteMobile("০১৭১১০০০০০০"))
        assertTrue(isCompleteMobile("01711 000 000"))
    }

    @Test
    fun theExamplesFromTheRequestThatAreNotComplete() {
        assertFalse(isCompleteMobile("0171100000"))   // 10 digits
        assertFalse(isCompleteMobile("01711 00"))
    }

    @Test
    fun otherCompleteWaysOfTyping() {
        assertTrue(isCompleteMobile("8801711000000"))      // country code without +
        assertTrue(isCompleteMobile("+88 01711-000000"))   // spaces and a dash
        assertTrue(isCompleteMobile("01711-000-000"))
        assertTrue(isCompleteMobile("০১৭১১ ০০০ ০০০"))      // Bengali digits and spaces
        assertTrue(isCompleteMobile("01911000000"))
    }

    @Test
    fun typingOneDigitAtATimeIsCompleteOnlyAtTheEleventhDigit() {
        val full = "01711000000"
        for (length in 1..10) {
            assertFalse("at $length digits", isCompleteMobile(full.take(length)))
        }
        assertTrue(isCompleteMobile(full))
    }

    @Test
    fun typingWithTheCountryCodeIsCompleteOnlyWhenAllDigitsAreThere() {
        val full = "+8801711000000"
        for (length in 1 until full.length) {
            assertFalse("at '${full.take(length)}'", isCompleteMobile(full.take(length)))
        }
        assertTrue(isCompleteMobile(full))
    }

    @Test
    fun notMobileNumbersAreNeverComplete() {
        assertFalse(isCompleteMobile(""))
        assertFalse(isCompleteMobile("   "))
        assertFalse(isCompleteMobile("017110000000"))     // 12 digits: too long
        assertFalse(isCompleteMobile("02711000000"))      // does not start with 01
        assertFalse(isCompleteMobile("11711000000"))
        assertFalse(isCompleteMobile("abcdefghijk"))
        assertFalse(isCompleteMobile("+8801711"))
        assertFalse(isCompleteMobile("88017110000000"))   // 14 digits
    }

    @Test
    fun completeMeansTheSameAsAValidMobile() {
        for (text in listOf("01711000000", "+8801711000000", "0171100000", "abc", "")) {
            assertEquals(normalizeMobile(text) != null, isCompleteMobile(text))
        }
    }

    // ---- phonebookNumber: a picked number, ready for the mobile box ----

    @Test
    fun aPhonebookNumberInAnyStyleBecomesTheElevenDigitMobile() {
        val styles = listOf(
            "01711000000",
            "+8801711000000",
            "+880 1711-000000",
            "+88 01711 000 000",
            "(+880) 1711 000000",
            "8801711000000",
            "01711 000 000",
            "01711-000-000",
            "০১৭১১০০০০০০",
            "+৮৮ ০১৭১১-০০০০০০"
        )
        for (raw in styles) {
            val result = phonebookNumber(raw)
            assertEquals("for '$raw'", PhonebookNumber("01711000000", true), result)
        }
    }

    @Test
    fun aLandlineIsLeftAsItIsAndMarkedInvalid() {
        val result = phonebookNumber("02-9876543")
        assertEquals("029876543", result.text) // only digits and + are kept, nothing is changed otherwise
        assertFalse(result.isValid)
    }

    @Test
    fun aForeignNumberIsLeftAsItIsAndMarkedInvalid() {
        val result = phonebookNumber("+1 (415) 555-0132")
        assertEquals("+14155550132", result.text)
        assertFalse(result.isValid)
    }

    @Test
    fun aTooShortNumberIsLeftAsItIs() {
        val result = phonebookNumber("017 110")
        assertEquals("017110", result.text)
        assertFalse(result.isValid)
    }

    @Test
    fun anEmptyPhonebookNumberGivesAnEmptyInvalidResult() {
        assertEquals(PhonebookNumber("", false), phonebookNumber(""))
        assertEquals(PhonebookNumber("", false), phonebookNumber("  - "))
    }

    @Test
    fun aValidImportedNumberIsAlsoCompleteForTheKeyboardRule() {
        val imported = phonebookNumber("+880 1711-000000")
        assertTrue(isCompleteMobile(imported.text))
    }

    @Test
    fun anInvalidImportedNumberIsNotComplete() {
        assertFalse(isCompleteMobile(phonebookNumber("02-9876543").text))
    }
}

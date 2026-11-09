package com.example.dukanhisabnext.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BengaliDigitsTest {
    @Test
    fun allDigitsConvertToBengali() {
        assertEquals("০১২৩৪৫৬৭৮৯", toBengaliDigits("0123456789"))
    }

    @Test
    fun nonDigitsStayTheSame() {
        assertEquals("৳ ১,৫০০.৫০ টাকা", toBengaliDigits("৳ 1,500.50 টাকা"))
    }

    @Test
    fun bengaliDigitsConvertBackToLatin() {
        assertEquals("0123456789", toLatinDigits("০১২৩৪৫৬৭৮৯"))
        assertEquals("12.5", toLatinDigits("১২.৫"))
    }

    @Test
    fun roundTripGivesTheOriginal() {
        val original = "Receipt 20261109-007"
        assertEquals(original, toLatinDigits(toBengaliDigits(original)))
    }
}

package com.example.dukanhisabnext.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    // ---- formatTaka ----

    @Test
    fun zero() = assertEquals("০", formatTaka(0))

    @Test
    fun wholeTakaHasNoDecimals() = assertEquals("১,৫০০", formatTaka(150_000))

    @Test
    fun paisaShowTwoDecimals() {
        assertEquals("১,৫০০.৫০", formatTaka(150_050))
        assertEquals("০.০৫", formatTaka(5))
    }

    @Test
    fun bangladeshiGrouping() {
        assertEquals("৯৯৯", formatTaka(99_900))
        assertEquals("১,০০০", formatTaka(100_000))
        assertEquals("১,২৩,৪৫৬", formatTaka(12_345_600))
        assertEquals("১,২৩,৪৫,৬৭৮", formatTaka(1_234_567_800))
    }

    @Test
    fun negativeAmounts() {
        assertEquals("-৫০", formatTaka(-5_000))
        assertEquals("-০.৫০", formatTaka(-50)) // sign must not be lost when taka part is 0
    }

    @Test
    fun veryLargeAmountDoesNotCrash() {
        // Long.MAX_VALUE paisa: just make sure grouping and digits still work.
        assertEquals("৯২,২৩,৩৭,২০,৩৬,৮৫,৪৭,৭৫৮.০৭", formatTaka(Long.MAX_VALUE))
    }

    // ---- parseTakaToPaisa ----

    @Test
    fun parsesWholeAndDecimalTaka() {
        assertEquals(120_000L, parseTakaToPaisa("1200"))
        assertEquals(1_250L, parseTakaToPaisa("12.5"))
        assertEquals(1_205L, parseTakaToPaisa("12.05"))
        assertEquals(50L, parseTakaToPaisa(".5"))
        assertEquals(1_200L, parseTakaToPaisa("12."))
    }

    @Test
    fun parsesBengaliDigitsCommasAndSpaces() {
        assertEquals(1_250L, parseTakaToPaisa("১২.৫"))
        assertEquals(120_000L, parseTakaToPaisa("১,২০০"))
        assertEquals(120_000L, parseTakaToPaisa("  1,200  "))
    }

    @Test
    fun noDoubleRoundingErrors() {
        // 0.1 + 0.2 is 0.30000000000000004 with Double; with paisa it is exact.
        assertEquals(30L, parseTakaToPaisa("0.1")!! + parseTakaToPaisa("0.2")!!)
    }

    @Test
    fun rejectsBadInput() {
        assertNull(parseTakaToPaisa(""))
        assertNull(parseTakaToPaisa("   "))
        assertNull(parseTakaToPaisa("."))
        assertNull(parseTakaToPaisa("abc"))
        assertNull(parseTakaToPaisa("12a"))
        assertNull(parseTakaToPaisa("-5"))       // negative money is never typed in
        assertNull(parseTakaToPaisa("1.234"))    // more than 2 decimals
        assertNull(parseTakaToPaisa("1.2.3"))
        assertNull(parseTakaToPaisa("1234567890123456")) // 16 digits: too large
    }

    @Test
    fun formatAndParseAgree() {
        val paisa = 12_345_678L
        assertEquals(paisa, parseTakaToPaisa(formatTaka(paisa)))
    }
}

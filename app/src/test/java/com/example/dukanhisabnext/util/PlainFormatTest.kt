package com.example.dukanhisabnext.util

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class PlainFormatTest {

    // ---- formatTakaPlain: Latin digits, no commas, trailing zeros dropped ----

    @Test
    fun takaPlain() {
        assertEquals("0", formatTakaPlain(0))
        assertEquals("1500", formatTakaPlain(150_000))
        assertEquals("1500.5", formatTakaPlain(150_050))
        assertEquals("1500.55", formatTakaPlain(150_055))
        assertEquals("0.05", formatTakaPlain(5))
    }

    @Test
    fun takaPlainCanBeParsedBack() {
        for (paisa in listOf(0L, 5L, 50L, 150_050L, 12_345_678L)) {
            assertEquals(paisa, parseTakaToPaisa(formatTakaPlain(paisa)))
        }
    }

    // ---- formatQuantityPlain ----

    @Test
    fun quantityPlain() {
        assertEquals("0", formatQuantityPlain(0))
        assertEquals("1", formatQuantityPlain(1_000))
        assertEquals("0.25", formatQuantityPlain(250))
        assertEquals("1.005", formatQuantityPlain(1_005))
        assertEquals("-2", formatQuantityPlain(-2_000))
    }

    @Test
    fun quantityPlainCanBeParsedBack() {
        for (q in listOf(0L, 250L, 1_005L, 12_500L)) {
            assertEquals(q, parseQuantity(formatQuantityPlain(q)))
        }
    }

    // ---- formatSignedQuantity ----

    @Test
    fun signedQuantity() {
        assertEquals("+২", formatSignedQuantity(2_000))
        assertEquals("+০.২৫", formatSignedQuantity(250))
        assertEquals("-২", formatSignedQuantity(-2_000))
        assertEquals("০", formatSignedQuantity(0))
    }

    // ---- formatDate (Dhaka time, Bengali digits) ----

    @Test
    fun dateUsesDhakaDay() {
        // 19:00 UTC on 9 Nov is already 01:00 on 10 Nov in Dhaka.
        val millis = Instant.parse("2026-11-09T19:00:00Z").toEpochMilli()
        assertEquals("১০/১১/২০২৬", formatDate(millis))
    }

    @Test
    fun dateJustBeforeDhakaMidnight() {
        // 17:59 UTC = 23:59 in Dhaka, still 9 Nov.
        val millis = Instant.parse("2026-11-09T17:59:00Z").toEpochMilli()
        assertEquals("০৯/১১/২০২৬", formatDate(millis))
    }
}

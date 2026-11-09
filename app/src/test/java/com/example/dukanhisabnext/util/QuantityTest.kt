package com.example.dukanhisabnext.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantityTest {

    @Test
    fun formatsWholeAndFractionalQuantities() {
        assertEquals("০", formatQuantity(0))
        assertEquals("১", formatQuantity(1_000))
        assertEquals("০.২৫", formatQuantity(250))
        assertEquals("১.৫", formatQuantity(1_500))
        assertEquals("১.০০৫", formatQuantity(1_005))
        assertEquals("১২", formatQuantity(12_000))
    }

    @Test
    fun formatsNegativeStock() {
        assertEquals("-২", formatQuantity(-2_000))
        assertEquals("-০.২৫", formatQuantity(-250))
    }

    @Test
    fun parsesQuantities() {
        assertEquals(2_000L, parseQuantity("2"))
        assertEquals(250L, parseQuantity("0.25"))
        assertEquals(250L, parseQuantity("০.২৫"))
        assertEquals(1_005L, parseQuantity("1.005"))
        assertEquals(500L, parseQuantity(".5"))
    }

    @Test
    fun rejectsBadQuantities() {
        assertNull(parseQuantity(""))
        assertNull(parseQuantity("1.2345")) // more than 3 decimals
        assertNull(parseQuantity("-1"))
        assertNull(parseQuantity("kg"))
    }

    @Test
    fun formatAndParseAgree() {
        assertEquals(1_250L, parseQuantity(formatQuantity(1_250)))
    }
}

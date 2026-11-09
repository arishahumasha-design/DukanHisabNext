package com.example.dukanhisabnext.util

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptNumberTest {

    private fun utc(text: String): Long = Instant.parse(text).toEpochMilli()

    @Test
    fun buildPadsToAtLeastThreeDigits() {
        assertEquals("261109-001", ReceiptNumber.build("261109", 1))
        assertEquals("261109-042", ReceiptNumber.build("261109", 42))
        assertEquals("261109-999", ReceiptNumber.build("261109", 999))
        assertEquals("261109-1000", ReceiptNumber.build("261109", 1000))
    }

    @Test
    fun firstReceiptOfTheDay() {
        assertEquals("261109-001", ReceiptNumber.next("261109", emptyList()))
    }

    @Test
    fun counterGoesUpByOne() {
        assertEquals("261109-003", ReceiptNumber.next("261109", listOf("261109-001", "261109-002")))
    }

    @Test
    fun counterGoesFrom999To1000AndBeyond() {
        val upTo999 = (1..999).map { ReceiptNumber.build("261109", it) }
        assertEquals("261109-1000", ReceiptNumber.next("261109", upTo999))

        // The trap: as TEXT, "261109-999" sorts after "261109-1000".
        val upTo1000 = upTo999 + "261109-1000"
        assertEquals("261109-999", upTo1000.max()) // text sorting would pick 999 ...
        // ... and would then hand out 1000 again. Counting as numbers gives 1001.
        assertEquals("261109-1001", ReceiptNumber.next("261109", upTo1000))
    }

    @Test
    fun orderOfTheExistingListDoesNotMatter() {
        val shuffled = listOf("261109-1000", "261109-002", "261109-999", "261109-010")
        assertEquals("261109-1001", ReceiptNumber.next("261109", shuffled))
    }

    @Test
    fun otherDaysAndBadNumbersAreIgnored() {
        val existing = listOf(
            "261108-050",   // yesterday
            "261109-abc",   // not a number
            "261109-",      // no counter
            "X261109-500",  // wrong start
            "261109-007"
        )
        assertEquals("261109-008", ReceiptNumber.next("261109", existing))
    }

    @Test
    fun aMovedBackClockContinuesThatDaysCounter() {
        // The phone's clock was set back to a day that already has receipts:
        // the counter continues from the highest one, so nothing is reused.
        val existing = listOf("261105-001", "261105-002", "261109-001")
        assertEquals("261105-003", ReceiptNumber.next("261105", existing))
    }

    @Test
    fun counterOfReadsTheNumber() {
        assertEquals(12, ReceiptNumber.counterOf("261109-0012", "261109"))
        assertEquals(1000, ReceiptNumber.counterOf("261109-1000", "261109"))
        assertNull(ReceiptNumber.counterOf("261108-001", "261109"))
        assertNull(ReceiptNumber.counterOf("261109-1a", "261109"))
    }

    @Test
    fun datePrefixUsesTheDhakaDay() {
        // 19:00 UTC on 9 Nov is 01:00 on 10 Nov in Dhaka.
        assertEquals("261110", ReceiptNumber.datePrefix(utc("2026-11-09T19:00:00Z")))
        // 17:59 UTC = 23:59 in Dhaka, still 9 Nov.
        assertEquals("261109", ReceiptNumber.datePrefix(utc("2026-11-09T17:59:00Z")))
    }
}

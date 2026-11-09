package com.example.dukanhisabnext.util

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DhakaTimeTest {

    // Helper: "2026-11-08T18:00:00Z" -> epoch millis. The "Z" means UTC.
    private fun utc(text: String): Long = Instant.parse(text).toEpochMilli()

    @Test
    fun dhakaIsSixHoursAheadOfUtc() {
        // Midnight 9 Nov in Dhaka = 18:00 on 8 Nov in UTC.
        assertEquals(utc("2026-11-08T18:00:00Z"), DhakaTime.startOfDay(LocalDate.of(2026, 11, 9)))
    }

    @Test
    fun saleAt1amDhakaCountsForTheNewDhakaDay() {
        // 01:00 on 10 Nov in Dhaka is still 9 Nov in UTC.
        val oneAmDhaka = utc("2026-11-09T19:00:00Z")
        assertEquals(LocalDate.of(2026, 11, 10), DhakaTime.localDate(oneAmDhaka))
    }

    @Test
    fun saleAt11pmDhakaIsInsideToday() {
        val noonDhaka = utc("2026-11-09T06:00:00Z")
        val elevenPmDhaka = utc("2026-11-09T17:00:00Z")
        val today = DhakaTime.today(nowMillis = noonDhaka)

        assertTrue(elevenPmDhaka >= today.startMillis && elevenPmDhaka < today.endMillisExclusive)
    }

    @Test
    fun todayIsExactlyOneDhakaDay() {
        val today = DhakaTime.today(nowMillis = utc("2026-11-09T06:00:00Z"))
        assertEquals(utc("2026-11-08T18:00:00Z"), today.startMillis)
        assertEquals(utc("2026-11-09T18:00:00Z"), today.endMillisExclusive)
    }

    @Test
    fun thisMonth() {
        val month = DhakaTime.thisMonth(nowMillis = utc("2026-11-09T06:00:00Z"))
        assertEquals(utc("2026-10-31T18:00:00Z"), month.startMillis)       // 1 Nov, Dhaka midnight
        assertEquals(utc("2026-11-30T18:00:00Z"), month.endMillisExclusive) // 1 Dec, Dhaka midnight
    }

    @Test
    fun thisYear() {
        val year = DhakaTime.thisYear(nowMillis = utc("2026-11-09T06:00:00Z"))
        assertEquals(utc("2025-12-31T18:00:00Z"), year.startMillis)
        assertEquals(utc("2026-12-31T18:00:00Z"), year.endMillisExclusive)
    }

    @Test
    fun customRangeIncludesTheWholeLastDay() {
        val range = DhakaTime.customRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 9))
        assertEquals(utc("2026-10-31T18:00:00Z"), range.startMillis)
        assertEquals(utc("2026-11-09T18:00:00Z"), range.endMillisExclusive)
    }
}

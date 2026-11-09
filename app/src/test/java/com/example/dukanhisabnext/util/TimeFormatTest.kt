package com.example.dukanhisabnext.util

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    private fun utc(text: String): Long = Instant.parse(text).toEpochMilli()

    @Test
    fun twelveHourTimeInDhakaWithBengaliDigits() {
        assertEquals("১১:৪৫", formatTime12(utc("2026-11-09T17:45:00Z"))) // 23:45 Dhaka
        assertEquals("১২:০৫", formatTime12(utc("2026-11-09T06:05:00Z"))) // 12:05 Dhaka (noon)
        assertEquals("১২:৩০", formatTime12(utc("2026-11-08T18:30:00Z"))) // 00:30 Dhaka (midnight)
        assertEquals("৯:০৭", formatTime12(utc("2026-11-09T03:07:00Z")))  // 09:07 Dhaka
    }

    @Test
    fun dayPeriodsByHour() {
        assertEquals(DayPeriod.NIGHT, dayPeriodOfHour(0))
        assertEquals(DayPeriod.NIGHT, dayPeriodOfHour(3))
        assertEquals(DayPeriod.MORNING, dayPeriodOfHour(4))
        assertEquals(DayPeriod.MORNING, dayPeriodOfHour(11))
        assertEquals(DayPeriod.NOON, dayPeriodOfHour(12))
        assertEquals(DayPeriod.NOON, dayPeriodOfHour(14))
        assertEquals(DayPeriod.AFTERNOON, dayPeriodOfHour(15))
        assertEquals(DayPeriod.AFTERNOON, dayPeriodOfHour(17))
        assertEquals(DayPeriod.EVENING, dayPeriodOfHour(18))
        assertEquals(DayPeriod.EVENING, dayPeriodOfHour(19))
        assertEquals(DayPeriod.NIGHT, dayPeriodOfHour(20))
        assertEquals(DayPeriod.NIGHT, dayPeriodOfHour(23))
    }

    @Test
    fun dayPeriodUsesDhakaTime() {
        assertEquals(DayPeriod.NIGHT, dayPeriod(utc("2026-11-09T17:45:00Z")))   // 23:45 Dhaka
        assertEquals(DayPeriod.MORNING, dayPeriod(utc("2026-11-09T03:07:00Z"))) // 09:07 Dhaka
        assertEquals(DayPeriod.NOON, dayPeriod(utc("2026-11-09T06:05:00Z")))    // 12:05 Dhaka
    }
}

package com.example.dukanhisabnext.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Timestamps are stored as UTC epoch millis, but "today", "this month" and
 * "this year" must follow the clock on the shop wall (Asia/Dhaka, UTC+6).
 * Example: a sale at 1 am in Dhaka is still the previous day in UTC, but it
 * must count for the new Dhaka day. See SPEC.md principle 8.
 */
object DhakaTime {
    val ZONE: ZoneId = ZoneId.of("Asia/Dhaka")

    /**
     * A time window for database queries: start is included, end is NOT.
     * Use it in SQL as `date >= :start AND date < :end`.
     */
    data class TimeRange(val startMillis: Long, val endMillisExclusive: Long)

    /** Which Dhaka calendar day a timestamp falls on. */
    fun localDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZONE).toLocalDate()

    /** Midnight at the start of [date] in Dhaka, as UTC millis. */
    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(ZONE).toInstant().toEpochMilli()

    fun today(nowMillis: Long = System.currentTimeMillis()): TimeRange {
        val day = localDate(nowMillis)
        return TimeRange(startOfDay(day), startOfDay(day.plusDays(1)))
    }

    fun thisMonth(nowMillis: Long = System.currentTimeMillis()): TimeRange {
        val firstDay = localDate(nowMillis).withDayOfMonth(1)
        return TimeRange(startOfDay(firstDay), startOfDay(firstDay.plusMonths(1)))
    }

    fun thisYear(nowMillis: Long = System.currentTimeMillis()): TimeRange {
        val firstDay = localDate(nowMillis).withDayOfYear(1)
        return TimeRange(startOfDay(firstDay), startOfDay(firstDay.plusYears(1)))
    }

    /** From the start of [from] up to the END of [to] (both days included). */
    fun customRange(from: LocalDate, to: LocalDate): TimeRange =
        TimeRange(startOfDay(from), startOfDay(to.plusDays(1)))
}

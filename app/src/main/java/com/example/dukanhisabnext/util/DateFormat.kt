package com.example.dukanhisabnext.util

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
private val CLOCK_12_HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH)

/** UTC millis -> Bengali date in Dhaka time, for example "১০/১১/২০২৬". */
fun formatDate(epochMillis: Long): String =
    toBengaliDigits(DAY_MONTH_YEAR.format(DhakaTime.localDate(epochMillis)))

/**
 * UTC millis -> Bengali 12-hour clock time in Dhaka, for example "১১:৪৫".
 * Use it together with [dayPeriod] ("রাত ১১:৪৫"); the period word comes from strings.xml.
 */
fun formatTime12(epochMillis: Long): String =
    toBengaliDigits(CLOCK_12_HOUR.format(Instant.ofEpochMilli(epochMillis).atZone(DhakaTime.ZONE)))

/** The part of the day, as people in Bangladesh say it (সকাল, দুপুর, বিকাল, সন্ধ্যা, রাত). */
enum class DayPeriod { MORNING, NOON, AFTERNOON, EVENING, NIGHT }

/** Which part of the day an hour (0-23) belongs to. */
fun dayPeriodOfHour(hour: Int): DayPeriod = when (hour) {
    in 4..11 -> DayPeriod.MORNING    // সকাল
    in 12..14 -> DayPeriod.NOON      // দুপুর
    in 15..17 -> DayPeriod.AFTERNOON // বিকাল
    in 18..19 -> DayPeriod.EVENING   // সন্ধ্যা
    else -> DayPeriod.NIGHT          // রাত (8 pm to 3:59 am)
}

/** The part of the day for a moment, in Dhaka time. */
fun dayPeriod(epochMillis: Long): DayPeriod =
    dayPeriodOfHour(Instant.ofEpochMilli(epochMillis).atZone(DhakaTime.ZONE).hour)

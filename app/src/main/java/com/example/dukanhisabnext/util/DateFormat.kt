package com.example.dukanhisabnext.util

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
/** English writes the month as a word, so "14 Nov 2026" cannot be read as month/day. */
private val DAY_MONTH_YEAR_ENGLISH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val CLOCK_12_HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH)

/**
 * UTC millis -> the date in Dhaka time: "১০/১১/২০২৬" in Bengali, "10 Nov 2026" in English.
 */
fun formatDate(epochMillis: Long): String {
    val day = DhakaTime.localDate(epochMillis)
    return if (CurrentLanguage.value.usesBengaliDigits) localDigits(DAY_MONTH_YEAR.format(day)) else DAY_MONTH_YEAR_ENGLISH.format(day)
}

/**
 * UTC millis -> Bengali 12-hour clock time in Dhaka, for example "১১:৪৫".
 * Use it together with [dayPeriod] ("রাত ১১:৪৫"); the period word comes from strings.xml.
 */
fun formatTime12(epochMillis: Long): String =
    localDigits(CLOCK_12_HOUR.format(Instant.ofEpochMilli(epochMillis).atZone(DhakaTime.ZONE)))

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

/**
 * The time of day as shown next to a date. Bengali: the part of the day first, "রাত ৮:১৭". English:
 * the clock with am or pm after it, "8:17 pm". [periodWord] gives the word of a part of the day from
 * strings.xml; English only uses the words of MORNING (before noon: "am") and NOON (from noon: "pm").
 */
fun clockText(epochMillis: Long, periodWord: (DayPeriod) -> String): String {
    val time = formatTime12(epochMillis)
    if (CurrentLanguage.value.usesBengaliDigits) return "${periodWord(dayPeriod(epochMillis))} $time"
    val hour = Instant.ofEpochMilli(epochMillis).atZone(DhakaTime.ZONE).hour
    return "$time ${periodWord(if (hour < 12) DayPeriod.MORNING else DayPeriod.NOON)}"
}

package com.example.dukanhisabnext.ui.components

import com.example.dukanhisabnext.ledger.dateToPickerMillis
import com.example.dukanhisabnext.ledger.dayText
import com.example.dukanhisabnext.ledger.isSelectableDay
import com.example.dukanhisabnext.ledger.pickerMillisToDate
import java.time.LocalDate

// The rules of the two-step "তারিখ বাছুন" date picker, as plain Kotlin (no Android) so they can be
// unit-tested on the PC. They were written for the Khata tab and are now shared with the expense
// list. The Material date picker works in UTC midnights (see dateToPickerMillis).

/**
 * The text on the "তারিখ বাছুন" chip once a range is chosen: "০১/১১/২০২৬ – ১২/১১/২০২৬", or one date
 * when both days are the same. null while no range is chosen (the chip then says "তারিখ বাছুন").
 */
fun customRangeText(from: LocalDate?, to: LocalDate?): String? {
    if (from == null || to == null) return null
    return if (from == to) dayText(from) else dayText(from) + " – " + dayText(to)
}

/**
 * The long text on the chip of the sales list: "১২ নভেম্বর ২০২৬" for one day, "১ নভেম্বর – ১২ নভেম্বর
 * ২০২৬" for a range (the year is written once when both days are in the same year, else on both).
 * null while no range is chosen (the chip then says "তারিখ বাছুন"). [monthNames] are the 12 Bengali
 * month names, January first.
 */
fun customRangeLongText(from: LocalDate?, to: LocalDate?, monthNames: List<String>): String? {
    if (from == null || to == null) return null
    fun full(d: LocalDate) = "${com.example.dukanhisabnext.util.toBengaliDigits(d.dayOfMonth.toString())} ${monthNames[d.monthValue - 1]} ${com.example.dukanhisabnext.util.toBengaliDigits(d.year.toString())}"
    fun noYear(d: LocalDate) = "${com.example.dukanhisabnext.util.toBengaliDigits(d.dayOfMonth.toString())} ${monthNames[d.monthValue - 1]}"
    return when {
        from == to -> full(from)
        from.year == to.year -> noYear(from) + " – " + full(to)
        else -> full(from) + " – " + full(to)
    }
}

/** The first picker ("from"): any day up to today. */
fun isSelectableFromDay(pickerMillis: Long, today: LocalDate): Boolean = isSelectableDay(pickerMillis, today)

/** The second picker ("to"): from the "from" day up to today (not before "from", not in the future). */
fun isSelectableToDay(pickerMillis: Long, from: LocalDate, today: LocalDate): Boolean {
    val day = pickerMillisToDate(pickerMillis)
    return !day.isBefore(from) && !day.isAfter(today)
}

fun pickerStartFor(date: LocalDate): Long = dateToPickerMillis(date)

/** The day the first picker opens on: the chosen "from" day, or today. */
fun fromPickerInitial(chosenFrom: LocalDate?, today: LocalDate): LocalDate = chosenFrom ?: today

/**
 * The day the second picker opens on: the chosen "to" day, or the "from" day just picked; never a
 * day before "from".
 */
fun toPickerInitial(from: LocalDate, chosenTo: LocalDate?): LocalDate =
    (chosenTo ?: from).let { if (it.isBefore(from)) from else it }

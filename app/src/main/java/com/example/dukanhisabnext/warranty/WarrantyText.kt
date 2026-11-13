package com.example.dukanhisabnext.warranty

import com.example.dukanhisabnext.data.entity.WarrantyUnit
import com.example.dukanhisabnext.util.toBengaliDigits
import java.time.LocalDate

// The words of a warranty, as plain Kotlin (no Android) so they can be unit-tested on the PC. The
// unit names and the month names come from strings.xml; the tests pass their own.

/** "৬ মাস": the number in Bengali digits and the unit's name. */
fun warrantyPeriodText(value: Int, unit: WarrantyUnit, unitName: (WarrantyUnit) -> String): String =
    "${toBengaliDigits(value.toString())} ${unitName(unit)}"

/** "১৩ মে ২০২৭": a Dhaka calendar day. [monthNames] are the 12 Bengali month names, January first. */
fun warrantyDayText(day: LocalDate, monthNames: List<String>): String =
    "${toBengaliDigits(day.dayOfMonth.toString())} ${monthNames[day.monthValue - 1]} ${toBengaliDigits(day.year.toString())}"

/** [warrantyDayText] of a stored epoch day (sale_items.warrantyUntil). */
fun warrantyUntilDayText(untilEpochDay: Long, monthNames: List<String>): String =
    warrantyDayText(LocalDate.ofEpochDay(untilEpochDay), monthNames)

/**
 * The live preview in the cart's warranty dialog: the last covered day as "১৩ মে ২০২৭" for a warranty
 * of [value] [unit] starting on [saleDay], or null while the number is empty or out of range.
 */
fun warrantyPreviewDay(saleDay: LocalDate, value: Int?, unit: WarrantyUnit?, monthNames: List<String>): String? {
    if (value == null || unit == null || checkWarranty(value, unit) != null) return null
    return warrantyDayText(warrantyUntil(saleDay, value, unit), monthNames)
}

/**
 * The number typed in a warranty box as a whole number: Bengali or normal digits, at most 3 digits
 * (999 is the biggest allowed number). Letters, signs and decimals are dropped. null when empty.
 */
fun parseWarrantyInput(text: String): Int? =
    com.example.dukanhisabnext.util.toLatinDigits(text).filter { it in '0'..'9' }.take(3).toIntOrNull()

/** What the box keeps while typing: normal digits only, at most 3. */
fun sanitizeWarrantyInput(text: String): String =
    com.example.dukanhisabnext.util.toLatinDigits(text).filter { it in '0'..'9' }.take(3)

package com.example.dukanhisabnext.warranty

import com.example.dukanhisabnext.data.entity.WarrantyUnit
import java.time.LocalDate

// Warranty (version 5). A product may have a default warranty (a number and a unit); each sale line
// stores its own snapshot: the number, the unit and the last day it covers (warrantyUntil).

/** The allowed numbers per unit: 1 to 999 days, 1 to 120 months, 1 to 50 years. */
fun warrantyMax(unit: WarrantyUnit): Int = when (unit) {
    WarrantyUnit.DAY -> 999
    WarrantyUnit.MONTH -> 120
    WarrantyUnit.YEAR -> 50
}

/** What is wrong with a warranty, if anything. */
enum class WarrantyProblem {
    /** Only one of the number and the unit is set (both or neither). */
    INCOMPLETE,
    /** The number is below 1 or above the limit of its unit. */
    OUT_OF_RANGE
}

/**
 * null = fine. "No warranty" is both null. Otherwise the number must be within 1..[warrantyMax].
 * The product form, the cart and the sale save all use this one check.
 */
fun checkWarranty(value: Int?, unit: WarrantyUnit?): WarrantyProblem? = when {
    value == null && unit == null -> null
    value == null || unit == null -> WarrantyProblem.INCOMPLETE
    value < 1 || value > warrantyMax(unit) -> WarrantyProblem.OUT_OF_RANGE
    else -> null
}

/**
 * The last day a warranty covers: [start] (the sale's Dhaka date) plus the period, counted on the
 * calendar, not as 30 or 365 days. A month or year that ends on a day that does not exist moves to
 * the last day of that month: 31 Jan + 1 month = 28 Feb (29 Feb in a leap year); 29 Feb + 1 year =
 * 28 Feb. 13 Nov 2026 + 6 months = 13 May 2027 ("১৩ মে ২০২৭ পর্যন্ত": covered through that day).
 */
fun warrantyUntil(start: LocalDate, value: Int, unit: WarrantyUnit): LocalDate {
    require(checkWarranty(value, unit) == null) { "check the warranty first" }
    return when (unit) {
        WarrantyUnit.DAY -> start.plusDays(value.toLong())
        WarrantyUnit.MONTH -> start.plusMonths(value.toLong())
        WarrantyUnit.YEAR -> start.plusYears(value.toLong())
    }
}

package com.example.dukanhisabnext.sale

import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.ledger.entryDateFor
import com.example.dukanhisabnext.util.DhakaTime
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// The date a sale belongs to (version 5). A sale has two times:
//  - saleDate: the day the sale belongs to (picked in the cart, default today). Reports, lists,
//    the receipt number, the stock movements and the due entry use it.
//  - createdAt: the real moment it was saved. Never editable; used to find a sale after a crash.

/** No sale can be dated before this day (a mistyped year is refused). */
val MIN_SALE_DAY: LocalDate = LocalDate.of(2020, 1, 1)

/** A sale dated more than this many days before today asks "ঠিক আছে?" first (a screen rule). */
const val OLD_SALE_CONFIRM_DAYS = 30L

/** What is wrong with a picked sale day, if anything. */
enum class SaleDayProblem { FUTURE, BEFORE_MIN }

/** null = the day can be used. Today and any earlier day from 1 Jan 2020 are fine. */
fun checkSaleDay(day: LocalDate, today: LocalDate): SaleDayProblem? = when {
    day.isAfter(today) -> SaleDayProblem.FUTURE
    day.isBefore(MIN_SALE_DAY) -> SaleDayProblem.BEFORE_MIN
    else -> null
}

/**
 * True when the day is more than 30 days before today (31 days and older): the screen asks
 * "আপনি অনেক পুরনো তারিখ দিয়েছেন, ঠিক আছে?" before the sale is saved. Exactly 30 days does not ask.
 */
fun needsOldDateConfirm(day: LocalDate, today: LocalDate): Boolean =
    ChronoUnit.DAYS.between(day, today) > OLD_SALE_CONFIRM_DAYS

/**
 * The saleDate to store for the Dhaka day [day]: today -> right now; an earlier day -> that day at
 * the current Dhaka time of day. The same rule as a back-dated khata entry or expense (entryDateFor).
 */
fun saleDateFor(day: LocalDate, nowMillis: Long): Long = entryDateFor(day, nowMillis)

/**
 * A sale is back-dated when it was saved on a later Dhaka day than the day it belongs to. Such a
 * sale shows its date only (its time of day is the time of saving, not of selling), and the
 * receipt screen adds a small "লেখা হয়েছে <date>" line.
 */
fun isBackDated(sale: Sale): Boolean =
    DhakaTime.localDate(sale.saleDate) != DhakaTime.localDate(sale.createdAt)

/**
 * Can this Material date-picker day (UTC midnight millis) be chosen as a sale day? Not in the future,
 * and not before 1 January 2020 (the same two rules as [checkSaleDay]).
 */
fun isSelectableSaleDay(pickerMillis: Long, today: LocalDate): Boolean =
    checkSaleDay(com.example.dukanhisabnext.ledger.pickerMillisToDate(pickerMillis), today) == null

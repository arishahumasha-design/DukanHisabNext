package com.example.dukanhisabnext.ui.khata

import com.example.dukanhisabnext.data.dao.KhataCustomerRow
import com.example.dukanhisabnext.data.dao.KhataEntryRow
import com.example.dukanhisabnext.ledger.KhataPeriod
import com.example.dukanhisabnext.ledger.KhataSort
import com.example.dukanhisabnext.ledger.PeriodSummary
import com.example.dukanhisabnext.ledger.SummaryLine
import com.example.dukanhisabnext.ledger.matchesKhataSearch
import com.example.dukanhisabnext.util.DhakaTime
import java.time.LocalDate

// The logic of the "বাকির খাতা" screen and the "যোগাযোগ" address book, as plain Kotlin (no Android)
// so it can be unit-tested on the PC. The rules of the numbers are in ledger/KhataRules.kt.

// =========================================================================================
// What the screen remembers (kept in the SavedStateHandle: rotation, Back, process death)
// =========================================================================================

/** "No day picked" in the saved state (a Long is stored; a missing day is this value). */
const val NO_DAY: Long = Long.MIN_VALUE

fun periodFromName(name: String?): KhataPeriod = KhataPeriod.entries.firstOrNull { it.name == name } ?: KhataPeriod.ALL

fun sortFromName(name: String?): KhataSort = KhataSort.entries.firstOrNull { it.name == name } ?: KhataSort.entries.first()

fun dayOrNull(epochDay: Long): LocalDate? = if (epochDay == NO_DAY) null else LocalDate.ofEpochDay(epochDay)

/** Everything the user chose on the screen. */
data class KhataControls(
    val period: KhataPeriod = KhataPeriod.ALL,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val sort: KhataSort = KhataSort.entries.first(),
    val onlyOwing: Boolean = false,
    val query: String = ""
) {
    val searching: Boolean get() = query.isNotBlank()
    val isAll: Boolean get() = period == KhataPeriod.ALL
}

// =========================================================================================
// The customers (the "সব" list, the customer picker and the address book)
// =========================================================================================

/** Customers A to Z by name (ties by id), whatever their balance. */
fun customersAtoZ(rows: List<KhataCustomerRow>): List<KhataCustomerRow> =
    rows.sortedWith(compareBy<KhataCustomerRow, String>(String.CASE_INSENSITIVE_ORDER) { it.customer.name }.thenBy { it.customer.id })

/** The picker and the address book: A to Z, filtered by the search (name or mobile). */
fun searchedCustomersAtoZ(rows: List<KhataCustomerRow>, query: String): List<KhataCustomerRow> =
    customersAtoZ(rows.filter { matchesKhataSearch(it.customer.name, it.customer.phone, query) })

// =========================================================================================
// The period chips: the entries
// =========================================================================================

/** The entries that match the search (customer name or mobile). The summary never uses this. */
fun searchedEntries(rows: List<KhataEntryRow>, query: String): List<KhataEntryRow> =
    rows.filter { matchesKhataSearch(it.customerName, it.customerPhone, query) }

data class EntryDay(val date: LocalDate, val rows: List<KhataEntryRow>)

/** Groups entries (already newest first) by the Dhaka day of their entry date, keeping the order. */
fun groupEntriesByDay(rows: List<KhataEntryRow>): List<EntryDay> =
    rows.groupBy { DhakaTime.localDate(it.entry.entryDate) }.map { (date, list) -> EntryDay(date, list) }

// =========================================================================================
// The summary box of a period
// =========================================================================================

/** The value of one summary line, as the summary stores it (positive amounts; CORRECTION and NET are signed). */
fun summaryAmount(summary: PeriodSummary, line: SummaryLine): Long = when (line) {
    SummaryLine.DUE_GIVEN -> summary.dueGiven
    SummaryLine.RECEIVED -> summary.received
    SummaryLine.OPENING_DUE -> summary.openingDue
    SummaryLine.ADVANCE_RETURNED -> summary.advanceReturned
    SummaryLine.CORRECTION -> summary.correctionNet
    SummaryLine.SALE_CANCEL -> summary.saleCancelled
    SummaryLine.NET -> summary.net
}

/** Only these two lines can be negative, so only they show a + or − sign. */
fun isSignedLine(line: SummaryLine): Boolean = line == SummaryLine.CORRECTION || line == SummaryLine.NET

/**
 * "৳ ১,২০০", and for the two signed lines "+ ৳ ১,২০০" / "− ৳ ১,২০০" (nothing for 0).
 * [formatted] is the money without the taka sign (formatTaka of the absolute amount).
 */
fun summaryAmountText(line: SummaryLine, amount: Long, taka: String, formatted: String): String {
    if (!isSignedLine(line) || amount == 0L) return "$taka $formatted"
    return (if (amount > 0) "+ " else "− ") + "$taka $formatted"
}

// =========================================================================================
// Empty messages
// =========================================================================================

enum class KhataEmpty {
    NO_CUSTOMERS, // "সব" with no customer at all
    NO_MATCH,     // there is something, but the search or the filter finds nothing
    NO_ENTRIES,   // a period chip with no entry in the window
    PICK_DATES    // "তারিখ বাছুন" before a range is chosen
}

/**
 * null = there is something to show.
 * [rangeReady] is false for "তারিখ বাছুন" until both days are chosen. [windowCount] is how many
 * entries the whole window has (before the search); [shownCount] how many are shown.
 */
fun khataEmptyKind(
    period: KhataPeriod,
    rangeReady: Boolean,
    searching: Boolean,
    totalCustomers: Int,
    windowCount: Int,
    shownCount: Int
): KhataEmpty? {
    if (period == KhataPeriod.ALL) {
        return when {
            shownCount > 0 -> null
            totalCustomers == 0 -> KhataEmpty.NO_CUSTOMERS
            else -> KhataEmpty.NO_MATCH
        }
    }
    return when {
        !rangeReady -> KhataEmpty.PICK_DATES
        shownCount > 0 -> null
        searching && windowCount > 0 -> KhataEmpty.NO_MATCH
        else -> KhataEmpty.NO_ENTRIES
    }
}

/** The address book: no customer at all, or nobody matches the search. */
fun contactsEmptyKind(totalCustomers: Int, shownCount: Int): KhataEmpty? = when {
    shownCount > 0 -> null
    totalCustomers == 0 -> KhataEmpty.NO_CUSTOMERS
    else -> KhataEmpty.NO_MATCH
}

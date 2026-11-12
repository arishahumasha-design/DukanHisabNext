package com.example.dukanhisabnext.ui.sales

import com.example.dukanhisabnext.data.dao.SaleListRow
import com.example.dukanhisabnext.data.dao.SalesListSummaryRow
import com.example.dukanhisabnext.data.dao.SalesSearchParams
import com.example.dukanhisabnext.util.DayPeriod
import com.example.dukanhisabnext.util.DhakaTime
import com.example.dukanhisabnext.util.dayPeriod
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.formatTime12
import com.example.dukanhisabnext.util.toBengaliDigits
import com.example.dukanhisabnext.util.toLatinDigits
import java.time.LocalDate

// The sales list rules, as plain Kotlin (no Android) so they can be unit-tested on the PC.
// Money is Long paisa. Every day, month and time is in Asia/Dhaka (a sale made at 11 pm belongs
// to that Dhaka day, even though it is the next day in UTC).

// =========================================================================================
// Filter chips: আজ, এই মাস, সব
// =========================================================================================

enum class SalesFilter { TODAY, THIS_MONTH, ALL }

/**
 * The time window of a filter, as UTC millis (start included, end excluded), using the Dhaka
 * day and month boundaries around [nowMillis]. ALL covers every moment.
 */
fun salesFilterRange(filter: SalesFilter, nowMillis: Long): DhakaTime.TimeRange = when (filter) {
    SalesFilter.TODAY -> DhakaTime.today(nowMillis)
    SalesFilter.THIS_MONTH -> DhakaTime.thisMonth(nowMillis)
    SalesFilter.ALL -> DhakaTime.TimeRange(0L, Long.MAX_VALUE)
}

/** True while the search box has text (spaces alone do not count). */
fun isSearching(query: String): Boolean = query.isNotBlank()

/**
 * The filter that really decides which sales are loaded. While the search box has text, ALL sales
 * are searched whatever chip is chosen (the chip does not apply). When the box is cleared, the
 * chosen chip applies again (it was never forgotten).
 */
fun effectiveSalesFilter(chip: SalesFilter, query: String): SalesFilter =
    if (isSearching(query)) SalesFilter.ALL else chip

// =========================================================================================
// Search: receipt number, customer name or customer mobile
// =========================================================================================

private fun compact(text: String): String = text.filter { it != '-' && it != ' ' }

/**
 * Does a sale match the typed [query]? A blank query matches everything. Bengali digits are
 * accepted (they are turned into normal digits first), exactly like the customer search.
 *  - receipt number: partial, with or without the dash ("261110", "1110-0", "261110001");
 *  - customer name: partial, upper or lower case ("করিম", "rah");
 *  - customer mobile: partial digits ("01711", "০১৭১১"), also typed with +88.
 */
fun matchesSalesQuery(row: SaleListRow, query: String): Boolean {
    val q = toLatinDigits(query).trim()
    if (q.isEmpty()) return true

    // Receipt number
    val compactQuery = compact(q)
    if (compactQuery.isNotEmpty() && compact(row.receiptNo).contains(compactQuery, ignoreCase = true)) return true

    // Customer name
    if (row.customerName?.contains(q, ignoreCase = true) == true) return true

    // Customer mobile (digits only; a leading 88 from +88 is allowed)
    val phone = row.customerPhone
    if (phone != null) {
        val digits = q.filter { it in '0'..'9' }
        if (digits.isNotEmpty()) {
            if (phone.contains(digits)) return true
            if (digits.startsWith("88") && digits.length > 2 && phone.contains(digits.removePrefix("88"))) return true
        }
    }
    return false
}

fun filterSales(rows: List<SaleListRow>, query: String): List<SaleListRow> =
    rows.filter { matchesSalesQuery(it, query) }

/** Makes %, _ and \ plain characters in a SQL LIKE pattern (used with ESCAPE '\'). */
fun likeEscape(text: String): String = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/**
 * The typed search as the four SQL parameters of SALES_SEARCH_SQL (SaleDao.kt), following exactly
 * the rules of [matchesSalesQuery]: Bengali digits allowed, receipt number with or without the
 * dash, partial name, partial mobile digits, also typed with +88. A blank query searches nothing.
 */
fun salesSearchParams(rawQuery: String): SalesSearchParams {
    val q = toLatinDigits(rawQuery).trim()
    if (q.isEmpty()) return SalesSearchParams("", "", "", "")
    val digits = q.filter { it in '0'..'9' }
    return SalesSearchParams(
        query = likeEscape(q),
        compactQuery = likeEscape(compact(q)),
        digits = digits,
        digitsWithout88 = if (digits.startsWith("88") && digits.length > 2) digits.removePrefix("88") else ""
    )
}

// =========================================================================================
// The capped list ("আরও দেখান")
// =========================================================================================

/** The sales list shows the newest this many rows; each "আরও দেখান" adds this many more. */
const val SALES_PAGE_SIZE = 300

/** The database summary row as the screen's [SalesSummary]. */
fun SalesListSummaryRow.toSalesSummary(): SalesSummary = SalesSummary(count, total, cancelledExcluded)

/**
 * The screen from ONE page of rows (already searched and capped by the database) and the summary of
 * the WHOLE window and search ([summary], from the aggregate query, never from [rows]).
 * "আরও দেখান" shows when the window has more sales than the rows loaded.
 */
fun buildSalesPageUi(rows: List<SaleListRow>, summary: SalesSummary, totalSalesInDb: Int): SalesListUi {
    val matching = summary.count + summary.cancelledExcluded
    val items = groupByDhakaDay(rows).flatMap { group ->
        listOf<SalesListItem>(SalesListItem.Header(group.date)) + group.rows.map { SalesListItem.Entry(it) }
    }
    return SalesListUi(
        items = items,
        summary = summary,
        empty = emptyKind(totalSalesInDb, matching),
        hasMore = matching > rows.size
    )
}

// =========================================================================================
// The line above the list: how many sales and their total
// =========================================================================================

/**
 * [count] and [total] only count sales that are NOT cancelled; [cancelledExcluded] is how many
 * cancelled sales were left out (they stay visible in the list, tagged, but are not money).
 */
data class SalesSummary(val count: Int, val total: Long, val cancelledExcluded: Int = 0)

/** The count and the total (sum of each sale's total, in paisa) of the sales shown, cancelled ones left out. */
fun salesSummary(rows: List<SaleListRow>): SalesSummary {
    val active = rows.filterNot { it.isCancelled }
    return SalesSummary(count = active.size, total = active.sumOf { it.total }, cancelledExcluded = rows.size - active.size)
}

/**
 * The line above the list: "N টি বিক্রি · মোট ৳ X", and " (বাতিল N টি বাদ)" after it only when some
 * cancelled sales were left out. [summaryText] and [excludedText] fill the two strings.xml sentences.
 */
fun salesSummaryLine(
    summary: SalesSummary,
    summaryText: (count: String, total: String) -> String,
    excludedText: (count: String) -> String
): String {
    val main = summaryText(toBengaliDigits(summary.count.toString()), formatTaka(summary.total))
    return if (summary.cancelledExcluded > 0) {
        main + " " + excludedText(toBengaliDigits(summary.cancelledExcluded.toString()))
    } else {
        main
    }
}

/** The small grey "বাতিল" tag is shown on cancelled rows; the red due tag is not (a cancelled sale owes nothing). */
fun showCancelledTag(row: SaleListRow): Boolean = row.isCancelled
fun showDueTag(row: SaleListRow): Boolean = !row.isCancelled && row.dueAmount > 0

// =========================================================================================
// Grouping by Dhaka day
// =========================================================================================

data class SalesDayGroup(val date: LocalDate, val rows: List<SaleListRow>)

/**
 * Groups sales by their Asia/Dhaka calendar day, newest day first and newest sale first inside a
 * day. A sale at 23:30 and one at 00:10 land on different days.
 */
fun groupByDhakaDay(rows: List<SaleListRow>): List<SalesDayGroup> =
    rows
        .sortedWith(compareByDescending<SaleListRow> { it.createdAt }.thenByDescending { it.id })
        .groupBy { DhakaTime.localDate(it.createdAt) } // a LinkedHashMap: keeps the newest day first
        .map { (date, dayRows) -> SalesDayGroup(date, dayRows) }

/** "১০ নভেম্বর ২০২৬": the day header. [monthNames] are the 12 Bengali month names, January first. */
fun dayHeaderText(date: LocalDate, monthNames: List<String>): String =
    "${toBengaliDigits(date.dayOfMonth.toString())} ${monthNames[date.monthValue - 1]} " +
        toBengaliDigits(date.year.toString())

/** "রাত ১১:৩০": the Dhaka time of a sale, with the part of the day. */
fun saleTimeText(createdAt: Long, period: (DayPeriod) -> String): String =
    "${period(dayPeriod(createdAt))} ${formatTime12(createdAt)}"

/** The customer's name, or the neutral word (সাধারণ কাস্টমার) when the sale has no customer. */
fun customerLabel(name: String?, walkIn: String): String = name?.trim()?.ifEmpty { null } ?: walkIn

// =========================================================================================
// Everything the screen shows, worked out in one place
// =========================================================================================

/** The list is flat (headers and sales in one lazy list). Each item has a stable key. */
sealed interface SalesListItem {
    val key: String

    data class Header(val date: LocalDate) : SalesListItem {
        override val key: String get() = "day-${date.toEpochDay()}"
    }

    data class Entry(val row: SaleListRow) : SalesListItem {
        override val key: String get() = "sale-${row.id}"
    }
}

/** Which empty message to show. */
enum class EmptyKind {
    NO_SALES_AT_ALL, // no sale has ever been saved
    NO_MATCH         // there are sales, but none for this search or filter
}

/** null when there is something to show. */
fun emptyKind(totalSalesInDb: Int, visibleCount: Int): EmptyKind? = when {
    visibleCount > 0 -> null
    totalSalesInDb == 0 -> EmptyKind.NO_SALES_AT_ALL
    else -> EmptyKind.NO_MATCH
}

data class SalesListUi(
    val items: List<SalesListItem>,
    val summary: SalesSummary,
    val empty: EmptyKind?,
    /** More sales than the rows shown: the "আরও দেখান" button. */
    val hasMore: Boolean = false
)

/**
 * [rows] are the sales of the chosen filter (already cut by the database). The search is applied
 * here, then the rest is grouped by Dhaka day and totalled. [totalSalesInDb] is every sale that exists.
 */
fun buildSalesListUi(rows: List<SaleListRow>, query: String, totalSalesInDb: Int): SalesListUi {
    val visible = filterSales(rows, query)
    val items = groupByDhakaDay(visible).flatMap { group ->
        listOf<SalesListItem>(SalesListItem.Header(group.date)) + group.rows.map { SalesListItem.Entry(it) }
    }
    return SalesListUi(items, salesSummary(visible), emptyKind(totalSalesInDb, visible.size))
}

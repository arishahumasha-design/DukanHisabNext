package com.example.dukanhisabnext.ledger

import com.example.dukanhisabnext.data.dao.KhataCustomerRow
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.ui.sales.dayHeaderText
import com.example.dukanhisabnext.util.DhakaTime
import com.example.dukanhisabnext.util.toLatinDigits
import java.time.LocalDate

// The rules of the "বাকির খাতা" tab (the time chips, the "সব" list, its sort and summary, and the
// period summary), as plain Kotlin (no Android) so they can be unit-tested on the PC.
// Money is Long paisa. Every day, week, month and year is an Asia/Dhaka one.

// =========================================================================================
// The time chips: সব | আজ | ৭ দিন | এই মাস | এই বছর | তারিখ বাছুন
// =========================================================================================

enum class KhataPeriod { ALL, TODAY, LAST_7_DAYS, THIS_MONTH, THIS_YEAR, CUSTOM }

/**
 * The time window of a chip, as UTC millis (start included, end excluded), using Dhaka days:
 *  - আজ: today;
 *  - ৭ দিন: today plus the 6 Dhaka days before it;
 *  - এই মাস / এই বছর: the Dhaka month / year of today;
 *  - তারিখ বাছুন: from the start of [customFrom] to the end of [customTo] (see [customDays]).
 * null for সব (it shows customers, not entries) and for তারিখ বাছুন before both days are picked.
 */
fun periodRange(
    period: KhataPeriod,
    nowMillis: Long,
    customFrom: LocalDate? = null,
    customTo: LocalDate? = null
): DhakaTime.TimeRange? {
    val today = DhakaTime.localDate(nowMillis)
    return when (period) {
        KhataPeriod.ALL -> null
        KhataPeriod.TODAY -> DhakaTime.today(nowMillis)
        KhataPeriod.LAST_7_DAYS -> DhakaTime.customRange(today.minusDays(6), today)
        KhataPeriod.THIS_MONTH -> DhakaTime.thisMonth(nowMillis)
        KhataPeriod.THIS_YEAR -> DhakaTime.thisYear(nowMillis)
        KhataPeriod.CUSTOM -> customDays(customFrom, customTo, today)?.let { (from, to) -> DhakaTime.customRange(from, to) }
    }
}

/**
 * The two picked days, made safe: a day after today becomes today (no future), and if "from" is
 * after "to" they are swapped. null until both days are picked.
 */
fun customDays(from: LocalDate?, to: LocalDate?, today: LocalDate): Pair<LocalDate, LocalDate>? {
    if (from == null || to == null) return null
    val a = minOf(from, today)
    val b = minOf(to, today)
    return if (a.isAfter(b)) b to a else a to b
}

// =========================================================================================
// Search: customer name or mobile (works in every chip)
// =========================================================================================

/**
 * Does a customer match the typed [query]? Blank matches everyone. Bengali digits are accepted.
 *  - name: partial, upper or lower case;
 *  - mobile: partial digits, also typed with +88 or with spaces and dashes.
 */
fun matchesKhataSearch(name: String, phone: String?, query: String): Boolean {
    val q = toLatinDigits(query).trim()
    if (q.isEmpty()) return true
    if (name.contains(q, ignoreCase = true)) return true
    if (phone != null) {
        val digits = q.filter { it in '0'..'9' }
        if (digits.isNotEmpty()) {
            if (phone.contains(digits)) return true
            if (digits.startsWith("88") && digits.length > 2 && phone.contains(digits.removePrefix("88"))) return true
        }
    }
    return false
}

// =========================================================================================
// The "সব" list: filter, sort, summary, last-date text
// =========================================================================================

/** The sort dialog. The first one is the default. */
enum class KhataSort { LAST_ENTRY_NEWEST, LAST_ENTRY_OLDEST, AMOUNT_LOW_TO_HIGH, AMOUNT_HIGH_TO_LOW }

/**
 * Sorts the "সব" list. Ties are always broken by name (A to Z) and then id, so the order never
 * jumps around. For the two date sorts, customers with no ledger entry at all come last.
 * The amount is the balance: an advance is a negative amount, so it is the "lowest".
 */
fun sortKhataCustomers(rows: List<KhataCustomerRow>, sort: KhataSort): List<KhataCustomerRow> {
    val byName = compareBy<KhataCustomerRow, String>(String.CASE_INSENSITIVE_ORDER) { it.customer.name }
        .thenBy { it.customer.id }
    val comparator: Comparator<KhataCustomerRow> = when (sort) {
        KhataSort.LAST_ENTRY_NEWEST ->
            compareBy<KhataCustomerRow> { it.lastEntryDate == null }
                .thenByDescending { it.lastEntryDate }
                .then(byName)
        KhataSort.LAST_ENTRY_OLDEST ->
            compareBy<KhataCustomerRow> { it.lastEntryDate == null }
                .thenBy { it.lastEntryDate }
                .then(byName)
        KhataSort.AMOUNT_LOW_TO_HIGH -> compareBy<KhataCustomerRow> { it.balance }.then(byName)
        KhataSort.AMOUNT_HIGH_TO_LOW -> compareByDescending<KhataCustomerRow> { it.balance }.then(byName)
    }
    return rows.sortedWith(comparator)
}

/**
 * What the "সব" list shows: the search (name or mobile), then the "যাদের বাকি আছে" filter
 * (only balances above 0), then the sort.
 */
fun visibleKhataCustomers(
    rows: List<KhataCustomerRow>,
    query: String,
    onlyOwing: Boolean,
    sort: KhataSort
): List<KhataCustomerRow> = sortKhataCustomers(
    rows.filter { matchesKhataSearch(it.customer.name, it.customer.phone, query) && (!onlyOwing || it.balance > 0) },
    sort
)

/**
 * The "সব" summary box, always for the WHOLE shop (it ignores the search and the filter):
 *  - [receivable] পাবো: the sum of the balances above 0;
 *  - [advance] অগ্রিম: the money held for customers (the balances below 0, as a positive amount);
 *  - [net] = receivable - advance (= the sum of every balance).
 */
data class KhataAllSummary(val receivable: Long, val advance: Long) {
    val net: Long get() = receivable - advance
}

fun khataAllSummary(allRows: List<KhataCustomerRow>): KhataAllSummary = KhataAllSummary(
    receivable = allRows.filter { it.balance > 0 }.sumOf { it.balance },
    advance = allRows.filter { it.balance < 0 }.sumOf { -it.balance }
)

/**
 * "১১ নভেম্বর ২০২৬ পর্যন্ত": the Dhaka date of the newest ledger entry. null when the customer
 * has none. [monthNames] are the 12 Bengali month names; [template] adds the word "পর্যন্ত".
 */
fun lastEntryText(lastEntryDate: Long?, monthNames: List<String>, template: (String) -> String): String? =
    lastEntryDate?.let { template(dayHeaderText(DhakaTime.localDate(it), monthNames)) }

// =========================================================================================
// The period summary (every chip except সব)
// =========================================================================================

/**
 * The line of the period summary each entry belongs to. Every entry falls into exactly one line
 * (the `when` in [periodLineOf] lists every kind, with no `else`).
 */
enum class PeriodLine {
    DUE_ON_SALES,     // বাকি দিলাম: বিক্রিতে (SALE_DUE)
    DUE_BY_HAND,      // বাকি দিলাম: হাতে (MANUAL_DUE)
    OPENING_DUE,      // পুরনো বাকি (OPENING_DUE)
    RECEIVED,         // টাকা পেলাম (PAYMENT_RECEIVED)
    ADVANCE_RETURNED, // অগ্রিম ফেরত দিলাম (ADVANCE_RETURNED)
    CORRECTION,       // সংশোধন (নিট): CORRECTION_PLUS and CORRECTION_MINUS without a sale
    SALE_CANCEL       // বিক্রি বাতিল: CORRECTION_MINUS linked to a sale
}

fun periodLineOf(entry: LedgerEntry): PeriodLine = when (historyKind(entry)) {
    HistoryKind.SALE_DUE -> PeriodLine.DUE_ON_SALES
    HistoryKind.MANUAL_DUE -> PeriodLine.DUE_BY_HAND
    HistoryKind.OPENING_DUE -> PeriodLine.OPENING_DUE
    HistoryKind.PAYMENT_RECEIVED -> PeriodLine.RECEIVED
    HistoryKind.ADVANCE_RETURNED -> PeriodLine.ADVANCE_RETURNED
    HistoryKind.CORRECTION_PLUS, HistoryKind.CORRECTION_MINUS -> PeriodLine.CORRECTION
    HistoryKind.SALE_CANCEL_REVERSAL -> PeriodLine.SALE_CANCEL
}

/**
 * The numbers of a period. Each value is the sum of signedAmount() of the entries in that line,
 * so there is no second formula. All lines except [correctionNet] always have one sign, and they
 * are stored here as POSITIVE amounts (the screen says the direction in words):
 *   net = dueOnSales + dueByHand + openingDue - received + advanceReturned + correctionNet - saleCancelled
 * [net] itself is the plain sum of signedAmount() of every entry: what the customers' total
 * balance changed by in this period.
 */
data class PeriodSummary(
    val dueOnSales: Long,
    val dueByHand: Long,
    val openingDue: Long,
    val received: Long,
    val advanceReturned: Long,
    val correctionNet: Long, // + when the corrections added to the dues, - when they took away
    val saleCancelled: Long,
    val net: Long
) {
    /** বাকি দিলাম = বিক্রিতে + হাতে. */
    val dueGiven: Long get() = dueOnSales + dueByHand

    /** The lines put together with their signs. Always equal to [net] (tested). */
    val linesTotal: Long
        get() = dueOnSales + dueByHand + openingDue - received + advanceReturned + correctionNet - saleCancelled
}

fun periodSummary(entries: List<LedgerEntry>): PeriodSummary {
    val byLine: Map<PeriodLine, Long> = entries
        .groupBy { periodLineOf(it) }
        .mapValues { (_, list) -> list.sumOf { signedAmount(it) } }
    fun line(l: PeriodLine) = byLine[l] ?: 0L
    return PeriodSummary(
        dueOnSales = line(PeriodLine.DUE_ON_SALES),
        dueByHand = line(PeriodLine.DUE_BY_HAND),
        openingDue = line(PeriodLine.OPENING_DUE),
        received = -line(PeriodLine.RECEIVED),            // signedAmount is negative: shown positive
        advanceReturned = line(PeriodLine.ADVANCE_RETURNED),
        correctionNet = line(PeriodLine.CORRECTION),
        saleCancelled = -line(PeriodLine.SALE_CANCEL),    // signedAmount is negative: shown positive
        net = entries.sumOf { signedAmount(it) }
    )
}

/** The lines the summary box shows, in this order. */
enum class SummaryLine { DUE_GIVEN, RECEIVED, OPENING_DUE, ADVANCE_RETURNED, CORRECTION, SALE_CANCEL, NET }

/**
 * "বাকি দিলাম" and "টাকা পেলাম" are always shown; the other lines only when they are not 0, so
 * the box stays short but the numbers stay honest (the lines always add up to the net).
 */
fun shownSummaryLines(summary: PeriodSummary): List<SummaryLine> = buildList {
    add(SummaryLine.DUE_GIVEN)
    add(SummaryLine.RECEIVED)
    if (summary.openingDue != 0L) add(SummaryLine.OPENING_DUE)
    if (summary.advanceReturned != 0L) add(SummaryLine.ADVANCE_RETURNED)
    if (summary.correctionNet != 0L) add(SummaryLine.CORRECTION)
    if (summary.saleCancelled != 0L) add(SummaryLine.SALE_CANCEL)
    add(SummaryLine.NET)
}

/** The small line under "বাকি দিলাম": "বিক্রিতে ৳X · হাতে ৳Y", shown only when some due was given. */
fun showsDueBreakdown(summary: PeriodSummary): Boolean = summary.dueGiven != 0L

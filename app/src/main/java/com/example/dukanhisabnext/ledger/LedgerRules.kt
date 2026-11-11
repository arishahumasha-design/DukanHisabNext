package com.example.dukanhisabnext.ledger

import com.example.dukanhisabnext.data.dao.LedgerHistoryRow
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.util.DhakaTime
import java.time.Instant
import java.time.LocalDate

// The rules of the baki khata (due ledger), as plain Kotlin (no Android) so they can be
// unit-tested on the PC. LedgerRepository runs them inside one database transaction.
// Money is Long paisa and always POSITIVE in an entry: the type says if it adds or takes away.
// Ledger entries are never edited or deleted; a mistake is fixed with a correction.

/** ৳1 crore = 1,00,00,000 taka = 1,000,000,000 paisa: the most one entry may be (catches typing mistakes). */
const val MAX_LEDGER_AMOUNT: Long = 1_000_000_000L

/** The most characters a note may have (an emoji counts as one), the same as a cancel reason. */
const val MAX_LEDGER_NOTE_LENGTH = 200

/** The payment methods offered on screen (BANK and OTHER exist in the database only). */
val LEDGER_METHODS: List<PaymentMethod> = listOf(PaymentMethod.CASH, PaymentMethod.BKASH, PaymentMethod.NAGAD)

// =========================================================================================
// The balance formula (the Kotlin twin of BALANCE_SUM_SQL in LedgerDao.kt)
// =========================================================================================

/**
 * How much one entry changes what the customer owes: + adds to the due, - takes away.
 * Every type must be listed (no "else"), so a new type cannot be forgotten: it will not compile.
 */
fun signedAmount(type: LedgerEntryType, amount: Long): Long = when (type) {
    LedgerEntryType.OPENING_DUE,
    LedgerEntryType.SALE_DUE,
    LedgerEntryType.MANUAL_DUE,
    LedgerEntryType.CORRECTION_PLUS,
    LedgerEntryType.ADVANCE_RETURNED -> amount

    LedgerEntryType.PAYMENT_RECEIVED,
    LedgerEntryType.CORRECTION_MINUS -> -amount
}

fun signedAmount(entry: LedgerEntry): Long = signedAmount(entry.type, entry.amount)

/** The customer's balance from a list of entries. Positive = owes, negative = advance (অগ্রিম). */
fun balanceOf(entries: List<LedgerEntry>): Long = entries.sumOf { signedAmount(it) }

// =========================================================================================
// What the shopkeeper can record
// =========================================================================================

/** The buttons on the customer detail screen, and the ledger type each one saves. */
enum class LedgerAction(val type: LedgerEntryType) {
    PAYMENT(LedgerEntryType.PAYMENT_RECEIVED),       // টাকা পেলাম
    MANUAL_DUE(LedgerEntryType.MANUAL_DUE),          // বাকি দিলাম
    OPENING_DUE(LedgerEntryType.OPENING_DUE),        // পুরনো বাকি (from the paper khata), once per customer
    CORRECTION_PLUS(LedgerEntryType.CORRECTION_PLUS),   // সংশোধন (+)
    CORRECTION_MINUS(LedgerEntryType.CORRECTION_MINUS), // সংশোধন (−)
    ADVANCE_RETURN(LedgerEntryType.ADVANCE_RETURNED);   // অগ্রিম ফেরত দিলাম

    /** Money changes hands: the method (নগদ / বিকাশ / নগদ (মোবাইল)) is asked and saved. */
    val needsMethod: Boolean get() = this == PAYMENT || this == ADVANCE_RETURN

    /** A correction must say why. */
    val needsNote: Boolean get() = this == CORRECTION_PLUS || this == CORRECTION_MINUS
}

/** Which actions to offer: the opening due only while there is none; advance return only while there is an advance. */
fun availableActions(balance: Long, hasOpeningDue: Boolean): List<LedgerAction> = LedgerAction.entries.filter {
    when (it) {
        LedgerAction.OPENING_DUE -> !hasOpeningDue
        LedgerAction.ADVANCE_RETURN -> balance < 0
        else -> true
    }
}

/** What the form sends. [amount] is paisa (null = nothing typed). [date] is the Dhaka day picked. */
data class LedgerInput(
    val action: LedgerAction,
    val amount: Long?,
    val date: LocalDate,
    val method: PaymentMethod? = null,
    val note: String? = null
)

enum class LedgerError {
    AMOUNT_MISSING,     // empty or 0
    AMOUNT_TOO_LARGE,   // above ৳1 crore
    DATE_IN_FUTURE,     // after today (Dhaka)
    METHOD_REQUIRED,    // a payment or an advance return without a method
    NOTE_REQUIRED,      // a correction without a note
    NOTE_TOO_LONG,      // more than 200 characters
    OPENING_DUE_EXISTS, // this customer already has an opening due
    NO_ADVANCE,         // an advance return, but the customer has no advance
    MORE_THAN_ADVANCE   // an advance return larger than the advance
}

/** The note as it is saved: trimmed; empty means no note. */
fun cleanNote(note: String?): String? = note?.trim()?.ifEmpty { null }

private fun lengthOf(text: String): Int = text.codePointCount(0, text.length)

/**
 * Every problem with [input] (empty = it can be saved). [today] is the Dhaka date now,
 * [currentBalance] the customer's balance before this entry, [hasOpeningDue] whether one exists.
 * Paying MORE than the due is allowed on purpose: the rest becomes an advance (অগ্রিম).
 */
fun checkLedgerInput(
    input: LedgerInput,
    today: LocalDate,
    currentBalance: Long,
    hasOpeningDue: Boolean
): Set<LedgerError> = buildSet {
    val amount = input.amount
    if (amount == null || amount <= 0) add(LedgerError.AMOUNT_MISSING)
    else if (amount > MAX_LEDGER_AMOUNT) add(LedgerError.AMOUNT_TOO_LARGE)

    if (input.date.isAfter(today)) add(LedgerError.DATE_IN_FUTURE)

    if (input.action.needsMethod && input.method == null) add(LedgerError.METHOD_REQUIRED)

    val note = cleanNote(input.note)
    if (input.action.needsNote && note == null) add(LedgerError.NOTE_REQUIRED)
    if (note != null && lengthOf(note) > MAX_LEDGER_NOTE_LENGTH) add(LedgerError.NOTE_TOO_LONG)

    if (input.action == LedgerAction.OPENING_DUE && hasOpeningDue) add(LedgerError.OPENING_DUE_EXISTS)

    if (input.action == LedgerAction.ADVANCE_RETURN) {
        val advance = -currentBalance // how much the shop holds for the customer
        when {
            advance <= 0 -> add(LedgerError.NO_ADVANCE)
            amount != null && amount > advance -> add(LedgerError.MORE_THAN_ADVANCE)
        }
    }
}

// =========================================================================================
// Dates: stored as UTC millis, picked and shown as a Dhaka day
// =========================================================================================

/**
 * The entryDate to save for the Dhaka day [picked]:
 *  - today -> right now;
 *  - an earlier day (an old khata entry) -> that Dhaka day at the current Dhaka time of day.
 * (A future day is refused by [checkLedgerInput] before this is used.)
 */
fun entryDateFor(picked: LocalDate, nowMillis: Long): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(DhakaTime.ZONE)
    if (picked == now.toLocalDate()) return nowMillis
    return picked.atTime(now.toLocalTime()).atZone(DhakaTime.ZONE).toInstant().toEpochMilli()
}

/**
 * The entry to save. The method is kept only where money changes hands; the note is trimmed.
 * Call only when [checkLedgerInput] found no problem (the amount is then set).
 */
fun buildLedgerEntry(customerId: Long, input: LedgerInput, nowMillis: Long): LedgerEntry = LedgerEntry(
    customerId = customerId,
    type = input.action.type,
    amount = requireNotNull(input.amount) { "check the input first" },
    paymentMethod = if (input.action.needsMethod) input.method else null,
    saleId = null,
    note = cleanNote(input.note),
    entryDate = entryDateFor(input.date, nowMillis),
    createdAt = nowMillis
)

/** An entry written later than its date (an old khata entry): the history shows the date only, plus when it was written. */
fun isBackDated(entry: LedgerEntry): Boolean =
    DhakaTime.localDate(entry.entryDate) != DhakaTime.localDate(entry.createdAt)

// =========================================================================================
// Previews for the form ("এরপর ব্যালেন্স হবে ...")
// =========================================================================================

/** The balance after saving this entry. */
fun balanceAfter(currentBalance: Long, action: LedgerAction, amount: Long): Long =
    currentBalance + signedAmount(action.type, amount)

/**
 * The advance (অগ্রিম) this entry would create, for the warning "অগ্রিম ৳ X হবে": only when the
 * entry LOWERS the balance below 0 (for example paying more than the due). null = no warning.
 */
fun advanceCreated(currentBalance: Long, action: LedgerAction, amount: Long): Long? {
    if (signedAmount(action.type, amount) >= 0) return null
    val after = balanceAfter(currentBalance, action, amount)
    return if (after < 0) -after else null
}

// =========================================================================================
// The history list
// =========================================================================================

/** How a history row is labelled. A cancel-sale reversal is a CORRECTION_MINUS linked to a sale. */
enum class HistoryKind {
    OPENING_DUE, SALE_DUE, MANUAL_DUE, PAYMENT_RECEIVED,
    CORRECTION_PLUS, CORRECTION_MINUS, SALE_CANCEL_REVERSAL, ADVANCE_RETURNED
}

fun historyKind(entry: LedgerEntry): HistoryKind = when (entry.type) {
    LedgerEntryType.OPENING_DUE -> HistoryKind.OPENING_DUE
    LedgerEntryType.SALE_DUE -> HistoryKind.SALE_DUE
    LedgerEntryType.MANUAL_DUE -> HistoryKind.MANUAL_DUE
    LedgerEntryType.PAYMENT_RECEIVED -> HistoryKind.PAYMENT_RECEIVED
    LedgerEntryType.CORRECTION_PLUS -> HistoryKind.CORRECTION_PLUS
    LedgerEntryType.CORRECTION_MINUS ->
        if (entry.saleId != null) HistoryKind.SALE_CANCEL_REVERSAL else HistoryKind.CORRECTION_MINUS
    LedgerEntryType.ADVANCE_RETURNED -> HistoryKind.ADVANCE_RETURNED
}

/** A sale due and a cancel-sale reversal open that sale's receipt. */
fun opensReceipt(row: LedgerHistoryRow): Boolean =
    row.entry.saleId != null &&
        historyKind(row.entry).let { it == HistoryKind.SALE_DUE || it == HistoryKind.SALE_CANCEL_REVERSAL }

/** The grey "বাতিল" tag on a sale due whose sale was cancelled later. */
fun showsCancelledTag(row: LedgerHistoryRow): Boolean =
    historyKind(row.entry) == HistoryKind.SALE_DUE && row.saleCancelled

/** The method is shown only where money changed hands. */
fun shownMethod(entry: LedgerEntry): PaymentMethod? =
    if (entry.type == LedgerEntryType.PAYMENT_RECEIVED || entry.type == LedgerEntryType.ADVANCE_RETURNED) entry.paymentMethod else null

data class HistoryDay(val date: LocalDate, val rows: List<LedgerHistoryRow>)

/**
 * Groups the history by the Dhaka day of its entry date, newest day first. Inside a day: newest
 * entry date first, then the newest save time, then the newest id (the same order as the query).
 */
fun groupHistoryByDhakaDay(rows: List<LedgerHistoryRow>): List<HistoryDay> =
    rows
        .sortedWith(
            compareByDescending<LedgerHistoryRow> { it.entry.entryDate }
                .thenByDescending { it.entry.createdAt }
                .thenByDescending { it.entry.id }
        )
        .groupBy { DhakaTime.localDate(it.entry.entryDate) }
        .map { (date, dayRows) -> HistoryDay(date, dayRows) }

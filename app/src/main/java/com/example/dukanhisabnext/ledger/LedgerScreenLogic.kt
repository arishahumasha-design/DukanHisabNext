package com.example.dukanhisabnext.ledger

import com.example.dukanhisabnext.data.dao.LedgerHistoryRow
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.util.DhakaTime
import com.example.dukanhisabnext.util.formatDate
import com.example.dukanhisabnext.util.parseTakaToPaisa
import com.example.dukanhisabnext.util.toBengaliDigits
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// The logic of the khata screens (customer detail history and the shared entry form), as plain
// Kotlin (no Android) so it can be unit-tested on the PC. The Bengali words come from strings.xml.

// =========================================================================================
// The shared entry form
// =========================================================================================

/** What the form holds while the user types. It lives in the ViewModel, so rotation keeps it. */
data class LedgerFormState(
    val action: LedgerAction,
    val amountText: String = "",
    val date: LocalDate,
    val method: PaymentMethod? = LEDGER_METHODS.first(),
    val note: String = ""
)

/** A fresh form for [action]: today (Dhaka), no amount, the first method (নগদ) where a method applies. */
fun newFormState(action: LedgerAction, today: LocalDate): LedgerFormState = LedgerFormState(
    action = action,
    date = today,
    method = if (action.needsMethod) LEDGER_METHODS.first() else null
)

/** The amount typed, in paisa; null when empty or not a number. */
fun parseAmountText(text: String): Long? = if (text.isBlank()) null else parseTakaToPaisa(text.trim())

fun inputOf(state: LedgerFormState): LedgerInput = LedgerInput(
    action = state.action,
    amount = parseAmountText(state.amountText),
    date = state.date,
    method = if (state.action.needsMethod) state.method else null,
    note = state.note
)

/** The note box never takes more than 200 characters (an emoji counts as one). */
fun limitNoteInput(text: String): String =
    if (text.codePointCount(0, text.length) <= MAX_LEDGER_NOTE_LENGTH) {
        text
    } else {
        text.substring(0, text.offsetByCodePoints(0, MAX_LEDGER_NOTE_LENGTH))
    }

fun noteLength(text: String): Int = text.codePointCount(0, text.length)

/** The form shows a plus/minus choice only for a correction. */
fun isCorrection(action: LedgerAction): Boolean =
    action == LedgerAction.CORRECTION_PLUS || action == LedgerAction.CORRECTION_MINUS

/** Which box a problem is shown under. */
enum class FormField { AMOUNT, DATE, METHOD, NOTE, GENERAL }

fun fieldOf(error: LedgerError): FormField = when (error) {
    LedgerError.AMOUNT_MISSING,
    LedgerError.AMOUNT_TOO_LARGE,
    LedgerError.NO_ADVANCE,
    LedgerError.MORE_THAN_ADVANCE -> FormField.AMOUNT
    LedgerError.DATE_IN_FUTURE -> FormField.DATE
    LedgerError.METHOD_REQUIRED -> FormField.METHOD
    LedgerError.NOTE_REQUIRED,
    LedgerError.NOTE_TOO_LONG -> FormField.NOTE
    LedgerError.OPENING_DUE_EXISTS -> FormField.GENERAL
}

/** The problems that belong under [field] (a box shows its first one). */
fun errorsFor(field: FormField, errors: Set<LedgerError>): List<LedgerError> =
    errors.filter { fieldOf(it) == field }

/** Problems that are not about a typed value. */
enum class SaveProblem {
    CUSTOMER_NOT_FOUND, // the customer is gone; nothing was saved
    UNEXPECTED          // something unexpected went wrong; nothing was saved
}

/** The two lines under the amount: the balance after saving, and the advance warning. */
data class FormPreview(val balanceAfter: Long, val advanceCreated: Long?)

/**
 * "এরপর ব্যালেন্স হবে ..." and "অগ্রিম ৳ X হবে". null while no valid amount is typed.
 * [currentBalance] is the customer's balance now (positive = owes, negative = advance).
 */
fun formPreview(currentBalance: Long, state: LedgerFormState): FormPreview? {
    val amount = parseAmountText(state.amountText) ?: return null
    if (amount <= 0 || amount > MAX_LEDGER_AMOUNT) return null
    return FormPreview(
        balanceAfter = balanceAfter(currentBalance, state.action, amount),
        advanceCreated = advanceCreated(currentBalance, state.action, amount)
    )
}

/** For an advance return: the most that can be given back (the current advance), else null. */
fun maxAdvanceReturn(currentBalance: Long, action: LedgerAction): Long? =
    if (action == LedgerAction.ADVANCE_RETURN && currentBalance < 0) -currentBalance else null

// =========================================================================================
// The date box (the Material date picker works in UTC midnights)
// =========================================================================================

fun dateToPickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun pickerMillisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/** A day can be picked unless it is after today (Dhaka). */
fun isSelectableDay(pickerMillis: Long, today: LocalDate): Boolean = !pickerMillisToDate(pickerMillis).isAfter(today)

/** "১১/১১/২০২৬": the day in the form box. */
fun dayText(date: LocalDate): String = formatDate(DhakaTime.startOfDay(date))

// =========================================================================================
// The history rows
// =========================================================================================

/** The type words (from strings.xml). */
data class HistoryLabels(
    val openingDue: String,       // পুরনো বাকি (খাতা থেকে)
    val saleDue: String,          // বিক্রির বাকি
    val manualDue: String,        // বাকি দিলাম
    val payment: String,          // টাকা পেলাম
    val correctionPlus: String,   // সংশোধন (+)
    val correctionMinus: String,  // সংশোধন (−)
    val saleCancel: String,       // বিক্রি বাতিল
    val advanceReturned: String,  // অগ্রিম ফেরত দিলাম
    val taka: String              // ৳
)

private const val SEPARATOR = " · "

/**
 * The first line of a row:
 *  - sale due and cancel reversal: "<label> · <receipt number>";
 *  - payment and advance return: "<label> · <method>";
 *  - correction: "<label> · <note>" (the note is part of the title, see [historyNote]);
 *  - the others: just the label.
 */
fun historyTitle(row: LedgerHistoryRow, labels: HistoryLabels, method: (PaymentMethod) -> String): String {
    val entry = row.entry
    val kind = historyKind(entry)
    val receipt = row.receiptNo?.let { toBengaliDigits(it) }
    return when (kind) {
        HistoryKind.OPENING_DUE -> labels.openingDue
        HistoryKind.MANUAL_DUE -> labels.manualDue
        HistoryKind.SALE_DUE -> withPart(labels.saleDue, receipt)
        HistoryKind.SALE_CANCEL_REVERSAL -> withPart(labels.saleCancel, receipt)
        HistoryKind.PAYMENT_RECEIVED -> withPart(labels.payment, shownMethod(entry)?.let(method))
        HistoryKind.ADVANCE_RETURNED -> withPart(labels.advanceReturned, shownMethod(entry)?.let(method))
        HistoryKind.CORRECTION_PLUS -> withPart(labels.correctionPlus, entry.note)
        HistoryKind.CORRECTION_MINUS -> withPart(labels.correctionMinus, entry.note)
    }
}

private fun withPart(label: String, part: String?): String =
    if (part.isNullOrBlank()) label else label + SEPARATOR + part

/** The note line under the title. A correction already shows its note in the title, so none here. */
fun historyNote(row: LedgerHistoryRow): String? {
    val kind = historyKind(row.entry)
    if (kind == HistoryKind.CORRECTION_PLUS || kind == HistoryKind.CORRECTION_MINUS) return null
    return row.entry.note?.takeIf { it.isNotBlank() }
}

/** "+ ৳ ১০০" when the entry adds to the due, "− ৳ ১০০" when it takes away. */
fun historyAmountText(entry: LedgerEntry, formattedAmount: String, taka: String): String {
    val sign = if (signedAmount(entry) >= 0) "+" else "−"
    return "$sign $taka $formattedAmount"
}

/** True when the amount adds to what the customer owes (the row is coloured amber, else green). */
fun addsToDue(entry: LedgerEntry): Boolean = signedAmount(entry) >= 0

/** The opening due is shown highlighted. */
fun isHighlighted(entry: LedgerEntry): Boolean = historyKind(entry) == HistoryKind.OPENING_DUE

/** "লেখা হয়েছে ১১/১১/২০২৬" for a back-dated row (null for a normal row). [template] fills the date in. */
fun writtenOnText(entry: LedgerEntry, template: (String) -> String): String? =
    if (isBackDated(entry)) template(formatDate(entry.createdAt)) else null

/** Back-dated rows show the date only; a normal row also shows its time. */
fun showsTime(entry: LedgerEntry): Boolean = !isBackDated(entry)

// =========================================================================================
// The action buttons and the saved message
// =========================================================================================

/** The route argument of an action, and back. A route argument can be anything the user did not make. */
fun actionFromRoute(name: String?): LedgerAction? = LedgerAction.entries.firstOrNull { it.name == name }

/** After a correction toggle: the action with the other sign. */
fun withCorrectionSign(action: LedgerAction, plus: Boolean): LedgerAction =
    if (isCorrection(action)) (if (plus) LedgerAction.CORRECTION_PLUS else LedgerAction.CORRECTION_MINUS) else action

/** The customer detail shows one সংশোধন button; it opens the form with the plus choice. */
fun buttonActions(balance: Long, hasOpeningDue: Boolean): List<LedgerAction> =
    availableActions(balance, hasOpeningDue).filter { it != LedgerAction.CORRECTION_MINUS }

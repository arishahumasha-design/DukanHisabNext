package com.example.dukanhisabnext.ui.customers

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.ledger.HistoryLabels
import com.example.dukanhisabnext.ledger.LedgerAction
import com.example.dukanhisabnext.ledger.LedgerError
import com.example.dukanhisabnext.ledger.SaveProblem

// The Bengali words of the khata screens (all from strings.xml).

/** The button on the customer detail screen. Both correction signs share one "সংশোধন" button. */
fun LedgerAction.buttonRes(): Int = when (this) {
    LedgerAction.PAYMENT -> R.string.ledger_btn_payment
    LedgerAction.MANUAL_DUE -> R.string.ledger_btn_manual_due
    LedgerAction.OPENING_DUE -> R.string.ledger_btn_opening_due
    LedgerAction.CORRECTION_PLUS, LedgerAction.CORRECTION_MINUS -> R.string.ledger_btn_correction
    LedgerAction.ADVANCE_RETURN -> R.string.ledger_btn_advance_return
}

/** The title of the entry form. */
fun LedgerAction.titleRes(): Int = when (this) {
    LedgerAction.PAYMENT -> R.string.ledger_title_payment
    LedgerAction.MANUAL_DUE -> R.string.ledger_title_manual_due
    LedgerAction.OPENING_DUE -> R.string.ledger_title_opening_due
    LedgerAction.CORRECTION_PLUS, LedgerAction.CORRECTION_MINUS -> R.string.ledger_title_correction
    LedgerAction.ADVANCE_RETURN -> R.string.ledger_title_advance_return
}

/** One clear Bengali message for every failed check. */
fun LedgerError.messageRes(): Int = when (this) {
    LedgerError.AMOUNT_MISSING -> R.string.err_ledger_amount_missing
    LedgerError.AMOUNT_TOO_LARGE -> R.string.err_ledger_amount_too_large
    LedgerError.DATE_IN_FUTURE -> R.string.err_ledger_date_future
    LedgerError.METHOD_REQUIRED -> R.string.err_ledger_method_required
    LedgerError.NOTE_REQUIRED -> R.string.err_ledger_note_required
    LedgerError.NOTE_TOO_LONG -> R.string.err_ledger_note_too_long
    LedgerError.OPENING_DUE_EXISTS -> R.string.err_ledger_opening_exists
    LedgerError.NO_ADVANCE -> R.string.err_ledger_no_advance
    LedgerError.MORE_THAN_ADVANCE -> R.string.err_ledger_more_than_advance
}

fun SaveProblem.messageRes(): Int = when (this) {
    SaveProblem.CUSTOMER_NOT_FOUND -> R.string.err_ledger_customer_missing
    SaveProblem.UNEXPECTED -> R.string.err_ledger_unexpected
}

/** The method names: the same words as on the receipt. */
@Composable
fun methodLabels(): Map<PaymentMethod, String> = mapOf(
    PaymentMethod.CASH to stringResource(R.string.receipt_method_cash),
    PaymentMethod.BKASH to stringResource(R.string.receipt_method_bkash),
    PaymentMethod.NAGAD to stringResource(R.string.receipt_method_nagad),
    PaymentMethod.BANK to stringResource(R.string.receipt_method_bank),
    PaymentMethod.OTHER to stringResource(R.string.receipt_method_other)
)

/** The type words of the history rows. */
@Composable
fun historyLabels(): HistoryLabels = HistoryLabels(
    openingDue = stringResource(R.string.hist_opening_due),
    saleDue = stringResource(R.string.hist_sale_due),
    manualDue = stringResource(R.string.hist_manual_due),
    payment = stringResource(R.string.hist_payment),
    correctionPlus = stringResource(R.string.hist_correction_plus),
    correctionMinus = stringResource(R.string.hist_correction_minus),
    saleCancel = stringResource(R.string.hist_sale_cancel),
    advanceReturned = stringResource(R.string.hist_advance_returned),
    taka = stringResource(R.string.unit_taka)
)

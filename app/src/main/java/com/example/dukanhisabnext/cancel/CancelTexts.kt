package com.example.dukanhisabnext.cancel

import com.example.dukanhisabnext.data.CancelResult
import com.example.dukanhisabnext.receipt.ReceiptData
import com.example.dukanhisabnext.receipt.ReceiptLabels
import com.example.dukanhisabnext.receipt.receiptMoney

// The words and small rules of the cancel screens, as plain Kotlin (no Android) so they can be
// unit-tested on the PC. The Bengali words come from strings.xml; the tests pass their own.

/** What the confirm dialog says. The three amount sentences get the money text ("৳ ১২০") to fill in. */
data class CancelDialogLabels(
    val stockBack: String,              // স্টক ফেরত যাবে
    val refund: (String) -> String,     // কাস্টমারকে ৳ X ফেরত দিন
    val dueReduced: (String) -> String, // বাকি ৳ Y কমবে
    val advance: (String) -> String     // কাস্টমারের ব্যালেন্স হবে অগ্রিম ৳ Z
)

/** One line of the dialog summary. [isWarning] is true for the advance (অগ্রিম) warning. */
data class CancelSummaryLine(val text: String, val isWarning: Boolean = false)

/**
 * The plain summary lines, in this order:
 *  1. stock comes back (always);
 *  2. the amount paid at the sale is handed back (only if it is above 0);
 *  3. the customer's due goes down (only if the sale had a due);
 *  4. a warning when the customer's balance would become negative (an advance), with that amount.
 */
fun cancelSummaryLines(preview: CancelPreview, labels: CancelDialogLabels, money: (Long) -> String): List<CancelSummaryLine> =
    buildList {
        add(CancelSummaryLine(labels.stockBack))
        if (preview.refundToCustomer > 0) add(CancelSummaryLine(labels.refund(money(preview.refundToCustomer))))
        if (preview.dueReversed > 0) add(CancelSummaryLine(labels.dueReduced(money(preview.dueReversed))))
        val after = preview.balanceAfter
        if (preview.makesAdvance && after != null) add(CancelSummaryLine(labels.advance(money(-after)), isWarning = true))
    }

/** The dialog summary of a receipt, using the same money format as the receipt. */
fun cancelSummaryFor(data: ReceiptData, labels: CancelDialogLabels, receiptLabels: ReceiptLabels): List<CancelSummaryLine> {
    val preview = cancelPreview(
        hasCustomer = data.customerBalance != null,
        paidAmount = data.paid,
        dueAmount = data.saleDue,
        currentBalance = data.customerBalance
    )
    return cancelSummaryLines(preview, labels) { receiptMoney(it, receiptLabels) }
}

/** The reason box never takes more than [MAX_CANCEL_REASON_LENGTH] characters (an emoji counts as one). */
fun limitReasonInput(text: String): String =
    if (text.codePointCount(0, text.length) <= MAX_CANCEL_REASON_LENGTH) {
        text
    } else {
        text.substring(0, text.offsetByCodePoints(0, MAX_CANCEL_REASON_LENGTH))
    }

/** How many characters are typed (as the user sees them), for the "12 / 200" counter. */
fun reasonLength(text: String): Int = text.codePointCount(0, text.length)

/** A calm message after a cancel attempt that did not simply succeed. */
enum class CancelFeedback {
    SALE_NOT_FOUND,  // there is no such sale; nothing was changed
    REASON_TOO_LONG, // the reason is over 200 characters; nothing was changed
    UNEXPECTED       // something unexpected went wrong; nothing was changed
}

/**
 * null = nothing to say: a successful cancel, or "already cancelled" (the screen then simply shows
 * the banner, because the receipt is loaded again).
 */
fun cancelFeedback(result: CancelResult): CancelFeedback? = when (result) {
    is CancelResult.Cancelled -> null
    is CancelResult.AlreadyCancelled -> null
    CancelResult.SaleNotFound -> CancelFeedback.SALE_NOT_FOUND
    CancelResult.ReasonTooLong -> CancelFeedback.REASON_TOO_LONG
}

/** The dialog closes after these results; it stays open (so the reason can be fixed) only for a too-long reason. */
fun closesDialog(result: CancelResult): Boolean = result != CancelResult.ReasonTooLong

/** The "বিক্রি বাতিল করুন" button: hidden once cancelled, disabled while a cancel is running. */
fun showCancelButton(data: ReceiptData): Boolean = !data.isCancelled
fun cancelButtonEnabled(cancelling: Boolean): Boolean = !cancelling

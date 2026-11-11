package com.example.dukanhisabnext.cancel

import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.data.entity.SaleItem
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType

// The rules of cancelling a sale (বিক্রি বাতিল), as plain Kotlin (no Android) so they can be
// unit-tested on the PC. SaleRepository.cancelSale runs them inside one database transaction.
// The sale row itself is never changed: cancelling only ADDS rows.

/** A cancel reason may be at most this many characters (after trimming). */
const val MAX_CANCEL_REASON_LENGTH = 200

sealed interface CancelReasonCheck {
    /** [reason] is trimmed; null when nothing was typed. */
    data class Ok(val reason: String?) : CancelReasonCheck
    data object TooLong : CancelReasonCheck
}

/**
 * The optional reason: trimmed; empty means no reason; more than 200 characters is refused.
 * Characters are counted as the user sees letters (code points), so an emoji counts as one.
 */
fun checkCancelReason(text: String?): CancelReasonCheck {
    val trimmed = text?.trim().orEmpty()
    if (trimmed.isEmpty()) return CancelReasonCheck.Ok(null)
    val length = trimmed.codePointCount(0, trimmed.length)
    return if (length > MAX_CANCEL_REASON_LENGTH) CancelReasonCheck.TooLong else CancelReasonCheck.Ok(trimmed)
}

/**
 * Stock coming back: for EVERY item of the sale, one SALE_CANCEL movement with the sold quantity
 * as a POSITIVE number, linked to the sale. Archived products are included (stock is a fact).
 * The SALE movements made when the sale was saved stay; together they add up to zero.
 */
fun stockBackMovements(saleId: Long, items: List<SaleItem>, now: Long): List<StockMovement> =
    items.map { item ->
        StockMovement(
            productId = item.productId,
            type = StockMovementType.SALE_CANCEL,
            quantity = item.quantity, // thousandths, always > 0 for a saved line
            referenceSaleId = saleId,
            createdAt = now
        )
    }

/**
 * The ledger entry that takes the sale's due away again: CORRECTION_MINUS for exactly that sale's
 * due, linked to the sale. null when the sale left no due (then the ledger is not touched).
 */
fun dueReversalEntry(sale: Sale, now: Long): LedgerEntry? {
    val customerId = sale.customerId ?: return null // a due always has a customer; no customer = no due
    if (sale.dueAmount <= 0) return null
    return LedgerEntry(
        customerId = customerId,
        type = LedgerEntryType.CORRECTION_MINUS,
        amount = sale.dueAmount,
        saleId = sale.id,
        entryDate = now,
        createdAt = now
    )
}

/**
 * What the confirm dialog will say before cancelling (numbers only; the words come from strings.xml).
 *  - [refundToCustomer]: what was paid at the sale; the shopkeeper hands it back outside the app.
 *  - [dueReversed]: how much of the customer's due goes away (0 when the sale had no due).
 *  - [balanceAfter]: the customer's balance after cancelling, or null when there is no customer.
 *    It can be NEGATIVE (the shop then holds money for the customer, shown as "অগ্রিম").
 */
data class CancelPreview(
    val refundToCustomer: Long,
    val dueReversed: Long,
    val balanceAfter: Long?
) {
    val makesAdvance: Boolean get() = (balanceAfter ?: 0L) < 0L
}

/** [currentBalance] is the customer's balance from the ledger right now (null when no customer). */
fun cancelPreview(sale: Sale, currentBalance: Long?): CancelPreview =
    cancelPreview(sale.customerId != null, sale.paidAmount, sale.dueAmount, currentBalance)

/**
 * The same numbers from the plain facts of a sale (the receipt screen has these, not a Sale row).
 * The due is only reversed when the sale has a customer, exactly like [dueReversalEntry].
 */
fun cancelPreview(hasCustomer: Boolean, paidAmount: Long, dueAmount: Long, currentBalance: Long?): CancelPreview {
    val reversed = if (hasCustomer && dueAmount > 0) dueAmount else 0L
    val after = if (hasCustomer && currentBalance != null) currentBalance - reversed else null
    return CancelPreview(refundToCustomer = paidAmount, dueReversed = reversed, balanceAfter = after)
}

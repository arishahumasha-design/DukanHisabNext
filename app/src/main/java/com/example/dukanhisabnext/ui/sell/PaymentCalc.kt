package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.data.SaleDraft
import com.example.dukanhisabnext.data.SaleDraftLine
import com.example.dukanhisabnext.data.SaleResult
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.ui.products.FormError
import com.example.dukanhisabnext.util.CartLine
import com.example.dukanhisabnext.util.SaleCalculator
import com.example.dukanhisabnext.util.SaleCheck
import com.example.dukanhisabnext.util.SaleError
import com.example.dukanhisabnext.util.parseTakaToPaisa

// The payment screen's rules, as plain Kotlin (no Android) so they can be unit-tested.
// Money is Long paisa. The math is the SAME SaleCalculator that saving uses.

/** What the payment screen shows, worked out from the cart and the typed "amount received". */
data class PaymentView(
    val total: Long,
    /** What the customer handed over (paisa). null while the box is empty or unreadable. */
    val received: Long?,
    val receivedError: FormError?,
    /** The part of the total that is paid. Never more than the total. */
    val paid: Long,
    /** What is still owed (বাকি). It becomes the customer's due. */
    val due: Long,
    /** What to give back (ফেরত) when received is more than the total. */
    val change: Long,
    /** There is a due but no customer is chosen yet. */
    val needsCustomer: Boolean,
    /** The cart and the amount are fine. A customer may still be missing (see [needsCustomer]). */
    val readyToSave: Boolean,
    /** Everything is fine, including the customer when one is needed. */
    val canSave: Boolean
)

fun paymentView(summary: CartSummary, receivedText: String, hasCustomer: Boolean): PaymentView {
    // 1. Read the "amount received" box. Empty is a mistake (for a full-credit sale type 0).
    val (received, receivedError) = when {
        receivedText.isBlank() -> null to FormError.REQUIRED
        else -> parseTakaToPaisa(receivedText).let { it to if (it == null) FormError.INVALID_NUMBER else null }
    }
    val notReady = PaymentView(
        total = summary.total, received = received, receivedError = receivedError,
        paid = 0, due = 0, change = 0, needsCustomer = false, readyToSave = false, canSave = false
    )
    if (received == null || !summary.canContinue) return notReady

    // 2. The amounts. hasCustomer = true here: whether a customer is missing is reported
    //    separately (needsCustomer), so the due can still be shown before one is chosen.
    val check = SaleCalculator.check(
        lines = summary.lines.map { CartLine(it.quantity!!, it.unitPrice!!) },
        discount = summary.discount,
        deliveryCharge = summary.delivery,
        received = received,
        hasCustomer = true
    )
    val amounts = (check as? SaleCheck.Valid)?.amounts ?: return notReady

    val needsCustomer = amounts.dueAmount > 0 && !hasCustomer
    return PaymentView(
        total = amounts.total,
        received = received,
        receivedError = null,
        paid = amounts.paidAmount,
        due = amounts.dueAmount,
        change = amounts.change,
        needsCustomer = needsCustomer,
        readyToSave = true,
        canSave = !needsCustomer
    )
}

/**
 * The sale to save, built from the cart. The discount and delivery are the paisa amounts the
 * cart worked out (a percentage discount is already a rounded taka amount). [draftId] is the
 * cart's single id: a double tap sends the same one.
 * Only call this when [CartSummary.canContinue] is true.
 */
fun buildDraft(
    summary: CartSummary,
    received: Long,
    method: PaymentMethod,
    customerId: Long?,
    draftId: String
): SaleDraft = SaleDraft(
    draftId = draftId,
    lines = summary.lines.map { SaleDraftLine(it.input.productId, it.quantity!!, it.unitPrice!!) },
    discount = summary.discount,
    deliveryCharge = summary.delivery,
    received = received,
    paymentMethod = method,
    customerId = customerId
)

/** Why a save did not happen. The screen turns each one into a Bengali message. */
sealed interface PaymentError {
    /** Archived or missing products; [productNames] are the cart lines to remove. */
    data class ProductsUnavailable(val productNames: List<String>) : PaymentError
    data object CustomerNotFound : PaymentError
    data object CustomerRequired : PaymentError
    /** A cart or money rule failed on save (the cart screen should have caught it). */
    data object SaleInvalid : PaymentError
    /** An unexpected failure. Nothing was saved, so the same cart can be saved again. */
    data object SaveFailed : PaymentError
}

/** null for a saved sale; otherwise the problem. [namesById] gives the cart's product names. */
fun paymentErrorOf(result: SaleResult, namesById: Map<Long, String>): PaymentError? = when (result) {
    is SaleResult.Saved -> null
    is SaleResult.ProductsUnavailable ->
        PaymentError.ProductsUnavailable(result.productIds.map { namesById[it] ?: "#$it" })
    SaleResult.CustomerNotFound -> PaymentError.CustomerNotFound
    is SaleResult.Invalid ->
        if (SaleError.CUSTOMER_REQUIRED in result.errors) PaymentError.CustomerRequired else PaymentError.SaleInvalid
}

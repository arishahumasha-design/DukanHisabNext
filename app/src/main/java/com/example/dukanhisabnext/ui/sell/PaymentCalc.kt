package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.data.SaleDraft
import com.example.dukanhisabnext.data.SaleDraftLine
import com.example.dukanhisabnext.data.SaleResult
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.ui.customers.CustomerFormErrors
import com.example.dukanhisabnext.ui.customers.CustomerFormInput
import com.example.dukanhisabnext.ui.customers.CustomerFormResult
import com.example.dukanhisabnext.ui.customers.ValidatedCustomer
import com.example.dukanhisabnext.ui.customers.validateCustomerForm
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
    draftId: String,
    note: String? = null
): SaleDraft = SaleDraft(
    draftId = draftId,
    lines = summary.lines.map { SaleDraftLine(it.input.productId, it.quantity!!, it.unitPrice!!) },
    discount = summary.discount,
    deliveryCharge = summary.delivery,
    received = received,
    paymentMethod = method,
    customerId = customerId,
    note = note
)

// ---- The "নতুন বাকি" screen ----

/** The due typed on the "নতুন বাকি" screen, checked. Paid = total - due. */
data class DueView(val due: Long?, val paid: Long?, val error: FormError?)

/** The due must be filled in, more than 0, and not more than the total. */
fun dueView(total: Long, dueText: String): DueView {
    if (dueText.isBlank()) return DueView(null, null, FormError.REQUIRED)
    val due = parseTakaToPaisa(dueText) ?: return DueView(null, null, FormError.INVALID_NUMBER)
    return when {
        due <= 0L -> DueView(null, null, FormError.MUST_BE_POSITIVE)
        due > total -> DueView(null, null, FormError.DUE_MORE_THAN_TOTAL)
        else -> DueView(due, total - due, null)
    }
}

/**
 * The due the "নতুন বাকি" screen starts with: what is missing when "পেয়েছি" is less than the
 * total, otherwise the whole total (nothing paid).
 */
fun defaultDuePaisa(total: Long, receivedText: String): Long {
    val received = parseTakaToPaisa(receivedText)
    return if (received != null && received < total) total - received else total
}

// ---- Which customer goes on the sale ----

/** What to do about the customer when saving. */
sealed interface CustomerDecision {
    /** A customer from the khata was picked (or created earlier for this cart). */
    data class UseExisting(val id: Long) : CustomerDecision

    /** A new name was typed: create this customer first (customer-form rules already checked). */
    data class CreateNew(val customer: ValidatedCustomer) : CustomerDecision

    /** No customer on this sale (allowed when there is no due). */
    data object NoCustomer : CustomerDecision

    /** Stop: a customer is needed or the typed details are wrong. [errors] go under the boxes. */
    data class Missing(val errors: CustomerFormErrors) : CustomerDecision
}

/**
 * Decides the customer for a save.
 * [sectionOn]: the "কাস্টমার তথ্য" switch (always true on the "নতুন বাকি" screen).
 * [selectedId]: an existing customer already chosen for this cart, or null.
 * [required]: true when the sale leaves a due.
 */
fun customerDecision(
    sectionOn: Boolean,
    selectedId: Long?,
    name: String,
    phone: String,
    address: String,
    required: Boolean
): CustomerDecision {
    val nameNeeded = CustomerDecision.Missing(CustomerFormErrors(name = FormError.REQUIRED))

    if (!sectionOn) return if (required) nameNeeded else CustomerDecision.NoCustomer
    if (selectedId != null) return CustomerDecision.UseExisting(selectedId)
    if (name.isBlank()) return if (required) nameNeeded else CustomerDecision.NoCustomer

    // A new name: the same rules as the customer form in the Khata tab (M3).
    return when (val result = validateCustomerForm(CustomerFormInput(name, phone, address, note = ""))) {
        is CustomerFormResult.Invalid -> CustomerDecision.Missing(result.errors)
        is CustomerFormResult.Valid -> CustomerDecision.CreateNew(result.customer)
    }
}

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

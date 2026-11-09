package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.ui.products.FormError
import com.example.dukanhisabnext.util.CartLine
import com.example.dukanhisabnext.util.MAX_PERCENT_HUNDREDTHS
import com.example.dukanhisabnext.util.SaleCalculator
import com.example.dukanhisabnext.util.SaleCheck
import com.example.dukanhisabnext.util.parsePercent
import com.example.dukanhisabnext.util.parseQuantity
import com.example.dukanhisabnext.util.parseTakaToPaisa

// Reads what is typed in the cart and works out the live numbers: line totals, subtotal,
// discount, delivery, total, and whether the cart may continue. Money is Long paisa,
// quantity is Long thousandths. The math is the SAME SaleCalculator that saving a sale uses
// later, so what the cart shows is what will be saved.

/** One cart line after reading its typed text. */
data class CartLineView(
    val input: CartLineInput,
    val quantity: Long?,           // thousandths; null while the box is empty or unreadable
    val unitPrice: Long?,          // paisa per unit; null while the box is empty or unreadable
    val quantityError: FormError?,
    val priceError: FormError?,
    /** The rounded line total in paisa. null while the line has a problem. */
    val lineTotal: Long?
)

data class CartSummary(
    val lines: List<CartLineView>,
    val discountError: FormError?,
    val deliveryError: FormError?,
    val subtotal: Long,            // sum of the valid, rounded line totals
    /**
     * The taka amount taken off, in paisa. For a percentage this is the rounded amount.
     * 0 when the switch is off, the box is empty, or the box has an error.
     */
    val discount: Long,
    /** The delivery charge in paisa. 0 when the switch is off, the box is empty or wrong. */
    val delivery: Long,
    val total: Long,               // subtotal - discount + delivery
    /** True only when the cart is not empty and every number is fine. */
    val canContinue: Boolean
)

private class Read(val value: Long?, val error: FormError?)

/** Quantity must be filled in and greater than 0. */
private fun readQuantity(text: String): Read {
    if (text.isBlank()) return Read(null, FormError.REQUIRED)
    val value = parseQuantity(text) ?: return Read(null, FormError.INVALID_NUMBER)
    return if (value <= 0L) Read(null, FormError.MUST_BE_POSITIVE) else Read(value, null)
}

/** Unit price must be filled in. 0 is allowed (a free item). */
private fun readPrice(text: String): Read {
    if (text.isBlank()) return Read(null, FormError.REQUIRED)
    val value = parseTakaToPaisa(text) ?: return Read(null, FormError.INVALID_NUMBER)
    return Read(value, null)
}

/** Taka boxes for discount and delivery: empty means 0. */
private fun readAmount(text: String): Read {
    if (text.isBlank()) return Read(0L, null)
    val value = parseTakaToPaisa(text) ?: return Read(null, FormError.INVALID_NUMBER)
    return Read(value, null)
}

private fun viewOf(line: CartLineInput): CartLineView {
    val quantity = readQuantity(line.quantityText)
    val price = readPrice(line.unitPriceText)

    var quantityError = quantity.error
    var lineTotal: Long? = null
    if (quantity.value != null && price.value != null) {
        lineTotal = try {
            SaleCalculator.lineTotal(quantity.value, price.value)
        } catch (e: ArithmeticException) {
            quantityError = FormError.TOO_LARGE // quantity x price does not fit: a typo
            null
        }
    }
    return CartLineView(line, quantity.value, price.value, quantityError, price.error, lineTotal)
}

/** What the discount box means in paisa, or an error. [subtotal] is needed for % and for "too large". */
private class DiscountResult(val paisa: Long, val error: FormError?)

private fun readDiscount(cart: CartState, subtotal: Long, subtotalComplete: Boolean): DiscountResult {
    if (!cart.discountOn || cart.discountText.isBlank()) return DiscountResult(0L, null)

    return when (cart.discountUnit) {
        DiscountUnit.TAKA -> {
            val read = readAmount(cart.discountText)
            when {
                read.error != null -> DiscountResult(0L, read.error)
                // Only judged when every line is fine: otherwise the subtotal is still incomplete.
                subtotalComplete && read.value!! > subtotal -> DiscountResult(0L, FormError.DISCOUNT_TOO_LARGE)
                else -> DiscountResult(read.value!!, null)
            }
        }

        DiscountUnit.PERCENT -> {
            val hundredths = parsePercent(cart.discountText)
                ?: return DiscountResult(0L, FormError.INVALID_NUMBER)
            if (hundredths > MAX_PERCENT_HUNDREDTHS) return DiscountResult(0L, FormError.PERCENT_OUT_OF_RANGE)
            try {
                // Taken from the subtotal, rounded half-up once to the paisa.
                DiscountResult(SaleCalculator.percentDiscount(subtotal, hundredths), null)
            } catch (e: ArithmeticException) {
                DiscountResult(0L, FormError.TOO_LARGE)
            }
        }
    }
}

fun summarize(cart: CartState): CartSummary {
    val views = cart.lines.map(::viewOf)
    val linesOk = views.all { it.quantityError == null && it.priceError == null }

    // Subtotal: the sum of the lines that are fine so far (so it moves while typing).
    var overflow = false
    var subtotal = 0L
    for (view in views) {
        val lineTotal = view.lineTotal ?: continue
        try {
            subtotal = Math.addExact(subtotal, lineTotal)
        } catch (e: ArithmeticException) {
            overflow = true
        }
    }
    if (overflow) subtotal = 0L

    val discount = readDiscount(cart, subtotal, subtotalComplete = linesOk && !overflow)

    // Delivery: a switch and a taka box. Off or empty means 0.
    val deliveryRead = if (cart.deliveryOn) readAmount(cart.deliveryText) else Read(0L, null)
    val deliveryError = deliveryRead.error
    val delivery = if (deliveryError == null) deliveryRead.value ?: 0L else 0L

    val total = try {
        Math.addExact(subtotal - discount.paisa, delivery)
    } catch (e: ArithmeticException) {
        overflow = true
        0L
    }

    // May the shopkeeper continue? Everything must be fine, and the SAME final check that
    // saving uses must agree (the customer and the money received come on the next screen).
    val allFine = views.isNotEmpty() && linesOk && !overflow && discount.error == null && deliveryError == null
    val canContinue = allFine && SaleCalculator.check(
        lines = views.map { CartLine(it.quantity!!, it.unitPrice!!) },
        discount = discount.paisa,
        deliveryCharge = delivery,
        received = null,
        hasCustomer = false
    ) is SaleCheck.Valid

    return CartSummary(views, discount.error, deliveryError, subtotal, discount.paisa, delivery, total, canContinue)
}

/**
 * True when the line sells more than the shelf holds ([quantity] and [stock] are thousandths).
 * Selling more is still allowed (SPEC section 5): the screen only warns.
 * Negative stock always warns. Unknown quantity or stock means no warning.
 */
fun exceedsStock(quantity: Long?, stock: Long?): Boolean =
    quantity != null && stock != null && quantity > stock

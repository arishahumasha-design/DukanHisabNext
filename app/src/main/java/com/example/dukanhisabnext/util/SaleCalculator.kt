package com.example.dukanhisabnext.util

// All sale money math lives here, as plain Kotlin with unit tests (SPEC.md principle 6).
// Money is Long paisa, quantity is Long thousandths. No Double anywhere, so every
// receipt adds up to the paisa.

/** One line in the cart: how much (thousandths) and the price for ONE unit (paisa). */
data class CartLine(val quantity: Long, val unitPrice: Long)

/** Why a sale cannot be saved. The screen turns each code into a Bengali message. */
enum class SaleError {
    EMPTY_CART,
    QUANTITY_NOT_POSITIVE, // a line has quantity 0 (SPEC: quantity must be greater than 0)
    NEGATIVE_PRICE,        // a unit price below 0 (0 = free item is allowed)
    NEGATIVE_AMOUNT,       // discount, delivery charge or amount received below 0
    DISCOUNT_TOO_LARGE,    // discount more than the subtotal (SPEC section 5)
    CUSTOMER_REQUIRED,     // there is a due but no customer (SPEC section 5)
    TOO_LARGE              // a number so big the math would overflow (a typo)
}

/**
 * Every amount of a sale, all in paisa. These always obey:
 *   subtotal = sum of lineTotals
 *   total    = subtotal - discount + deliveryCharge
 *   paid     = the smaller of received and total (never more than total)
 *   due      = total - paid   (never negative)
 *   change   = received - paid (the ফেরত to give back; never negative)
 */
data class SaleAmounts(
    val lineTotals: List<Long>,
    val subtotal: Long,
    val discount: Long,
    val deliveryCharge: Long,
    val total: Long,
    val received: Long,
    val paidAmount: Long,
    val dueAmount: Long,
    val change: Long
) {
    /** True when every rule above holds. Used by tests and as a last check before saving. */
    fun isConsistent(): Boolean =
        subtotal == lineTotals.sum() &&
            total == subtotal - discount + deliveryCharge &&
            paidAmount in 0..total &&
            dueAmount == total - paidAmount &&
            change == received - paidAmount &&
            change >= 0 &&
            discount in 0..subtotal &&
            deliveryCharge >= 0
}

sealed interface SaleCheck {
    data class Valid(val amounts: SaleAmounts) : SaleCheck
    data class Invalid(val errors: Set<SaleError>) : SaleCheck
}

object SaleCalculator {

    /** Adding half of 1000 before dividing by 1000 rounds to the nearest paisa (half-up). */
    private const val HALF_SCALE = QUANTITY_SCALE / 2

    /**
     * Price of one cart line in paisa, rounded half-up to the nearest paisa.
     * quantity is thousandths, so: quantity x unitPrice / 1000.
     *   2.5 kg (2500) x ৳ 40 (4000 paisa)      = 10,000 paisa exactly
     *   0.333 kg (333) x ৳ 100.01 (10001 paisa) = 3330.333 -> 3330 paisa
     *   0.5 (500) x 1 paisa                     = 0.5 -> 1 paisa (half goes up)
     * Throws ArithmeticException if the numbers are too big for a Long.
     */
    fun lineTotal(quantity: Long, unitPrice: Long): Long {
        require(quantity >= 0 && unitPrice >= 0) { "quantity and unitPrice must not be negative" }
        val thousandthsOfPaisa = Math.multiplyExact(quantity, unitPrice)
        return Math.addExact(thousandthsOfPaisa, HALF_SCALE) / QUANTITY_SCALE
    }

    /**
     * Checks a sale and works out every amount.
     * [received] = null means "not on the payment screen yet": treat it as paid in full,
     * so the cart screen can show totals without asking about money or a customer.
     */
    fun check(
        lines: List<CartLine>,
        discount: Long,
        deliveryCharge: Long,
        received: Long?,
        hasCustomer: Boolean
    ): SaleCheck {
        // 1. Simple checks on what was typed.
        val errors = mutableSetOf<SaleError>()
        if (lines.isEmpty()) errors += SaleError.EMPTY_CART
        if (lines.any { it.quantity <= 0 }) errors += SaleError.QUANTITY_NOT_POSITIVE
        if (lines.any { it.unitPrice < 0 }) errors += SaleError.NEGATIVE_PRICE
        if (discount < 0 || deliveryCharge < 0 || (received ?: 0) < 0) errors += SaleError.NEGATIVE_AMOUNT
        if (errors.isNotEmpty()) return SaleCheck.Invalid(errors)

        // 2. The math. Any overflow becomes TOO_LARGE instead of a wrong number.
        return try {
            val lineTotals = lines.map { lineTotal(it.quantity, it.unitPrice) }
            val subtotal = lineTotals.fold(0L) { sum, line -> Math.addExact(sum, line) }

            if (discount > subtotal) return SaleCheck.Invalid(setOf(SaleError.DISCOUNT_TOO_LARGE))

            val total = Math.addExact(subtotal - discount, deliveryCharge)
            val receivedAmount = received ?: total
            val paid = minOf(receivedAmount, total)
            val due = total - paid
            val change = receivedAmount - paid

            if (due > 0 && !hasCustomer) return SaleCheck.Invalid(setOf(SaleError.CUSTOMER_REQUIRED))

            SaleCheck.Valid(
                SaleAmounts(
                    lineTotals = lineTotals,
                    subtotal = subtotal,
                    discount = discount,
                    deliveryCharge = deliveryCharge,
                    total = total,
                    received = receivedAmount,
                    paidAmount = paid,
                    dueAmount = due,
                    change = change
                )
            )
        } catch (e: ArithmeticException) {
            SaleCheck.Invalid(setOf(SaleError.TOO_LARGE))
        }
    }
}

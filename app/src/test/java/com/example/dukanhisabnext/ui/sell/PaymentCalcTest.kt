package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.data.SaleDraft
import com.example.dukanhisabnext.data.SaleDraftLine
import com.example.dukanhisabnext.data.SaleResult
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.ui.products.FormError
import com.example.dukanhisabnext.util.SaleAmounts
import com.example.dukanhisabnext.util.SaleError
import com.example.dukanhisabnext.util.formatTakaPlain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentCalcTest {

    private fun line(id: Long, quantity: String, price: String) =
        CartLineInput(id, "পণ্য $id", ProductUnit.KG, quantity, price)

    /** A cart worked out the way the cart screen does it. */
    private fun summaryOf(
        vararg lines: CartLineInput,
        discountPercent: String? = null,
        delivery: String? = null
    ): CartSummary = summarize(
        CartState(
            lines = lines.toList(),
            discountOn = discountPercent != null,
            discountUnit = DiscountUnit.PERCENT,
            discountText = discountPercent.orEmpty(),
            deliveryOn = delivery != null,
            deliveryText = delivery.orEmpty()
        )
    )

    private val twoHundred = summaryOf(line(1, "1", "200")) // total ৳200 = 20000 paisa

    // ---- The change rule from the plan: total ৳200, received ৳500 ----

    @Test
    fun changeRuleTotal200Received500() {
        val v = paymentView(twoHundred, "500", hasCustomer = false)
        assertEquals(20_000L, v.total)
        assertEquals(50_000L, v.received)
        assertEquals(20_000L, v.paid)   // never more than the total
        assertEquals(0L, v.due)
        assertEquals(30_000L, v.change) // ৳300 back to the customer
        assertFalse(v.needsCustomer)
        assertTrue(v.readyToSave)
        assertTrue(v.canSave)           // no due: no customer needed
    }

    @Test
    fun exactPaymentHasNoChangeAndNoDue() {
        val v = paymentView(twoHundred, "200", hasCustomer = false)
        assertEquals(20_000L, v.paid)
        assertEquals(0L, v.due)
        assertEquals(0L, v.change)
        assertTrue(v.canSave)
    }

    @Test
    fun prefilledTotalGivesNoDueAndNoChange() {
        // The box starts as the total, written as plain text (even with paisa).
        val summary = summaryOf(line(1, "1", "299.99"))
        val prefilled = formatTakaPlain(summary.total)
        assertEquals("299.99", prefilled)

        val v = paymentView(summary, prefilled, hasCustomer = false)
        assertEquals(0L, v.due)
        assertEquals(0L, v.change)
        assertTrue(v.canSave)
    }

    // ---- Dues need a customer ----

    @Test
    fun partPaymentIsADueAndNeedsACustomer() {
        val v = paymentView(twoHundred, "120", hasCustomer = false)
        assertEquals(12_000L, v.paid)
        assertEquals(8_000L, v.due)
        assertEquals(0L, v.change)
        assertTrue(v.needsCustomer)
        assertTrue(v.readyToSave)  // the amount is fine...
        assertFalse(v.canSave)     // ...but Save is blocked until a customer is chosen
    }

    @Test
    fun partPaymentWithACustomerCanBeSaved() {
        val v = paymentView(twoHundred, "120", hasCustomer = true)
        assertEquals(8_000L, v.due)
        assertFalse(v.needsCustomer)
        assertTrue(v.canSave)
    }

    @Test
    fun nothingPaidMakesTheWholeTotalDue() {
        val v = paymentView(twoHundred, "0", hasCustomer = true)
        assertEquals(0L, v.paid)
        assertEquals(20_000L, v.due)
        assertTrue(v.canSave)
        assertFalse(paymentView(twoHundred, "0", hasCustomer = false).canSave)
    }

    @Test
    fun aFreeSaleNeedsNoCustomer() {
        val free = summaryOf(line(1, "1", "0"))
        val v = paymentView(free, "0", hasCustomer = false)
        assertEquals(0L, v.total)
        assertEquals(0L, v.due)
        assertTrue(v.canSave)
    }

    // ---- The "amount received" box ----

    @Test
    fun emptyReceivedIsAMistakeNotZero() {
        val v = paymentView(twoHundred, "", hasCustomer = true)
        assertEquals(FormError.REQUIRED, v.receivedError)
        assertNull(v.received)
        assertFalse(v.readyToSave)
        assertFalse(v.canSave)
        assertEquals(0L, v.due) // nothing is worked out until the box is fine
    }

    @Test
    fun unreadableReceivedIsReported() {
        for (text in listOf("abc", ".", "1.234", "-5")) {
            val v = paymentView(twoHundred, text, hasCustomer = true)
            assertEquals("for '$text'", FormError.INVALID_NUMBER, v.receivedError)
            assertFalse(v.canSave)
        }
    }

    @Test
    fun bengaliDigitsAreAccepted() {
        val v = paymentView(twoHundred, "৫০০", hasCustomer = false)
        assertEquals(50_000L, v.received)
        assertEquals(30_000L, v.change)
    }

    @Test
    fun anInvalidCartCannotBeSaved() {
        val broken = summaryOf(line(1, "", "200")) // quantity box empty
        val v = paymentView(broken, "200", hasCustomer = true)
        assertFalse(v.readyToSave)
        assertFalse(v.canSave)
    }

    @Test
    fun anEmptyCartCannotBeSaved() {
        assertFalse(paymentView(summaryOf(), "0", hasCustomer = true).canSave)
    }

    // ---- Discount and delivery are part of the total ----

    @Test
    fun totalIncludesPercentDiscountAndDelivery() {
        // ৳333.33, 10% off = ৳33.33, delivery ৳20 -> ৳320.00
        val summary = summaryOf(line(1, "1", "333.33"), discountPercent = "10", delivery = "20")
        assertEquals(32_000L, summary.total)

        val v = paymentView(summary, "320", hasCustomer = false)
        assertEquals(0L, v.due)
        assertEquals(0L, v.change)
        assertEquals(32_000L, v.total)
    }

    // ---- buildDraft ----

    @Test
    fun draftCarriesTheCartAndTheCartsDraftId() {
        val summary = summaryOf(
            line(1, "2.5", "40"), line(2, "1", "15.5"),
            discountPercent = "10", delivery = "20"
        )
        // lines: 10000 + 1550 = 11550; 10% = 1155 (1155.0); delivery 2000
        assertEquals(11_550L, summary.subtotal)

        val draft = buildDraft(summary, received = 5_000, method = PaymentMethod.NAGAD, customerId = 7, draftId = "cart-42")

        assertEquals(
            SaleDraft(
                draftId = "cart-42",
                lines = listOf(SaleDraftLine(1, 2_500, 4_000), SaleDraftLine(2, 1_000, 1_550)),
                discount = 1_155,        // the PERCENT discount already turned into paisa
                deliveryCharge = 2_000,
                received = 5_000,
                paymentMethod = PaymentMethod.NAGAD,
                customerId = 7
            ),
            draft
        )
    }

    @Test
    fun draftWithoutCustomerOrDiscount() {
        val draft = buildDraft(twoHundred, 20_000, PaymentMethod.CASH, customerId = null, draftId = "d")
        assertNull(draft.customerId)
        assertEquals(0L, draft.discount)
        assertEquals(0L, draft.deliveryCharge)
    }

    @Test
    fun anEditedCartPriceGoesIntoTheDraftNotTheProduct() {
        val draft = buildDraft(summaryOf(line(1, "2", "115")), 23_000, PaymentMethod.CASH, null, "d")
        assertEquals(11_500L, draft.lines.single().unitPrice) // the typed price, for this sale only
    }

    // ---- Error messages for repository results ----

    private val names = mapOf(1L to "চাল", 2L to "তেল")

    @Test
    fun savedSaleIsNotAnError() {
        val amounts = SaleAmounts(listOf(1), 1, 0, 0, 1, 1, 1, 0, 0)
        assertNull(paymentErrorOf(SaleResult.Saved(1, "261109-001", amounts), names))
    }

    @Test
    fun unavailableProductsAreNamedForTheShopkeeper() {
        val error = paymentErrorOf(SaleResult.ProductsUnavailable(listOf(2, 1)), names)
        assertEquals(PaymentError.ProductsUnavailable(listOf("তেল", "চাল")), error)
    }

    @Test
    fun anUnknownProductIdStillGetsAName() {
        val error = paymentErrorOf(SaleResult.ProductsUnavailable(listOf(99)), names)
        assertEquals(PaymentError.ProductsUnavailable(listOf("#99")), error)
    }

    @Test
    fun customerErrors() {
        assertEquals(PaymentError.CustomerNotFound, paymentErrorOf(SaleResult.CustomerNotFound, names))
        assertEquals(
            PaymentError.CustomerRequired,
            paymentErrorOf(SaleResult.Invalid(setOf(SaleError.CUSTOMER_REQUIRED)), names)
        )
    }

    @Test
    fun otherRuleFailuresAreOneGeneralError() {
        assertEquals(
            PaymentError.SaleInvalid,
            paymentErrorOf(SaleResult.Invalid(setOf(SaleError.DISCOUNT_TOO_LARGE)), names)
        )
        assertEquals(
            PaymentError.SaleInvalid,
            paymentErrorOf(SaleResult.Invalid(setOf(SaleError.EMPTY_CART, SaleError.TOO_LARGE)), names)
        )
    }
}

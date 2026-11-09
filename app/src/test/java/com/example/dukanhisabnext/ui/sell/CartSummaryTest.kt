package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.ui.products.FormError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CartSummaryTest {

    private fun line(id: Long, quantity: String, price: String) =
        CartLineInput(id, "পণ্য $id", ProductUnit.KG, quantity, price)

    private fun cart(vararg lines: CartLineInput, discount: String = "", delivery: String = "") =
        CartState(lines.toList(), discount, delivery)

    // ---- Totals ----

    @Test
    fun emptyCartCannotContinue() {
        val s = summarize(CartState())
        assertEquals(0L, s.subtotal)
        assertEquals(0L, s.total)
        assertFalse(s.canContinue)
    }

    @Test
    fun lineTotalsSubtotalAndTotalAreLive() {
        val s = summarize(
            cart(line(1, "2.5", "40"), line(2, "1", "15"), discount = "5", delivery = "20")
        )
        assertEquals(listOf(10_000L, 1_500L), s.lines.map { it.lineTotal })
        assertEquals(11_500L, s.subtotal)
        assertEquals(13_000L, s.total) // 115 - 5 + 20
        assertTrue(s.canContinue)
    }

    @Test
    fun emptyDiscountAndDeliveryMeanZero() {
        val s = summarize(cart(line(1, "1", "100")))
        assertEquals(10_000L, s.total)
        assertNull(s.discountError)
        assertNull(s.deliveryError)
        assertTrue(s.canContinue)
    }

    @Test
    fun lineTotalsRoundHalfUpToThePaisaAndTheSubtotalUsesTheRoundedLines() {
        val s = summarize(cart(line(1, "0.333", "100.01"), line(2, "0.333", "100.01"), line(3, "0.334", "100.01")))
        assertEquals(listOf(3_330L, 3_330L, 3_340L), s.lines.map { it.lineTotal })
        assertEquals(10_000L, s.subtotal)
    }

    @Test
    fun anEditedPriceIsUsedForTheLine() {
        val s = summarize(cart(line(1, "2", "115")))
        assertEquals(23_000L, s.subtotal) // 2 x ৳115, not the product's own price
    }

    @Test
    fun aFreeItemIsAllowed() {
        val s = summarize(cart(line(1, "1", "0")))
        assertEquals(0L, s.total)
        assertTrue(s.canContinue)
    }

    // ---- Line problems ----

    @Test
    fun emptyQuantityOrPriceBlocksContinueAndShowsRequired() {
        val s = summarize(cart(line(1, "", "50"), line(2, "1", "")))
        assertEquals(FormError.REQUIRED, s.lines[0].quantityError)
        assertEquals(FormError.REQUIRED, s.lines[1].priceError)
        assertFalse(s.canContinue)
    }

    @Test
    fun quantityMustBeReadableAndMoreThanZero() {
        assertEquals(FormError.INVALID_NUMBER, summarize(cart(line(1, "1.2345", "50"))).lines[0].quantityError)
        assertEquals(FormError.INVALID_NUMBER, summarize(cart(line(1, ".", "50"))).lines[0].quantityError)
        assertEquals(FormError.MUST_BE_POSITIVE, summarize(cart(line(1, "0", "50"))).lines[0].quantityError)
        assertFalse(summarize(cart(line(1, "0", "50"))).canContinue)
    }

    @Test
    fun priceMustBeReadable() {
        assertEquals(FormError.INVALID_NUMBER, summarize(cart(line(1, "1", "1.234"))).lines[0].priceError)
    }

    @Test
    fun aBrokenLineIsLeftOutOfTheSubtotalButTheOthersCount() {
        val s = summarize(cart(line(1, "1", "100"), line(2, "", "50")))
        assertEquals(10_000L, s.subtotal)
        assertNull(s.lines[1].lineTotal)
        assertFalse(s.canContinue)
    }

    @Test
    fun hugeNumbersAreReportedInsteadOfBreaking() {
        val s = summarize(cart(line(1, "999999999999999", "999999999999999")))
        assertEquals(FormError.TOO_LARGE, s.lines[0].quantityError)
        assertNull(s.lines[0].lineTotal)
        assertFalse(s.canContinue)
    }

    // ---- Discount and delivery ----

    @Test
    fun discountEqualToSubtotalIsAllowed() {
        val s = summarize(cart(line(1, "1", "50"), discount = "50", delivery = "10"))
        assertNull(s.discountError)
        assertEquals(1_000L, s.total)
        assertTrue(s.canContinue)
    }

    @Test
    fun discountMoreThanSubtotalIsAnErrorAndBlocksContinue() {
        val s = summarize(cart(line(1, "1", "50"), discount = "50.01"))
        assertEquals(FormError.DISCOUNT_TOO_LARGE, s.discountError)
        assertEquals(5_000L, s.total) // a bad discount counts as 0 in the total
        assertFalse(s.canContinue)
    }

    @Test
    fun discountIsNotJudgedWhileALineIsStillBeingTyped() {
        // The subtotal is incomplete, so a "too large" error would be wrong and would flicker.
        val s = summarize(cart(line(1, "1", "50"), line(2, "", "80"), discount = "100"))
        assertNull(s.discountError)
        assertFalse(s.canContinue) // still blocked, by the broken line
    }

    @Test
    fun unreadableDiscountOrDeliveryIsReported() {
        val s = summarize(cart(line(1, "1", "50"), discount = "1.234", delivery = "x"))
        assertEquals(FormError.INVALID_NUMBER, s.discountError)
        assertEquals(FormError.INVALID_NUMBER, s.deliveryError)
        assertEquals(5_000L, s.total)
        assertFalse(s.canContinue)
    }

    @Test
    fun deliveryOnlyAddsToTheTotal() {
        val s = summarize(cart(line(1, "1", "50"), delivery = "12.5"))
        assertEquals(5_000L, s.subtotal)
        assertEquals(6_250L, s.total)
    }

    // ---- Stock warning ----

    @Test
    fun exceedsStockRule() {
        assertTrue(exceedsStock(quantity = 3_000, stock = 2_000))   // 3 > 2
        assertTrue(exceedsStock(quantity = 1, stock = 0))           // nothing on the shelf
        assertTrue(exceedsStock(quantity = 1_000, stock = -500))    // negative stock always warns
        assertFalse(exceedsStock(quantity = 2_000, stock = 2_000))  // exactly all of it: fine
        assertFalse(exceedsStock(quantity = 1_000, stock = 2_000))
        assertFalse(exceedsStock(quantity = null, stock = 2_000))   // box still empty
        assertFalse(exceedsStock(quantity = 1_000, stock = null))   // stock unknown
    }

    @Test
    fun fractionalQuantitiesAreComparedExactly() {
        assertTrue(exceedsStock(quantity = 2_501, stock = 2_500))   // 2.501 kg > 2.5 kg
        assertFalse(exceedsStock(quantity = 2_500, stock = 2_500))
    }
}

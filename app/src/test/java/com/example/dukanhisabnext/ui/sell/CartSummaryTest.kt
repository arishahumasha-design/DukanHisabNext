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

    // The cart now has on/off switches. These tests were written when the boxes were always
    // there, so a box with text in it counts as "switched on" (the discount is in taka).
    private fun cart(vararg lines: CartLineInput, discount: String = "", delivery: String = "") =
        CartState(
            lines = lines.toList(),
            discountOn = discount.isNotEmpty(),
            discountText = discount,
            deliveryOn = delivery.isNotEmpty(),
            deliveryText = delivery
        )

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

    // ---- Switches (new in step 3b) ----

    private val oneLineOf33333 = listOf(line(1, "1", "333.33")) // subtotal ৳333.33 = 33333 paisa

    private fun withDiscount(
        unit: DiscountUnit,
        text: String,
        on: Boolean = true,
        lines: List<CartLineInput> = oneLineOf33333
    ) = CartState(lines = lines, discountOn = on, discountUnit = unit, discountText = text)

    @Test
    fun discountSwitchOffMeansZeroEvenWithTextInTheBox() {
        val s = summarize(withDiscount(DiscountUnit.TAKA, "50", on = false))
        assertEquals(0L, s.discount)
        assertEquals(33_333L, s.total)
        assertNull(s.discountError)
        assertTrue(s.canContinue)
    }

    @Test
    fun deliverySwitchOffMeansZeroEvenWithTextInTheBox() {
        val s = summarize(CartState(oneLineOf33333, deliveryOn = false, deliveryText = "20"))
        assertEquals(0L, s.delivery)
        assertEquals(33_333L, s.total)
    }

    @Test
    fun deliverySwitchOnAddsTheCharge() {
        val s = summarize(CartState(oneLineOf33333, deliveryOn = true, deliveryText = "20"))
        assertEquals(2_000L, s.delivery)
        assertEquals(35_333L, s.total)
    }

    @Test
    fun switchesOnWithEmptyBoxesMeanZero() {
        val s = summarize(CartState(oneLineOf33333, discountOn = true, deliveryOn = true))
        assertEquals(0L, s.discount)
        assertEquals(0L, s.delivery)
        assertTrue(s.canContinue)
    }

    @Test
    fun badDeliveryTextIsOnlyAnErrorWhileTheSwitchIsOn() {
        assertEquals(
            FormError.INVALID_NUMBER,
            summarize(CartState(oneLineOf33333, deliveryOn = true, deliveryText = "x")).deliveryError
        )
        assertNull(summarize(CartState(oneLineOf33333, deliveryOn = false, deliveryText = "x")).deliveryError)
    }

    // ---- Percentage discount ----

    @Test
    fun tenPercentOf33333Paisa() {
        // 10% of ৳333.33 = ৳33.333 -> ৳33.33 (3333 paisa), rounded once to the paisa.
        val s = summarize(withDiscount(DiscountUnit.PERCENT, "10"))
        assertEquals(33_333L, s.subtotal)
        assertEquals(3_333L, s.discount)
        assertEquals(30_000L, s.total) // exactly ৳300.00
        assertNull(s.discountError)
        assertTrue(s.canContinue)
    }

    @Test
    fun zeroPercentIsAllowedAndTakesNothingOff() {
        val s = summarize(withDiscount(DiscountUnit.PERCENT, "0"))
        assertEquals(0L, s.discount)
        assertEquals(33_333L, s.total)
        assertNull(s.discountError)
        assertTrue(s.canContinue)
    }

    @Test
    fun hundredPercentTakesEverythingOff() {
        val s = summarize(withDiscount(DiscountUnit.PERCENT, "100"))
        assertEquals(33_333L, s.discount)
        assertEquals(0L, s.total)
        assertNull(s.discountError)
        assertTrue(s.canContinue)
    }

    @Test
    fun hundredPercentWithDeliveryLeavesTheDelivery() {
        val s = summarize(CartState(oneLineOf33333, true, DiscountUnit.PERCENT, "100", true, "20"))
        assertEquals(2_000L, s.total)
    }

    @Test
    fun moreThanHundredPercentIsRefused() {
        for (text in listOf("100.01", "101", "150", "1000")) {
            val s = summarize(withDiscount(DiscountUnit.PERCENT, text))
            assertEquals("for $text%", FormError.PERCENT_OUT_OF_RANGE, s.discountError)
            assertEquals(0L, s.discount)
            assertEquals(33_333L, s.total) // a bad discount counts as 0
            assertFalse(s.canContinue)
        }
    }

    @Test
    fun decimalPercentsWork() {
        // 12.5% of 10000 paisa = 1250 paisa
        val s = summarize(withDiscount(DiscountUnit.PERCENT, "12.5", lines = listOf(line(1, "1", "100"))))
        assertEquals(1_250L, s.discount)
    }

    @Test
    fun percentIsTakenFromTheSubtotalOfTheRoundedLines() {
        // Lines round to 3330 + 3330 + 3340 = 10000, so 10% is exactly 1000 (not 10% of the unrounded sum).
        val lines = listOf(line(1, "0.333", "100.01"), line(2, "0.333", "100.01"), line(3, "0.334", "100.01"))
        val s = summarize(withDiscount(DiscountUnit.PERCENT, "10", lines = lines))
        assertEquals(10_000L, s.subtotal)
        assertEquals(1_000L, s.discount)
    }

    @Test
    fun percentIsTakenFromTheSubtotalNotFromTheDelivery() {
        val s = summarize(CartState(oneLineOf33333, true, DiscountUnit.PERCENT, "10", true, "100"))
        assertEquals(3_333L, s.discount)             // 10% of the subtotal only
        assertEquals(33_333L - 3_333L + 10_000L, s.total)
    }

    @Test
    fun badPercentTextIsReported() {
        assertEquals(FormError.INVALID_NUMBER, summarize(withDiscount(DiscountUnit.PERCENT, "abc")).discountError)
        assertEquals(FormError.INVALID_NUMBER, summarize(withDiscount(DiscountUnit.PERCENT, "1.234")).discountError)
        assertEquals(FormError.INVALID_NUMBER, summarize(withDiscount(DiscountUnit.PERCENT, ".")).discountError)
    }

    @Test
    fun emptyPercentBoxMeansZero() {
        val s = summarize(withDiscount(DiscountUnit.PERCENT, ""))
        assertEquals(0L, s.discount)
        assertTrue(s.canContinue)
    }

    @Test
    fun theSameTextMeansDifferentThingsInTakaAndInPercent() {
        assertEquals(1_000L, summarize(withDiscount(DiscountUnit.TAKA, "10")).discount)    // ৳10
        assertEquals(3_333L, summarize(withDiscount(DiscountUnit.PERCENT, "10")).discount) // 10%
    }

    @Test
    fun percentFollowsTheSubtotalLive() {
        val one = summarize(withDiscount(DiscountUnit.PERCENT, "10", lines = listOf(line(1, "1", "100"))))
        val two = summarize(withDiscount(DiscountUnit.PERCENT, "10", lines = listOf(line(1, "2", "100"))))
        assertEquals(1_000L, one.discount)
        assertEquals(2_000L, two.discount)
    }

    @Test
    fun percentOfAnEmptyCartIsZeroAndCannotContinue() {
        val s = summarize(withDiscount(DiscountUnit.PERCENT, "10", lines = emptyList()))
        assertEquals(0L, s.discount)
        assertFalse(s.canContinue)
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

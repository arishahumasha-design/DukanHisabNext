package com.example.dukanhisabnext.util

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleCalculatorTest {

    // Helpers: run a check and unwrap the result.
    private fun valid(
        lines: List<CartLine>,
        discount: Long = 0,
        delivery: Long = 0,
        received: Long? = null,
        hasCustomer: Boolean = false
    ): SaleAmounts {
        val result = SaleCalculator.check(lines, discount, delivery, received, hasCustomer)
        return (result as SaleCheck.Valid).amounts
    }

    private fun errors(
        lines: List<CartLine>,
        discount: Long = 0,
        delivery: Long = 0,
        received: Long? = null,
        hasCustomer: Boolean = false
    ): Set<SaleError> {
        val result = SaleCalculator.check(lines, discount, delivery, received, hasCustomer)
        return (result as SaleCheck.Invalid).errors
    }

    // ---- lineTotal: quantity (thousandths) x unit price (paisa) / 1000, half-up ----

    @Test
    fun exactLineTotals() {
        assertEquals(10_000L, SaleCalculator.lineTotal(2_500, 4_000))   // 2.5 kg x ৳40 = ৳100
        assertEquals(36_000L, SaleCalculator.lineTotal(3_000, 12_000))  // 3 pcs x ৳120 = ৳360
        assertEquals(0L, SaleCalculator.lineTotal(1_000, 0))            // a free item
    }

    @Test
    fun fractionsOfAPaisaRoundHalfUp() {
        assertEquals(3_330L, SaleCalculator.lineTotal(333, 10_001))  // 3330.333 -> 3330
        assertEquals(1L, SaleCalculator.lineTotal(500, 1))           // 0.5 -> 1 (half goes up)
        assertEquals(0L, SaleCalculator.lineTotal(499, 1))           // 0.499 -> 0
        assertEquals(5_000L, SaleCalculator.lineTotal(1_500, 3_333)) // 4999.5 -> 5000
        assertEquals(4_999L, SaleCalculator.lineTotal(1_499, 3_335)) // 4999.165 -> 4999
    }

    @Test(expected = ArithmeticException::class)
    fun lineTotalOverflowThrows() {
        SaleCalculator.lineTotal(Long.MAX_VALUE, 2)
    }

    // ---- check: a normal sale ----

    @Test
    fun fullCashSaleWithDiscountDeliveryAndChange() {
        val a = valid(
            lines = listOf(CartLine(2_500, 4_000), CartLine(1_000, 1_500)), // ৳100 + ৳15
            discount = 500,   // ৳5
            delivery = 2_000, // ৳20
            received = 20_000 // ৳200
        )
        assertEquals(listOf(10_000L, 1_500L), a.lineTotals)
        assertEquals(11_500L, a.subtotal)
        assertEquals(13_000L, a.total)      // 115 - 5 + 20
        assertEquals(13_000L, a.paidAmount) // never more than the total
        assertEquals(0L, a.dueAmount)
        assertEquals(7_000L, a.change)      // ৳70 back to the customer
        assertTrue(a.isConsistent())
    }

    @Test
    fun receiptAlwaysAddsUpBecauseSubtotalUsesTheRoundedLines() {
        // Each line rounds; the subtotal is the sum of the ROUNDED lines, as printed.
        val a = valid(listOf(CartLine(333, 10_001), CartLine(333, 10_001), CartLine(334, 10_001)))
        assertEquals(listOf(3_330L, 3_330L, 3_340L), a.lineTotals)
        assertEquals(10_000L, a.subtotal)
        assertTrue(a.isConsistent())
    }

    @Test
    fun notOnThePaymentScreenYetMeansPaidInFull() {
        val a = valid(listOf(CartLine(1_000, 5_000)), received = null)
        assertEquals(5_000L, a.paidAmount)
        assertEquals(0L, a.dueAmount)
        assertEquals(0L, a.change)
    }

    @Test
    fun exactPaymentHasNoChangeAndNoDue() {
        val a = valid(listOf(CartLine(1_000, 5_000)), received = 5_000)
        assertEquals(0L, a.change)
        assertEquals(0L, a.dueAmount)
    }

    // ---- dues ----

    @Test
    fun partPaymentBecomesADueWhenThereIsACustomer() {
        val a = valid(listOf(CartLine(1_000, 13_000)), received = 5_000, hasCustomer = true)
        assertEquals(5_000L, a.paidAmount)
        assertEquals(8_000L, a.dueAmount)
        assertEquals(0L, a.change)
        assertTrue(a.isConsistent())
    }

    @Test
    fun nothingPaidMeansTheWholeTotalIsDue() {
        val a = valid(listOf(CartLine(1_000, 13_000)), received = 0, hasCustomer = true)
        assertEquals(0L, a.paidAmount)
        assertEquals(13_000L, a.dueAmount)
    }

    @Test
    fun aDueWithoutACustomerIsRefused() {
        assertEquals(setOf(SaleError.CUSTOMER_REQUIRED), errors(listOf(CartLine(1_000, 13_000)), received = 5_000))
    }

    @Test
    fun aCustomerIsOptionalWhenThereIsNoDue() {
        valid(listOf(CartLine(1_000, 13_000)), received = 13_000, hasCustomer = false)
        valid(listOf(CartLine(1_000, 13_000)), received = 13_000, hasCustomer = true)
    }

    // ---- discount and delivery ----

    @Test
    fun discountEqualToSubtotalIsAllowed() {
        val a = valid(listOf(CartLine(1_000, 5_000)), discount = 5_000, delivery = 1_000)
        assertEquals(1_000L, a.total) // only the delivery is left
    }

    @Test
    fun discountMoreThanSubtotalIsRefused() {
        assertEquals(setOf(SaleError.DISCOUNT_TOO_LARGE), errors(listOf(CartLine(1_000, 5_000)), discount = 5_001))
    }

    @Test
    fun aFreeSaleWithTotalZeroIsAllowed() {
        val a = valid(listOf(CartLine(1_000, 0)), received = 0)
        assertEquals(0L, a.total)
        assertEquals(0L, a.dueAmount)
    }

    // ---- bad input ----

    @Test
    fun emptyCartIsRefused() {
        assertEquals(setOf(SaleError.EMPTY_CART), errors(emptyList()))
    }

    @Test
    fun zeroQuantityIsRefused() {
        assertEquals(setOf(SaleError.QUANTITY_NOT_POSITIVE), errors(listOf(CartLine(0, 5_000))))
    }

    @Test
    fun negativePriceIsRefused() {
        assertEquals(setOf(SaleError.NEGATIVE_PRICE), errors(listOf(CartLine(1_000, -1))))
    }

    @Test
    fun negativeAmountsAreRefused() {
        val line = listOf(CartLine(1_000, 5_000))
        assertEquals(setOf(SaleError.NEGATIVE_AMOUNT), errors(line, discount = -1))
        assertEquals(setOf(SaleError.NEGATIVE_AMOUNT), errors(line, delivery = -1))
        assertEquals(setOf(SaleError.NEGATIVE_AMOUNT), errors(line, received = -1))
    }

    @Test
    fun severalSimpleProblemsAreReportedTogether() {
        val e = errors(listOf(CartLine(0, -5)), discount = -1)
        assertEquals(
            setOf(SaleError.QUANTITY_NOT_POSITIVE, SaleError.NEGATIVE_PRICE, SaleError.NEGATIVE_AMOUNT),
            e
        )
    }

    @Test
    fun hugeNumbersGiveTooLargeInsteadOfAWrongTotal() {
        assertEquals(setOf(SaleError.TOO_LARGE), errors(listOf(CartLine(Long.MAX_VALUE, 2))))
        assertEquals(setOf(SaleError.TOO_LARGE), errors(listOf(CartLine(1, Long.MAX_VALUE))))
        assertEquals(setOf(SaleError.TOO_LARGE), errors(listOf(CartLine(1_000, 5_000)), delivery = Long.MAX_VALUE))
    }

    // ---- many random sales: the rules must ALWAYS hold ----

    @Test
    fun randomSalesAlwaysAddUpToThePaisa() {
        val random = Random(seed = 2026) // fixed seed: the same "random" carts every run
        repeat(5_000) {
            val lines = List(random.nextInt(1, 8)) {
                CartLine(quantity = random.nextLong(1, 50_000), unitPrice = random.nextLong(0, 500_000))
            }
            val subtotal = lines.sumOf { SaleCalculator.lineTotal(it.quantity, it.unitPrice) }
            val discount = random.nextLong(0, subtotal + 1)
            val delivery = random.nextLong(0, 10_000)
            val received = random.nextLong(0, subtotal + delivery + 20_000)

            val a = valid(lines, discount, delivery, received, hasCustomer = true)

            assertTrue("rules broken for $lines", a.isConsistent())
            // Each line is within half a paisa of the exact value.
            lines.zip(a.lineTotals).forEach { (line, rounded) ->
                val exactTimes1000 = line.quantity * line.unitPrice
                assertTrue(kotlin.math.abs(rounded * 1000 - exactTimes1000) <= 500)
            }
        }
    }
}

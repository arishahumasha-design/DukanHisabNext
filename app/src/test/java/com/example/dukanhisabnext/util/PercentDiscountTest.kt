package com.example.dukanhisabnext.util

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PercentDiscountTest {

    // ---- parsePercent: hundredths of a percent ----

    @Test
    fun parsesPercentsAsHundredths() {
        assertEquals(1_000L, parsePercent("10"))
        assertEquals(1_250L, parsePercent("12.5"))
        assertEquals(0L, parsePercent("0"))
        assertEquals(10_000L, parsePercent("100"))
        assertEquals(1_250L, parsePercent("১২.৫")) // Bengali digits
        assertEquals(5L, parsePercent("0.05"))
    }

    @Test
    fun parseDoesNotLimitTheSize() {
        // The 0..100 rule is checked by the caller; parse only reads the number.
        assertEquals(10_001L, parsePercent("100.01"))
        assertEquals(15_000L, parsePercent("150"))
    }

    @Test
    fun badPercentTextIsNull() {
        assertNull(parsePercent(""))
        assertNull(parsePercent("."))
        assertNull(parsePercent("abc"))
        assertNull(parsePercent("-5"))
        assertNull(parsePercent("1.234"))
    }

    // ---- percentDiscount: rounded half-up ONCE to the paisa ----

    @Test
    fun tenPercentOf33333Paisa() {
        // ৳333.33 x 10% = ৳33.333 -> 3333 paisa (৳33.33)
        assertEquals(3_333L, SaleCalculator.percentDiscount(33_333, 1_000))
    }

    @Test
    fun zeroPercentIsZero() {
        assertEquals(0L, SaleCalculator.percentDiscount(33_333, 0))
        assertEquals(0L, SaleCalculator.percentDiscount(0, 1_000))
    }

    @Test
    fun hundredPercentIsTheWholeSubtotal() {
        assertEquals(33_333L, SaleCalculator.percentDiscount(33_333, 10_000))
        assertEquals(1L, SaleCalculator.percentDiscount(1, 10_000))
    }

    @Test
    fun roundsHalfUp() {
        assertEquals(1L, SaleCalculator.percentDiscount(1, 5_000))   // 50% of 1 paisa = 0.5 -> 1
        assertEquals(2L, SaleCalculator.percentDiscount(3, 5_000))   // 50% of 3 paisa = 1.5 -> 2
        assertEquals(0L, SaleCalculator.percentDiscount(1, 4_999))   // 49.99% of 1 paisa = 0.4999 -> 0
        assertEquals(33L, SaleCalculator.percentDiscount(100, 3_333)) // 33.33% of 100 = 33.33 -> 33
        assertEquals(1_250L, SaleCalculator.percentDiscount(10_000, 1_250)) // 12.5% of ৳100 = ৳12.50
        assertEquals(3L, SaleCalculator.percentDiscount(25, 1_250))  // 12.5% of 25 = 3.125 -> 3
        assertEquals(4L, SaleCalculator.percentDiscount(35, 1_250))  // 12.5% of 35 = 4.375 -> 4
    }

    @Test(expected = IllegalArgumentException::class)
    fun moreThanHundredPercentIsRefused() {
        SaleCalculator.percentDiscount(33_333, 10_001) // 100.01%
    }

    @Test(expected = IllegalArgumentException::class)
    fun aHugePercentIsRefused() {
        SaleCalculator.percentDiscount(33_333, 15_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aNegativePercentIsRefused() {
        SaleCalculator.percentDiscount(33_333, -1)
    }

    @Test(expected = ArithmeticException::class)
    fun overflowIsReportedNotWrong() {
        SaleCalculator.percentDiscount(Long.MAX_VALUE, 10_000)
    }

    @Test
    fun aPercentageAboveOneHundredIsCaughtByTheLimitConstant() {
        assertTrue(parsePercent("100.01")!! > MAX_PERCENT_HUNDREDTHS)
        assertTrue(parsePercent("100")!! <= MAX_PERCENT_HUNDREDTHS)
    }

    // ---- Many random cases: the discount never breaks the money rules ----

    @Test
    fun randomPercentDiscountsNeverExceedTheSubtotalAndStayWithinHalfAPaisa() {
        val random = Random(seed = 33)
        repeat(5_000) {
            val subtotal = random.nextLong(0, 5_000_000)
            val percent = random.nextLong(0, MAX_PERCENT_HUNDREDTHS + 1)

            val discount = SaleCalculator.percentDiscount(subtotal, percent)

            assertTrue("discount $discount > subtotal $subtotal at $percent", discount in 0..subtotal)
            // Within half a paisa of the exact value (exact = subtotal x percent / 10000).
            val exactTimes10000 = subtotal * percent
            assertTrue(kotlin.math.abs(discount * 10_000 - exactTimes10000) <= 5_000)
        }
    }

    @Test
    fun aPercentDiscountedSaleStillAddsUpToThePaisa() {
        val random = Random(seed = 34)
        repeat(2_000) {
            val lines = List(random.nextInt(1, 6)) {
                CartLine(random.nextLong(1, 20_000), random.nextLong(0, 300_000))
            }
            val subtotal = lines.sumOf { SaleCalculator.lineTotal(it.quantity, it.unitPrice) }
            val discount = SaleCalculator.percentDiscount(subtotal, random.nextLong(0, MAX_PERCENT_HUNDREDTHS + 1))
            val delivery = random.nextLong(0, 10_000)

            val check = SaleCalculator.check(lines, discount, delivery, received = null, hasCustomer = false)
            val amounts = (check as SaleCheck.Valid).amounts

            assertTrue(amounts.isConsistent())
            assertEquals(subtotal - discount + delivery, amounts.total)
        }
    }
}

package com.example.dukanhisabnext.sale

import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.util.DhakaTime
import com.example.dukanhisabnext.util.ReceiptNumber
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleDateRulesTest {

    private val today = LocalDate.of(2026, 11, 13)

    /** 13 Nov 2026, 15:20 in Dhaka. */
    private val now = LocalDateTime.of(2026, 11, 13, 15, 20).atZone(DhakaTime.ZONE).toInstant().toEpochMilli()

    @Test
    fun todayAndEarlierDaysAreFine() {
        assertNull(checkSaleDay(today, today))
        assertNull(checkSaleDay(today.minusDays(1), today))
        assertNull(checkSaleDay(LocalDate.of(2020, 1, 1), today))
    }

    @Test
    fun aFutureDayIsRefused() {
        assertEquals(SaleDayProblem.FUTURE, checkSaleDay(today.plusDays(1), today))
    }

    @Test
    fun aDayBefore2020IsRefused() {
        assertEquals(SaleDayProblem.BEFORE_MIN, checkSaleDay(LocalDate.of(2019, 12, 31), today))
        assertEquals(SaleDayProblem.BEFORE_MIN, checkSaleDay(LocalDate.of(1926, 11, 13), today)) // a mistyped year
    }

    @Test
    fun onlyMoreThanThirtyDaysAsks() {
        assertFalse(needsOldDateConfirm(today, today))
        assertFalse(needsOldDateConfirm(today.minusDays(30), today))
        assertTrue(needsOldDateConfirm(today.minusDays(31), today))
        assertTrue(needsOldDateConfirm(LocalDate.of(2020, 1, 1), today))
    }

    @Test
    fun todayIsRightNow() {
        assertEquals(now, saleDateFor(today, now))
    }

    @Test
    fun anEarlierDayKeepsTheTimeOfDay() {
        val expected = LocalDateTime.of(2026, 11, 10, 15, 20).atZone(DhakaTime.ZONE).toInstant().toEpochMilli()
        assertEquals(expected, saleDateFor(LocalDate.of(2026, 11, 10), now))
    }

    @Test
    fun theReceiptNumberOfABackDatedSaleCarriesItsOwnDay() {
        assertEquals("261110", ReceiptNumber.datePrefix(saleDateFor(LocalDate.of(2026, 11, 10), now)))
        assertEquals("261113", ReceiptNumber.datePrefix(saleDateFor(today, now)))
    }

    private fun sale(createdAt: Long, saleDate: Long) = Sale(
        receiptNo = "x", subtotal = 0, discount = 0, deliveryCharge = 0, total = 0, paidAmount = 0,
        paymentMethod = PaymentMethod.CASH, dueAmount = 0, createdAt = createdAt, saleDate = saleDate
    )

    @Test
    fun aSaleIsBackDatedOnlyWhenItsDayDiffersFromTheDayOfSaving() {
        assertFalse(isBackDated(sale(now, now)))
        assertTrue(isBackDated(sale(now, saleDateFor(today.minusDays(1), now))))
        assertFalse(isBackDated(sale(createdAt = 1_000L, saleDate = 1_000L))) // an old, migrated sale
    }

    @Test
    fun aSaleBuiltWithoutASaleDateTakesItsCreationTime() {
        val old = Sale(
            receiptNo = "x", subtotal = 0, discount = 0, deliveryCharge = 0, total = 0, paidAmount = 0,
            paymentMethod = PaymentMethod.CASH, dueAmount = 0, createdAt = 42L
        )
        assertEquals(42L, old.saleDate)
    }
}

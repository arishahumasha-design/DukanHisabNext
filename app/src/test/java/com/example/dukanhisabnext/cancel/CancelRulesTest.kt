package com.example.dukanhisabnext.cancel

import com.example.dukanhisabnext.data.dao.SaleListRow
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.data.entity.SaleItem
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CancelRulesTest {

    private val now = 1_762_800_000_000L

    private fun sale(
        id: Long = 7,
        customerId: Long? = 3,
        total: Long = 50_000,
        paid: Long = 20_000,
        due: Long = 30_000
    ) = Sale(
        id = id, receiptNo = "261110-001", customerId = customerId,
        subtotal = total, discount = 0, deliveryCharge = 0, total = total,
        paidAmount = paid, paymentMethod = PaymentMethod.CASH, dueAmount = due, createdAt = now - 1_000
    )

    private val items = listOf(
        SaleItem(1, 7, productId = 11, productNameSnapshot = "চাল", quantity = 2_500, unitPrice = 4_000, lineTotal = 10_000),
        SaleItem(2, 7, productId = 12, productNameSnapshot = "সাবান", quantity = 3_000, unitPrice = 5_000, lineTotal = 15_000),
        SaleItem(3, 7, productId = 13, productNameSnapshot = "জিরা", quantity = 250, unitPrice = 80_000, lineTotal = 20_000)
    )

    /** The balance formula from SPEC.md section 4 (the Kotlin twin of the database formula). */
    private fun balance(entries: List<LedgerEntry>): Long = com.example.dukanhisabnext.ledger.balanceOf(entries)

    private fun entry(type: LedgerEntryType, amount: Long) =
        LedgerEntry(customerId = 3, type = type, amount = amount, entryDate = now, createdAt = now)

    // =====================================================================================
    // The reason: trimmed, optional, at most 200 characters
    // =====================================================================================

    @Test
    fun noReasonIsFine() {
        assertEquals(CancelReasonCheck.Ok(null), checkCancelReason(null))
        assertEquals(CancelReasonCheck.Ok(null), checkCancelReason(""))
        assertEquals(CancelReasonCheck.Ok(null), checkCancelReason("    "))
        assertEquals(CancelReasonCheck.Ok(null), checkCancelReason("\n\t "))
    }

    @Test
    fun theReasonIsTrimmed() {
        assertEquals(CancelReasonCheck.Ok("ভুল বিক্রি"), checkCancelReason("  ভুল বিক্রি  "))
        assertEquals(CancelReasonCheck.Ok("wrong item"), checkCancelReason("\nwrong item\n"))
    }

    @Test
    fun exactly200CharactersIsAllowed() {
        val reason = "ক".repeat(200)
        assertEquals(CancelReasonCheck.Ok(reason), checkCancelReason(reason))
    }

    @Test
    fun moreThan200CharactersIsRefused() {
        assertEquals(CancelReasonCheck.TooLong, checkCancelReason("ক".repeat(201)))
        assertEquals(CancelReasonCheck.TooLong, checkCancelReason("a".repeat(500)))
    }

    @Test
    fun spacesAroundDoNotCountTowardsTheLimit() {
        val reason = "ক".repeat(200)
        assertEquals(CancelReasonCheck.Ok(reason), checkCancelReason("   $reason   "))
    }

    @Test
    fun anEmojiCountsAsOneCharacter() {
        // Each emoji is two "chars" inside Kotlin, but one letter on screen.
        val emojis = "😀".repeat(200)
        assertEquals(400, emojis.length)
        assertEquals(CancelReasonCheck.Ok(emojis), checkCancelReason(emojis))
        assertEquals(CancelReasonCheck.TooLong, checkCancelReason("😀".repeat(201)))
    }

    @Test
    fun theLimitIs200() {
        assertEquals(200, MAX_CANCEL_REASON_LENGTH)
    }

    // =====================================================================================
    // Stock comes back
    // =====================================================================================

    @Test
    fun everyItemComesBackAsAPositiveSaleCancelMovementLinkedToTheSale() {
        val movements = stockBackMovements(saleId = 7, items = items, now = now)

        assertEquals(3, movements.size)
        assertEquals(listOf(11L, 12L, 13L), movements.map { it.productId })
        assertEquals(listOf(2_500L, 3_000L, 250L), movements.map { it.quantity }) // 0.250 kg too
        assertTrue(movements.all { it.type == StockMovementType.SALE_CANCEL })
        assertTrue(movements.all { it.referenceSaleId == 7L })
        assertTrue(movements.all { it.createdAt == now })
        assertTrue(movements.all { it.quantity > 0 })
        assertTrue(movements.all { it.unitCost == null })
    }

    @Test
    fun theSaleAndItsCancellationAddUpToZeroStockChange() {
        // When the sale was saved, each item went OUT (negative). Cancelling brings each back IN.
        val soldOut = items.map {
            StockMovement(productId = it.productId, type = StockMovementType.SALE, quantity = -it.quantity, referenceSaleId = 7, createdAt = now)
        }
        val all = soldOut + stockBackMovements(7, items, now)
        val perProduct = all.groupBy { it.productId }.mapValues { (_, list) -> list.sumOf { it.quantity } }
        assertEquals(mapOf(11L to 0L, 12L to 0L, 13L to 0L), perProduct)
    }

    @Test
    fun aSaleThatPushedStockBelowZeroGetsExactlyTheSoldQuantityBack() {
        // 1 kg on the shelf, 3 kg sold: stock -2 kg. Cancelling adds 3 kg back: stock 1 kg again.
        val opening = 1_000L
        val sold = SaleItem(1, 7, 11, "চাল", 3_000, 4_000, null, 12_000)
        val afterSale = opening - sold.quantity
        assertEquals(-2_000L, afterSale)
        val back = stockBackMovements(7, listOf(sold), now).single().quantity
        assertEquals(opening, afterSale + back)
    }

    @Test
    fun noItemsMeansNoMovements() {
        assertTrue(stockBackMovements(7, emptyList(), now).isEmpty())
    }

    // =====================================================================================
    // The due is taken away
    // =====================================================================================

    @Test
    fun aDueIsReversedByExactlyThatAmountLinkedToTheSale() {
        val entry = dueReversalEntry(sale(due = 30_000), now)!!
        assertEquals(LedgerEntryType.CORRECTION_MINUS, entry.type)
        assertEquals(30_000L, entry.amount)
        assertEquals(3L, entry.customerId)
        assertEquals(7L, entry.saleId)
        assertEquals(now, entry.entryDate)
        assertEquals(now, entry.createdAt)
        assertNull(entry.paymentMethod)
    }

    @Test
    fun aFullyPaidSaleDoesNotTouchTheLedger() {
        assertNull(dueReversalEntry(sale(paid = 50_000, due = 0), now))
    }

    @Test
    fun aSaleWithoutACustomerDoesNotTouchTheLedger() {
        assertNull(dueReversalEntry(sale(customerId = null, paid = 50_000, due = 0), now))
    }

    @Test
    fun aSaleAndItsCancellationLeaveTheBalanceWhereItWas() {
        val before = listOf(entry(LedgerEntryType.OPENING_DUE, 5_000)) // ৳50 owed from before
        val saleDue = entry(LedgerEntryType.SALE_DUE, 30_000)
        val reversal = dueReversalEntry(sale(due = 30_000), now)!!

        assertEquals(35_000L, balance(before + saleDue))
        assertEquals(5_000L, balance(before + saleDue + reversal)) // back to the old ৳50, not counted twice
    }

    @Test
    fun aLaterPartPaymentMakesTheBalanceNegativeAfterCancelling() {
        // Sale due ৳300, then the customer paid ৳100 of it, then the sale is cancelled.
        val history = listOf(
            entry(LedgerEntryType.SALE_DUE, 30_000),
            entry(LedgerEntryType.PAYMENT_RECEIVED, 10_000)
        )
        val reversal = dueReversalEntry(sale(due = 30_000), now)!!
        assertEquals(20_000L, balance(history))
        assertEquals(-10_000L, balance(history + reversal)) // the shop now holds ৳100 for the customer
    }

    // =====================================================================================
    // What the confirm dialog says
    // =====================================================================================

    @Test
    fun theRefundIsWhatWasPaidAtTheSale() {
        val preview = cancelPreview(sale(paid = 20_000, due = 30_000), currentBalance = 30_000)
        assertEquals(20_000L, preview.refundToCustomer)
        assertEquals(30_000L, preview.dueReversed)
        assertEquals(0L, preview.balanceAfter)
        assertFalse(preview.makesAdvance)
    }

    @Test
    fun theBalanceAfterIncludesOtherDues() {
        val preview = cancelPreview(sale(due = 30_000), currentBalance = 35_000)
        assertEquals(5_000L, preview.balanceAfter)
    }

    @Test
    fun aBalanceGoingBelowZeroIsAnAdvance() {
        val preview = cancelPreview(sale(due = 30_000), currentBalance = 20_000)
        assertEquals(-10_000L, preview.balanceAfter)
        assertTrue(preview.makesAdvance)
    }

    @Test
    fun aFullyPaidSaleWithACustomerLeavesTheBalanceAlone() {
        val preview = cancelPreview(sale(paid = 50_000, due = 0), currentBalance = 8_000)
        assertEquals(50_000L, preview.refundToCustomer)
        assertEquals(0L, preview.dueReversed)
        assertEquals(8_000L, preview.balanceAfter)
    }

    @Test
    fun aSaleWithoutACustomerHasNoBalance() {
        val preview = cancelPreview(sale(customerId = null, paid = 50_000, due = 0), currentBalance = null)
        assertEquals(50_000L, preview.refundToCustomer)
        assertEquals(0L, preview.dueReversed)
        assertNull(preview.balanceAfter)
        assertFalse(preview.makesAdvance)
    }

    @Test
    fun nothingPaidMeansNothingToHandBack() {
        val preview = cancelPreview(sale(paid = 0, due = 50_000), currentBalance = 50_000)
        assertEquals(0L, preview.refundToCustomer)
        assertEquals(50_000L, preview.dueReversed)
        assertEquals(0L, preview.balanceAfter)
    }

    // =====================================================================================
    // The sales list row knows it is cancelled
    // =====================================================================================

    @Test
    fun aListRowIsCancelledOnlyWhenItHasACancelTime() {
        val active = SaleListRow(1, "261110-001", now, 10_000, 0, null, null)
        val cancelled = active.copy(cancelledAt = now + 60_000)
        assertFalse(active.isCancelled)
        assertTrue(cancelled.isCancelled)
    }
}

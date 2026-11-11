package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType
import com.example.dukanhisabnext.util.DhakaTime
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phone tests for cancelling a sale (বিক্রি বাতিল). In-memory database only: the shop data on the
 * phone is never touched. Covers: everything together, a crash saves nothing, only once (also when
 * two cancels race), archived products, negative stock, the balance never counted twice, receipt
 * numbers never reused, the sale row unchanged, and the lists/totals.
 */
@RunWith(AndroidJUnit4::class)
class SaleCancelRepositoryTest : DatabaseTestBase() {

    // Noon in Dhaka on 9 November 2026 (Dhaka is UTC+6).
    private val nov9 = Instant.parse("2026-11-09T06:00:00Z").toEpochMilli()
    private var clockNow = nov9

    private fun repo(afterCancelStep: (CancelStep) -> Unit = {}) =
        SaleRepository(db, { clockNow }, afterCancelStep = afterCancelStep)

    private var nextDraft = 0

    private suspend fun product(name: String, stock: Long = 10_000): Long {
        val id = db.productDao().insertProduct(
            Product(name = name, unit = ProductUnit.KG, sellPrice = 10_000, costPrice = 8_000, createdAt = now, updatedAt = now)
        )
        if (stock != 0L) {
            db.stockMovementDao().insert(
                StockMovement(productId = id, type = StockMovementType.OPENING, quantity = stock, createdAt = now)
            )
        }
        return id
    }

    /** Saves a sale through the real repository and returns its id. */
    private suspend fun sell(
        vararg lines: SaleDraftLine,
        received: Long,
        customerId: Long? = null
    ): SaleResult.Saved {
        val result = repo().completeSale(
            SaleDraft("draft-${nextDraft++}", lines.toList(), 0, 0, received, PaymentMethod.CASH, customerId)
        )
        return result as? SaleResult.Saved ?: throw AssertionError("Expected the sale to be saved, got $result")
    }

    private fun cancelled(result: CancelResult): CancelResult.Cancelled =
        result as? CancelResult.Cancelled ?: throw AssertionError("Expected Cancelled, got $result")

    private suspend fun movementsOf(productId: Long) = db.stockMovementDao().observeForProduct(productId).first()

    // =====================================================================================
    // Everything is saved together
    // =====================================================================================

    @Test
    fun cancelRecordsItReturnsStockAndReversesTheDue() = runBlocking {
        val rice = product("চাল", stock = 10_000)
        val oil = product("তেল", stock = 5_000)
        val karim = addCustomer("করিম")
        // ৳250 + ৳100 = ৳350; ৳150 paid, ৳200 due.
        val sale = sell(SaleDraftLine(rice, 2_500, 10_000), SaleDraftLine(oil, 1_000, 10_000), received = 15_000, customerId = karim)
        assertEquals(20_000L, db.ledgerDao().getBalance(karim))

        clockNow = nov9 + 60_000
        val result = cancelled(repo().cancelSale(sale.saleId, "  ভুল দাম  "))

        // The cancellation record
        val c = db.saleCancellationDao().getForSale(sale.saleId)!!
        assertEquals(result.cancellation, c)
        assertEquals("ভুল দাম", c.reason)
        assertEquals(clockNow, c.createdAt)

        // Stock is back where it was, through positive SALE_CANCEL rows linked to the sale
        assertEquals(10_000L, db.stockMovementDao().getStock(rice))
        assertEquals(5_000L, db.stockMovementDao().getStock(oil))
        val back = movementsOf(rice).single { it.type == StockMovementType.SALE_CANCEL }
        assertEquals(2_500L, back.quantity)
        assertEquals(sale.saleId, back.referenceSaleId)
        assertEquals(clockNow, back.createdAt)

        // The due is taken away by exactly that amount, linked to the sale
        val reversal = db.ledgerDao().observeForCustomer(karim).first().single { it.type == LedgerEntryType.CORRECTION_MINUS }
        assertEquals(20_000L, reversal.amount)
        assertEquals(sale.saleId, reversal.saleId)
        assertEquals(0L, db.ledgerDao().getBalance(karim))
    }

    @Test
    fun aFullyPaidSaleWithACustomerDoesNotTouchTheLedger() = runBlocking {
        val rice = product("চাল")
        val karim = addCustomer("করিম")
        val sale = sell(SaleDraftLine(rice, 1_000, 10_000), received = 10_000, customerId = karim)

        cancelled(repo().cancelSale(sale.saleId, null))

        assertEquals(0, countRows("ledger_entries"))
        assertEquals(10_000L, db.stockMovementDao().getStock(rice))
    }

    @Test
    fun aSaleWithoutACustomerCanBeCancelled() = runBlocking {
        val rice = product("চাল")
        val sale = sell(SaleDraftLine(rice, 3_000, 10_000), received = 30_000)

        val c = cancelled(repo().cancelSale(sale.saleId, "")).cancellation

        assertNull(c.reason) // blank reason is saved as no reason
        assertEquals(0, countRows("ledger_entries"))
        assertEquals(10_000L, db.stockMovementDao().getStock(rice))
    }

    // =====================================================================================
    // A crash partway through saves nothing at all
    // =====================================================================================

    @Test
    fun aCrashAfterAnyStepSavesNothing() = runBlocking {
        val rice = product("চাল", stock = 10_000)
        val karim = addCustomer("করিম")
        val sale = sell(SaleDraftLine(rice, 2_000, 10_000), received = 5_000, customerId = karim) // ৳150 due
        val movementsBefore = countRows("stock_movements")
        val ledgerBefore = countRows("ledger_entries")

        for (crashAt in CancelStep.entries) {
            val crashing = repo { step -> if (step == crashAt) throw IllegalStateException("simulated crash") }
            try {
                crashing.cancelSale(sale.saleId, "test")
                fail("Expected the simulated crash at $crashAt")
            } catch (expected: IllegalStateException) {
                // good
            }
            assertEquals("cancellation kept after crash at $crashAt", 0, countRows("sale_cancellations"))
            assertEquals("stock rows kept after crash at $crashAt", movementsBefore, countRows("stock_movements"))
            assertEquals("ledger rows kept after crash at $crashAt", ledgerBefore, countRows("ledger_entries"))
            assertEquals(8_000L, db.stockMovementDao().getStock(rice))
            assertEquals(15_000L, db.ledgerDao().getBalance(karim))
        }

        // After all those crashes a normal cancel works.
        cancelled(repo().cancelSale(sale.saleId, null))
        assertEquals(10_000L, db.stockMovementDao().getStock(rice))
        assertEquals(0L, db.ledgerDao().getBalance(karim))
    }

    // =====================================================================================
    // Cancel only once
    // =====================================================================================

    @Test
    fun aSecondCancelChangesNothing() = runBlocking {
        val rice = product("চাল", stock = 10_000)
        val karim = addCustomer("করিম")
        val sale = sell(SaleDraftLine(rice, 2_000, 10_000), received = 0, customerId = karim)
        val repo = repo()

        val first = cancelled(repo.cancelSale(sale.saleId, "প্রথম"))
        val second = repo.cancelSale(sale.saleId, "দ্বিতীয়")

        assertEquals(CancelResult.AlreadyCancelled(first.cancellation), second)
        assertEquals(1, countRows("sale_cancellations"))
        assertEquals(10_000L, db.stockMovementDao().getStock(rice)) // not 12 kg
        assertEquals(0L, db.ledgerDao().getBalance(karim))           // not -৳200
        assertEquals("প্রথম", db.saleCancellationDao().getForSale(sale.saleId)!!.reason)
    }

    @Test
    fun doubleTapCancelsOnlyOnce() = runBlocking {
        val rice = product("চাল", stock = 10_000)
        val karim = addCustomer("করিম")
        val sale = sell(SaleDraftLine(rice, 2_000, 10_000), received = 0, customerId = karim)
        val repo = repo()

        val results = (1..5).map { async(Dispatchers.IO) { repo.cancelSale(sale.saleId, null) } }.awaitAll()

        assertEquals(1, results.count { it is CancelResult.Cancelled })
        assertEquals(4, results.count { it is CancelResult.AlreadyCancelled })
        assertOnlyOneCancellation(sale.saleId, rice, karim)
    }

    @Test
    fun twoScreensCancellingAtOnceCancelOnlyOnce() = runBlocking {
        // Two separate repositories do not share the lock: only the database (check + unique index) protects.
        val rice = product("চাল", stock = 10_000)
        val karim = addCustomer("করিম")
        val sale = sell(SaleDraftLine(rice, 2_000, 10_000), received = 0, customerId = karim)

        val results = (1..4).map { async(Dispatchers.IO) { repo().cancelSale(sale.saleId, null) } }.awaitAll()

        assertEquals(1, results.count { it is CancelResult.Cancelled })
        assertEquals(3, results.count { it is CancelResult.AlreadyCancelled })
        assertOnlyOneCancellation(sale.saleId, rice, karim)
    }

    private suspend fun assertOnlyOneCancellation(saleId: Long, productId: Long, customerId: Long) {
        assertEquals(1, countRows("sale_cancellations"))
        assertEquals(1, movementsOf(productId).count { it.type == StockMovementType.SALE_CANCEL })
        assertEquals(10_000L, db.stockMovementDao().getStock(productId))
        assertEquals(0L, db.ledgerDao().getBalance(customerId))
        assertEquals(1, db.ledgerDao().observeForCustomer(customerId).first().count { it.type == LedgerEntryType.CORRECTION_MINUS && it.saleId == saleId })
    }

    @Test
    fun theDatabaseItselfRefusesASecondCancellationRow() = runBlocking {
        val rice = product("চাল")
        val sale = sell(SaleDraftLine(rice, 1_000, 10_000), received = 10_000)
        cancelled(repo().cancelSale(sale.saleId, null))

        assertConstraintFails {
            db.saleCancellationDao().insert(
                com.example.dukanhisabnext.data.entity.SaleCancellation(saleId = sale.saleId, createdAt = now)
            )
        }
    }

    @Test
    fun aCancellationMustPointAtARealSale() = runBlocking {
        assertConstraintFails {
            db.saleCancellationDao().insert(
                com.example.dukanhisabnext.data.entity.SaleCancellation(saleId = 999, createdAt = now)
            )
        }
    }

    // =====================================================================================
    // Stock: archived products and negative stock
    // =====================================================================================

    @Test
    fun stockComesBackEvenForAnArchivedProduct() = runBlocking {
        val rice = product("চাল", stock = 10_000)
        val sale = sell(SaleDraftLine(rice, 4_000, 10_000), received = 40_000)
        db.productDao().setActive(rice, isActive = false, updatedAt = now)

        cancelled(repo().cancelSale(sale.saleId, null))

        assertEquals(10_000L, db.stockMovementDao().getStock(rice))
    }

    @Test
    fun aSaleThatPushedStockBelowZeroGetsExactlyTheSoldQuantityBack() = runBlocking {
        val rice = product("চাল", stock = 1_000)                       // 1 kg
        val sale = sell(SaleDraftLine(rice, 3_000, 10_000), received = 30_000) // sold 3 kg
        assertEquals(-2_000L, db.stockMovementDao().getStock(rice))

        cancelled(repo().cancelSale(sale.saleId, null))

        assertEquals(1_000L, db.stockMovementDao().getStock(rice))
    }

    // =====================================================================================
    // The balance is never counted twice
    // =====================================================================================

    @Test
    fun aLaterPartPaymentLeavesTheCustomerInAdvance() = runBlocking {
        val rice = product("চাল")
        val karim = addCustomer("করিম")
        val sale = sell(SaleDraftLine(rice, 3_000, 10_000), received = 0, customerId = karim) // ৳300 due
        db.ledgerDao().insert(
            LedgerEntry(customerId = karim, type = LedgerEntryType.PAYMENT_RECEIVED, amount = 10_000,
                paymentMethod = PaymentMethod.CASH, entryDate = now, createdAt = now)
        ) // ৳100 paid later
        assertEquals(20_000L, db.ledgerDao().getBalance(karim))

        cancelled(repo().cancelSale(sale.saleId, null))

        assertEquals(-10_000L, db.ledgerDao().getBalance(karim)) // the shop holds ৳100 (অগ্রিম)
        // The total receivable only counts customers who owe; an advance does not make it negative.
        assertEquals(0L, db.ledgerDao().observeTotalReceivable().first())
    }

    @Test
    fun anOlderDueIsKept() = runBlocking {
        val rice = product("চাল")
        val karim = addCustomer("করিম")
        db.ledgerDao().insert(
            LedgerEntry(customerId = karim, type = LedgerEntryType.OPENING_DUE, amount = 5_000, entryDate = now, createdAt = now)
        ) // ৳50 from before the app
        val sale = sell(SaleDraftLine(rice, 2_000, 10_000), received = 0, customerId = karim)
        assertEquals(25_000L, db.ledgerDao().getBalance(karim))

        cancelled(repo().cancelSale(sale.saleId, null))

        assertEquals(5_000L, db.ledgerDao().getBalance(karim))
    }

    // =====================================================================================
    // The sale stays as it was, and its receipt number is never reused
    // =====================================================================================

    @Test
    fun theSaleRowAndItsItemsAreNotChanged() = runBlocking {
        val rice = product("চাল")
        val karim = addCustomer("করিম")
        val sale = sell(SaleDraftLine(rice, 2_000, 10_000), received = 5_000, customerId = karim)
        val before = db.saleDao().getSale(sale.saleId)
        val itemsBefore = db.saleDao().getItems(sale.saleId)

        cancelled(repo().cancelSale(sale.saleId, null))

        assertEquals(before, db.saleDao().getSale(sale.saleId))
        assertEquals(itemsBefore, db.saleDao().getItems(sale.saleId))
        // The SALE_DUE entry is still there too: nothing is deleted, only added.
        assertEquals(1, db.ledgerDao().observeForCustomer(karim).first().count { it.type == LedgerEntryType.SALE_DUE })
    }

    @Test
    fun theReceiptNumberIsNeverReused() = runBlocking {
        val rice = product("চাল")
        val first = sell(SaleDraftLine(rice, 1_000, 10_000), received = 10_000)
        assertEquals("261109-001", first.receiptNo)

        cancelled(repo().cancelSale(first.saleId, null))
        val next = sell(SaleDraftLine(rice, 1_000, 10_000), received = 10_000)

        assertEquals("261109-002", next.receiptNo)
    }

    @Test
    fun aSaleCanBeCancelledLongAfter() = runBlocking {
        val rice = product("চাল")
        val sale = sell(SaleDraftLine(rice, 1_000, 10_000), received = 10_000)

        clockNow = nov9 + 400L * 24 * 60 * 60 * 1000 // more than a year later
        val c = cancelled(repo().cancelSale(sale.saleId, null)).cancellation

        assertEquals(clockNow, c.createdAt)
        assertEquals(nov9, db.saleDao().getSale(sale.saleId)!!.createdAt)
    }

    // =====================================================================================
    // Wrong input
    // =====================================================================================

    @Test
    fun anUnknownSaleIsReported() = runBlocking {
        assertEquals(CancelResult.SaleNotFound, repo().cancelSale(999, null))
        assertEquals(0, countRows("sale_cancellations"))
    }

    @Test
    fun aTooLongReasonIsRefusedAndNothingIsSaved() = runBlocking {
        val rice = product("চাল")
        val sale = sell(SaleDraftLine(rice, 1_000, 10_000), received = 10_000)

        assertEquals(CancelResult.ReasonTooLong, repo().cancelSale(sale.saleId, "ক".repeat(201)))
        assertEquals(0, countRows("sale_cancellations"))
        assertEquals(9_000L, db.stockMovementDao().getStock(rice))

        // Exactly 200 characters is fine.
        val c = cancelled(repo().cancelSale(sale.saleId, "ক".repeat(200))).cancellation
        assertEquals(200, c.reason!!.length)
    }

    // =====================================================================================
    // Lists, totals and the receipt
    // =====================================================================================

    @Test
    fun theListShowsCancelledSalesButTheTotalsLeaveThemOut() = runBlocking {
        val rice = product("চাল", stock = 100_000)
        val kept = sell(SaleDraftLine(rice, 1_000, 10_000), received = 10_000)  // ৳100
        val gone = sell(SaleDraftLine(rice, 2_000, 10_000), received = 20_000)  // ৳200
        clockNow = nov9 + 60_000
        cancelled(repo().cancelSale(gone.saleId, null))

        val today = DhakaTime.today(nov9)
        val rows = repo().observeSalesList(today).first()
        assertEquals(2, rows.size)
        assertEquals(nov9 + 60_000, rows.single { it.id == gone.saleId }.cancelledAt)
        assertTrue(rows.single { it.id == gone.saleId }.isCancelled)
        assertNull(rows.single { it.id == kept.saleId }.cancelledAt)

        val totals = repo().observeActiveTotals(today).first()
        assertEquals(1, totals.count)
        assertEquals(10_000L, totals.total)

        // The plain sale count still counts every sale (it decides if the list is "empty").
        assertEquals(2, repo().observeSalesCount().first())
    }

    @Test
    fun totalsAreZeroWhenEverySaleIsCancelled() = runBlocking {
        val rice = product("চাল")
        val sale = sell(SaleDraftLine(rice, 1_000, 10_000), received = 10_000)
        cancelled(repo().cancelSale(sale.saleId, null))

        val totals = repo().observeActiveTotals(DhakaTime.today(nov9)).first()
        assertEquals(0, totals.count)
        assertEquals(0L, totals.total)
    }

    @Test
    fun aCancelledSaleStillHasItsReceipt() = runBlocking {
        val rice = product("চাল")
        val sale = sell(SaleDraftLine(rice, 1_000, 10_000), received = 10_000)
        cancelled(repo().cancelSale(sale.saleId, null))

        val receipt = ReceiptRepository(db).load(sale.saleId, change = 0)
        assertNotNull(receipt)
        assertEquals(sale.receiptNo, receipt!!.receiptNo)
    }

    @Test
    fun theCancellationCanBeWatched() = runBlocking {
        val rice = product("চাল")
        val sale = sell(SaleDraftLine(rice, 1_000, 10_000), received = 10_000)
        val repo = repo()
        assertNull(repo.observeCancellation(sale.saleId).first())
        assertNull(repo.getCancellation(sale.saleId))

        val c = cancelled(repo.cancelSale(sale.saleId, "x")).cancellation

        assertEquals(c, repo.observeCancellation(sale.saleId).first())
        assertEquals(c, repo.getCancellation(sale.saleId))
    }
}

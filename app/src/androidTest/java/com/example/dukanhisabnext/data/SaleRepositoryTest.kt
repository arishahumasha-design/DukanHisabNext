package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType
import com.example.dukanhisabnext.util.SaleError
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phone tests for saving a sale (M4 step 2). Each test names the requirement it covers:
 *  [1] everything saved together     [2] a crash saves nothing      [3] fast sales, unique numbers
 *  [4] moved-back clock               [5] double tap                 [6] balance changes by the due
 *  [7] archived/missing product       [8] snapshots                  [9] due without a customer
 */
@RunWith(AndroidJUnit4::class)
class SaleRepositoryTest : DatabaseTestBase() {

    // Noon in Dhaka on 9 and 10 November 2026 (Dhaka is UTC+6).
    private val nov9 = Instant.parse("2026-11-09T06:00:00Z").toEpochMilli()
    private val nov10 = Instant.parse("2026-11-10T06:00:00Z").toEpochMilli()

    /** The "phone clock" the repository sees. Tests move it around. */
    private var clockNow = nov9

    private fun repo(afterStep: (SaveStep) -> Unit = {}) = SaleRepository(db, { clockNow }, afterStep)

    private var nextDraft = 0

    private fun draft(
        vararg lines: SaleDraftLine,
        discount: Long = 0,
        delivery: Long = 0,
        received: Long,
        customerId: Long? = null,
        method: PaymentMethod = PaymentMethod.CASH,
        draftId: String = "draft-${nextDraft++}"
    ) = SaleDraft(draftId, lines.toList(), discount, delivery, received, method, customerId)

    /** A product with an opening stock (thousandths). */
    private suspend fun product(name: String, sellPrice: Long, costPrice: Long?, stock: Long = 10_000): Long {
        val id = db.productDao().insertProduct(
            Product(
                name = name, unit = ProductUnit.KG, sellPrice = sellPrice, costPrice = costPrice,
                createdAt = now, updatedAt = now
            )
        )
        if (stock != 0L) {
            db.stockMovementDao().insert(
                StockMovement(productId = id, type = StockMovementType.OPENING, quantity = stock, createdAt = now)
            )
        }
        return id
    }

    private fun saved(result: SaleResult): SaleResult.Saved =
        result as? SaleResult.Saved ?: throw AssertionError("Expected the sale to be saved, got $result")

    /** Nothing about a sale is in the database. */
    private fun assertNoSaleRows() {
        assertEquals("sales", 0, countRows("sales"))
        assertEquals("sale_items", 0, countRows("sale_items"))
        assertEquals("ledger_entries", 0, countRows("ledger_entries"))
    }

    // =====================================================================================
    // [1] Everything is saved together
    // =====================================================================================

    @Test
    fun saleItemsStockAndDueAreAllSaved() = runBlocking {
        val rice = product("চাল", sellPrice = 12_000, costPrice = 10_000, stock = 10_000)
        val oil = product("তেল", sellPrice = 20_000, costPrice = null, stock = 5_000)
        val karim = addCustomer("করিম")

        val result = saved(
            repo().completeSale(
                draft(
                    SaleDraftLine(rice, quantity = 2_500, unitPrice = 12_000), // 2.5 kg = ৳300
                    SaleDraftLine(oil, quantity = 1_000, unitPrice = 20_000),  // 1 kg = ৳200
                    discount = 2_000,  // ৳20
                    delivery = 3_000,  // ৳30
                    received = 30_000, // ৳300 paid, ৳210 due
                    customerId = karim,
                    method = PaymentMethod.BKASH
                )
            )
        )

        // The sale
        val sale = db.saleDao().getSale(result.saleId)!!
        assertEquals("261109-001", sale.receiptNo)
        assertEquals(karim, sale.customerId)
        assertEquals(50_000L, sale.subtotal)
        assertEquals(2_000L, sale.discount)
        assertEquals(3_000L, sale.deliveryCharge)
        assertEquals(51_000L, sale.total)
        assertEquals(30_000L, sale.paidAmount)
        assertEquals(21_000L, sale.dueAmount)
        assertEquals(PaymentMethod.BKASH, sale.paymentMethod)
        assertEquals(nov9, sale.createdAt)

        // The lines
        val items = db.saleDao().getItems(result.saleId)
        assertEquals(listOf(rice, oil), items.map { it.productId })
        assertEquals(listOf(30_000L, 20_000L), items.map { it.lineTotal })

        // Stock went DOWN by negative SALE movements that point at this sale
        assertEquals(7_500L, db.stockMovementDao().getStock(rice))
        assertEquals(4_000L, db.stockMovementDao().getStock(oil))
        val riceSale = db.stockMovementDao().observeForProduct(rice).first().single { it.type == StockMovementType.SALE }
        assertEquals(-2_500L, riceSale.quantity)
        assertEquals(result.saleId, riceSale.referenceSaleId)

        // One ledger entry for the due
        val entry = db.ledgerDao().observeForCustomer(karim).first().single()
        assertEquals(LedgerEntryType.SALE_DUE, entry.type)
        assertEquals(21_000L, entry.amount)
        assertEquals(result.saleId, entry.saleId)
        assertEquals(nov9, entry.entryDate)
    }

    @Test
    fun fullyPaidSaleWritesNoLedgerEntry() = runBlocking {
        val rice = product("চাল", 12_000, 10_000)
        val karim = addCustomer("করিম")

        saved(repo().completeSale(draft(SaleDraftLine(rice, 1_000, 12_000), received = 12_000, customerId = karim)))

        assertEquals(1, countRows("sales"))
        assertEquals(0, countRows("ledger_entries"))
    }

    @Test
    fun stockMayGoNegative() = runBlocking {
        val rice = product("চাল", 12_000, 10_000, stock = 1_000) // only 1 kg on the shelf
        saved(repo().completeSale(draft(SaleDraftLine(rice, 3_000, 12_000), received = 36_000)))
        assertEquals(-2_000L, db.stockMovementDao().getStock(rice))
    }

    // =====================================================================================
    // [2] A crash partway through saves nothing at all
    // =====================================================================================

    @Test
    fun aCrashAfterAnyStepSavesNothing() = runBlocking {
        val rice = product("চাল", 12_000, 10_000, stock = 10_000)
        val karim = addCustomer("করিম")

        for (crashAt in SaveStep.entries) {
            val crashingRepo = repo { step -> if (step == crashAt) throw IllegalStateException("simulated crash") }
            try {
                // A sale WITH a due, so every step (including the ledger) is reached.
                crashingRepo.completeSale(
                    draft(SaleDraftLine(rice, 2_000, 12_000), received = 10_000, customerId = karim)
                )
                fail("Expected the simulated crash at $crashAt")
            } catch (expected: IllegalStateException) {
                // good
            }

            assertNoSaleRows()
            assertEquals("stock changed after crash at $crashAt", 10_000L, db.stockMovementDao().getStock(rice))
            assertEquals("balance changed after crash at $crashAt", 0L, db.ledgerDao().getBalance(karim))
        }

        // After all those crashes a normal save works, and no receipt number was used up.
        val ok = saved(repo().completeSale(draft(SaleDraftLine(rice, 2_000, 12_000), received = 24_000)))
        assertEquals("261109-001", ok.receiptNo)
    }

    // =====================================================================================
    // [3] Sales saved at nearly the same time get different receipt numbers
    // =====================================================================================

    @Test
    fun twentySalesAtOnceGetTwentyDifferentNumbers() = runBlocking {
        val rice = product("চাল", 12_000, 10_000)
        val repository = repo()
        val drafts = (1..20).map { draft(SaleDraftLine(rice, 1_000, 12_000), received = 12_000, draftId = "fast-$it") }

        // Start all 20 saves at the same moment on background threads.
        val results = drafts.map { d -> async(Dispatchers.IO) { repository.completeSale(d) } }.awaitAll()

        val numbers = results.map { saved(it).receiptNo }
        assertEquals(20, numbers.toSet().size)
        // padStart, not String.format: format() follows the phone's language and could give Bengali digits.
        assertEquals((1..20).map { "261109-" + it.toString().padStart(3, '0') }.toSet(), numbers.toSet())
        assertEquals(20, countRows("sales"))
        assertEquals(-10_000L, db.stockMovementDao().getStock(rice)) // 10 kg opening - 20 x 1 kg sold
    }

    @Test
    fun twoSeparateRepositoriesAtOnceStillGetDifferentNumbers() = runBlocking {
        // Even without the shared lock, the database transaction keeps numbers unique.
        val rice = product("চাল", 12_000, 10_000)
        val first = repo()
        val second = repo()

        val results = (1..10).flatMap { i ->
            listOf(
                async(Dispatchers.IO) { first.completeSale(draft(SaleDraftLine(rice, 1_000, 12_000), received = 12_000, draftId = "a-$i")) },
                async(Dispatchers.IO) { second.completeSale(draft(SaleDraftLine(rice, 1_000, 12_000), received = 12_000, draftId = "b-$i")) }
            )
        }.awaitAll()

        assertEquals(20, results.map { saved(it).receiptNo }.toSet().size)
    }

    @Test
    fun counterGoesFrom999To1000InTheDatabase() = runBlocking {
        val rice = product("চাল", 12_000, 10_000)
        db.saleDao().insertSale(sale("261109-999", createdAt = nov9)) // a day that already reached 999

        val r = repo()
        assertEquals("261109-1000", saved(r.completeSale(draft(SaleDraftLine(rice, 1_000, 12_000), received = 12_000))).receiptNo)
        // As text "261109-999" sorts after "261109-1000"; counting as numbers gives 1001.
        assertEquals("261109-1001", saved(r.completeSale(draft(SaleDraftLine(rice, 1_000, 12_000), received = 12_000))).receiptNo)
    }

    // =====================================================================================
    // [4] A moved-back phone clock never reuses a receipt number
    // =====================================================================================

    @Test
    fun movedBackClockNeverReusesANumber() = runBlocking {
        val rice = product("চাল", 12_000, 10_000)
        val r = repo()
        suspend fun sell() = saved(r.completeSale(draft(SaleDraftLine(rice, 1_000, 12_000), received = 12_000))).receiptNo

        clockNow = nov9
        assertEquals("261109-001", sell())
        assertEquals("261109-002", sell())

        clockNow = nov10
        assertEquals("261110-001", sell())

        clockNow = nov9 // the clock is moved back a day
        assertEquals("261109-003", sell())

        clockNow = nov9 - 3 * 60 * 60 * 1000L // ...and back again, earlier on the same day
        assertEquals("261109-004", sell())

        val all = (1L..5L).map { db.saleDao().getSale(it)!!.receiptNo }
        assertEquals(5, all.toSet().size)
    }

    // =====================================================================================
    // [5] Double-tap protection
    // =====================================================================================

    @Test
    fun doubleTapAtTheSameMomentSavesOnce() = runBlocking {
        val rice = product("চাল", 12_000, 10_000, stock = 10_000)
        val r = repo()
        val cart = draft(SaleDraftLine(rice, 2_000, 12_000), received = 24_000, draftId = "same-cart")

        // Two taps at the same moment
        val (tap1, tap2) = listOf(
            async(Dispatchers.IO) { r.completeSale(cart) },
            async(Dispatchers.IO) { r.completeSale(cart) }
        ).awaitAll()

        assertEquals(saved(tap1).saleId, saved(tap2).saleId)
        assertEquals(saved(tap1).receiptNo, saved(tap2).receiptNo)
        assertEquals(1, countRows("sales"))
        assertEquals(1, countRows("sale_items"))
        assertEquals(8_000L, db.stockMovementDao().getStock(rice)) // stock went down ONCE
    }

    @Test
    fun aThirdTapLaterStillSavesNothingNew() = runBlocking {
        val rice = product("চাল", 12_000, 10_000)
        val r = repo()
        val cart = draft(SaleDraftLine(rice, 1_000, 12_000), received = 12_000, draftId = "cart-x")

        val first = saved(r.completeSale(cart))
        val again = saved(r.completeSale(cart))

        assertEquals(first, again)
        assertEquals(1, countRows("sales"))
    }

    @Test
    fun aNewCartWithTheSameItemsIsANewSale() = runBlocking {
        val rice = product("চাল", 12_000, 10_000)
        val r = repo()

        saved(r.completeSale(draft(SaleDraftLine(rice, 1_000, 12_000), received = 12_000, draftId = "cart-1")))
        saved(r.completeSale(draft(SaleDraftLine(rice, 1_000, 12_000), received = 12_000, draftId = "cart-2")))

        assertEquals(2, countRows("sales"))
    }

    @Test
    fun aFailedSaveCanBeTriedAgain() = runBlocking {
        val rice = product("চাল", 12_000, 10_000)
        var crashOnce = true
        val r = repo { if (crashOnce) { crashOnce = false; throw IllegalStateException("simulated crash") } }
        val cart = draft(SaleDraftLine(rice, 1_000, 12_000), received = 12_000, draftId = "retry-cart")

        try {
            r.completeSale(cart)
            fail("Expected the simulated crash")
        } catch (expected: IllegalStateException) {
            // good
        }
        assertEquals(0, countRows("sales"))

        // The failed attempt is not remembered as "saved", so the retry really saves.
        saved(r.completeSale(cart))
        assertEquals(1, countRows("sales"))
    }

    // =====================================================================================
    // [6] The customer's balance changes by exactly the due
    // =====================================================================================

    @Test
    fun balanceGoesUpByExactlyTheDue() = runBlocking {
        val rice = product("চাল", 12_000, 10_000)
        val karim = addCustomer("করিম")
        db.ledgerDao().insert(
            LedgerEntry(customerId = karim, type = LedgerEntryType.MANUAL_DUE, amount = 10_000, entryDate = now, createdAt = now)
        )
        assertEquals(10_000L, db.ledgerDao().getBalance(karim))
        assertEquals(10_000L, db.ledgerDao().observeTotalReceivable().first())

        // Total ৳510, paid ৳300 -> due ৳210
        val result = saved(
            repo().completeSale(
                draft(SaleDraftLine(rice, 4_250, 12_000), received = 30_000, customerId = karim)
            )
        )
        assertEquals(21_000L, result.amounts.dueAmount)

        assertEquals(31_000L, db.ledgerDao().getBalance(karim))                     // 100 + 210
        assertEquals(31_000L, db.ledgerDao().observeTotalReceivable().first())

        // A fully paid sale to the same customer leaves the balance alone.
        saved(repo().completeSale(draft(SaleDraftLine(rice, 1_000, 12_000), received = 12_000, customerId = karim)))
        assertEquals(31_000L, db.ledgerDao().getBalance(karim))
    }

    // =====================================================================================
    // [7] An archived or missing product is refused, and nothing is saved
    // =====================================================================================

    @Test
    fun archivedProductIsRefused() = runBlocking {
        val rice = product("চাল", 12_000, 10_000, stock = 10_000)
        val oil = product("তেল", 20_000, null, stock = 5_000)
        db.productDao().setActive(rice, isActive = false, updatedAt = now)

        val result = repo().completeSale(
            draft(SaleDraftLine(oil, 1_000, 20_000), SaleDraftLine(rice, 1_000, 12_000), received = 32_000)
        )

        assertEquals(SaleResult.ProductsUnavailable(listOf(rice)), result)
        assertNoSaleRows()
        assertEquals(10_000L, db.stockMovementDao().getStock(rice))
        assertEquals(5_000L, db.stockMovementDao().getStock(oil)) // the good line was not sold either
    }

    @Test
    fun missingProductIsRefused() = runBlocking {
        val result = repo().completeSale(draft(SaleDraftLine(productId = 999, quantity = 1_000, unitPrice = 5_000), received = 5_000))

        assertEquals(SaleResult.ProductsUnavailable(listOf(999L)), result)
        assertNoSaleRows()
        assertEquals(0, countRows("stock_movements"))
    }

    @Test
    fun unknownCustomerIsRefused() = runBlocking {
        val rice = product("চাল", 12_000, 10_000)
        val result = repo().completeSale(draft(SaleDraftLine(rice, 1_000, 12_000), received = 0, customerId = 999))

        assertEquals(SaleResult.CustomerNotFound, result)
        assertNoSaleRows()
        assertEquals(10_000L, db.stockMovementDao().getStock(rice))
    }

    // =====================================================================================
    // [8] Snapshots are taken at the moment of sale; later product changes do not touch them
    // =====================================================================================

    @Test
    fun snapshotsKeepTheValuesFromTheMomentOfSale() = runBlocking {
        val rice = product("চাল", sellPrice = 12_000, costPrice = 10_000)

        // The price was edited in the cart: ৳115 instead of ৳120.
        val result = saved(repo().completeSale(draft(SaleDraftLine(rice, 2_000, 11_500), received = 23_000)))

        // The edited price changed only this sale, never the product.
        assertEquals(12_000L, db.productDao().getProduct(rice)!!.sellPrice)

        // Later the product is renamed and its prices change.
        val p = db.productDao().getProduct(rice)!!
        db.productDao().updateProduct(p.copy(name = "মিনিকেট চাল", sellPrice = 13_000, costPrice = 11_000))

        // The old sale still shows what happened at the time.
        val item = db.saleDao().getItems(result.saleId).single()
        assertEquals("চাল", item.productNameSnapshot)
        assertEquals(11_500L, item.unitPrice)
        assertEquals(10_000L, item.costPriceSnapshot)
        assertEquals(23_000L, item.lineTotal)
        assertEquals(23_000L, db.saleDao().getSale(result.saleId)!!.total)
    }

    @Test
    fun unknownCostPriceIsSavedAsEmpty() = runBlocking {
        val oil = product("তেল", sellPrice = 20_000, costPrice = null)
        val result = saved(repo().completeSale(draft(SaleDraftLine(oil, 1_000, 20_000), received = 20_000)))
        assertNull(db.saleDao().getItems(result.saleId).single().costPriceSnapshot)
    }

    // =====================================================================================
    // [9] A sale with a due and no customer is refused
    // =====================================================================================

    @Test
    fun dueWithoutCustomerIsRefused() = runBlocking {
        val rice = product("চাল", 12_000, 10_000, stock = 10_000)

        val result = repo().completeSale(draft(SaleDraftLine(rice, 1_000, 12_000), received = 5_000, customerId = null))

        assertEquals(SaleResult.Invalid(setOf(SaleError.CUSTOMER_REQUIRED)), result)
        assertNoSaleRows()
        assertEquals(10_000L, db.stockMovementDao().getStock(rice))
    }

    // ---- Other rules from the calculator are enforced on save too ----

    @Test
    fun discountAboveSubtotalIsRefused() = runBlocking {
        val rice = product("চাল", 12_000, 10_000)
        val result = repo().completeSale(draft(SaleDraftLine(rice, 1_000, 12_000), discount = 12_001, received = 0))
        assertEquals(SaleResult.Invalid(setOf(SaleError.DISCOUNT_TOO_LARGE)), result)
        assertNoSaleRows()
    }

    @Test
    fun emptyCartIsRefused() = runBlocking {
        val result = repo().completeSale(draft(received = 0))
        assertTrue(result is SaleResult.Invalid)
        assertNoSaleRows()
    }
}

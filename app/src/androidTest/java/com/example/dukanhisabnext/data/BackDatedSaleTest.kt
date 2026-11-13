package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType
import com.example.dukanhisabnext.data.entity.WarrantyUnit
import com.example.dukanhisabnext.util.DhakaTime
import com.example.dukanhisabnext.util.SaleError
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phone tests for version 5: the sale date (a sale may belong to an earlier day than the day it is
 * saved) and the warranty snapshot of each line. The real moment of saving (createdAt) is kept
 * apart from the sale date (saleDate); everything that belongs to the sale uses the sale date.
 */
@RunWith(AndroidJUnit4::class)
class BackDatedSaleTest : DatabaseTestBase() {

    private val nov10 = LocalDate.of(2026, 11, 10)
    private val nov13 = LocalDate.of(2026, 11, 13)

    /** 15:20 in Dhaka on [day]. */
    private fun at(day: LocalDate, hour: Int = 15, minute: Int = 20): Long =
        LocalDateTime.of(day, java.time.LocalTime.of(hour, minute)).atZone(DhakaTime.ZONE).toInstant().toEpochMilli()

    private var clockNow = at(nov13)
    private val journal = InMemorySaleDraftJournal()
    private fun repo() = SaleRepository(db, { clockNow }, journal = journal)

    private var nextDraft = 0
    private fun draft(
        vararg lines: SaleDraftLine,
        received: Long,
        customerId: Long? = null,
        saleDay: LocalDate? = null,
        draftId: String = "draft-${nextDraft++}"
    ) = SaleDraft(draftId, lines.toList(), 0, 0, received, PaymentMethod.CASH, customerId, saleDay = saleDay)

    private suspend fun product(name: String, price: Long = 10_000, cost: Long? = 8_000): Long {
        val id = db.productDao().insertProduct(
            Product(name = name, unit = ProductUnit.PIECE, sellPrice = price, costPrice = cost, createdAt = now, updatedAt = now)
        )
        db.stockMovementDao().insert(StockMovement(productId = id, type = StockMovementType.OPENING, quantity = 50_000, createdAt = now))
        return id
    }

    private fun saved(result: SaleResult): SaleResult.Saved =
        result as? SaleResult.Saved ?: throw AssertionError("Expected the sale to be saved, got $result")

    private fun dayRange(day: LocalDate) = DhakaTime.startOfDay(day) to DhakaTime.startOfDay(day.plusDays(1))

    // =====================================================================================
    // Everything of a back-dated sale is dated by its sale day; createdAt stays the real moment
    // =====================================================================================

    @Test
    fun aBackDatedSaleIsDatedByItsDayEverywhere() = runBlocking {
        val phone = product("ফোন")
        val cable = product("কেবল", price = 5_000)
        val karim = addCustomer("করিম")

        val result = saved(
            repo().completeSale(
                draft(
                    SaleDraftLine(phone, 1_000, 10_000, warrantyValue = 6, warrantyUnit = WarrantyUnit.MONTH),
                    SaleDraftLine(cable, 1_000, 5_000),
                    received = 5_000, customerId = karim, saleDay = nov10
                )
            )
        )

        val sale = db.saleDao().getSale(result.saleId)!!
        assertEquals("the real moment of saving", clockNow, sale.createdAt)
        assertEquals("10 Nov at the time of day of saving", at(nov10), sale.saleDate)
        assertEquals("261110-001", sale.receiptNo)

        // The stock movements of the sale are on its day.
        val movements = db.stockMovementDao().observeForProduct(phone).first().filter { it.type == StockMovementType.SALE }
        assertEquals(listOf(at(nov10)), movements.map { it.createdAt })

        // The due entry is on its day; its createdAt is the real moment.
        val due = db.ledgerDao().observeForCustomer(karim).first().single()
        assertEquals(LedgerEntryType.SALE_DUE, due.type)
        assertEquals(10_000L, due.amount)
        assertEquals(at(nov10), due.entryDate)
        assertEquals(clockNow, due.createdAt)

        // The warranty snapshot: 6 months from 10 Nov 2026 = 10 May 2027. The cable has none.
        val items = db.saleDao().getItems(result.saleId)
        val phoneLine = items.single { it.productId == phone }
        assertEquals(6, phoneLine.warrantyValue)
        assertEquals(WarrantyUnit.MONTH, phoneLine.warrantyUnit)
        assertEquals(LocalDate.of(2027, 5, 10).toEpochDay(), phoneLine.warrantyUntil)
        val cableLine = items.single { it.productId == cable }
        assertNull(cableLine.warrantyValue)
        assertNull(cableLine.warrantyUnit)
        assertNull(cableLine.warrantyUntil)
    }

    @Test
    fun todayIsTheDefaultAndIsTheMomentOfSaving() = runBlocking {
        val phone = product("ফোন")
        val result = saved(repo().completeSale(draft(SaleDraftLine(phone, 1_000, 10_000), received = 10_000)))
        val sale = db.saleDao().getSale(result.saleId)!!
        assertEquals(clockNow, sale.createdAt)
        assertEquals(clockNow, sale.saleDate)
        assertEquals("261113-001", sale.receiptNo)
    }

    @Test
    fun aBackDatedSaleTakesTheNextFreeNumberOfItsOwnDay() = runBlocking {
        val phone = product("ফোন")
        // Two sales made on 10 Nov itself.
        clockNow = at(nov10, 10, 0)
        saved(repo().completeSale(draft(SaleDraftLine(phone, 1_000, 10_000), received = 10_000)))
        clockNow = at(nov10, 20, 0)
        saved(repo().completeSale(draft(SaleDraftLine(phone, 1_000, 10_000), received = 10_000)))
        // Three days later: a forgotten 10 Nov sale, and a normal one.
        clockNow = at(nov13)
        val late = saved(repo().completeSale(draft(SaleDraftLine(phone, 1_000, 10_000), received = 10_000, saleDay = nov10)))
        val today = saved(repo().completeSale(draft(SaleDraftLine(phone, 1_000, 10_000), received = 10_000)))

        assertEquals("261110-003", late.receiptNo)
        assertEquals("261113-001", today.receiptNo)
        assertEquals(4, countRows("sales"))
    }

    // =====================================================================================
    // Refusals save nothing
    // =====================================================================================

    @Test
    fun aFutureDayIsRefusedAndNothingIsSaved() = runBlocking {
        val phone = product("ফোন")
        val result = repo().completeSale(draft(SaleDraftLine(phone, 1_000, 10_000), received = 10_000, saleDay = nov13.plusDays(1)))
        assertEquals(SaleResult.Invalid(setOf(SaleError.FUTURE_DATE)), result)
        assertEquals(0, countRows("sales"))
    }

    @Test
    fun aDayBefore2020IsRefusedAndNothingIsSaved() = runBlocking {
        val phone = product("ফোন")
        val result = repo().completeSale(draft(SaleDraftLine(phone, 1_000, 10_000), received = 10_000, saleDay = LocalDate.of(2019, 12, 31)))
        assertEquals(SaleResult.Invalid(setOf(SaleError.DATE_BEFORE_MIN)), result)
        assertEquals(0, countRows("sales"))
        // 1 Jan 2020 itself is allowed.
        saved(repo().completeSale(draft(SaleDraftLine(phone, 1_000, 10_000), received = 10_000, saleDay = LocalDate.of(2020, 1, 1))))
        Unit
    }

    @Test
    fun aBrokenWarrantyIsRefusedAndNothingIsSaved() = runBlocking {
        val phone = product("ফোন")
        for (line in listOf(
            SaleDraftLine(phone, 1_000, 10_000, warrantyValue = 6, warrantyUnit = null),      // incomplete
            SaleDraftLine(phone, 1_000, 10_000, warrantyValue = 121, warrantyUnit = WarrantyUnit.MONTH), // too long
            SaleDraftLine(phone, 1_000, 10_000, warrantyValue = 0, warrantyUnit = WarrantyUnit.DAY)      // zero
        )) {
            assertEquals(SaleResult.Invalid(setOf(SaleError.WARRANTY_INVALID)), repo().completeSale(draft(line, received = 10_000)))
        }
        assertEquals(0, countRows("sales"))
        assertEquals(0, countRows("sale_items"))
    }

    // =====================================================================================
    // A snapshot is a snapshot
    // =====================================================================================

    @Test
    fun changingTheProductWarrantyLaterNeverChangesAnOldSale() = runBlocking {
        val phoneId = product("ফোন")
        val phone = db.productDao().getProduct(phoneId)!!
        db.productDao().updateProduct(phone.copy(warrantyValue = 1, warrantyUnit = WarrantyUnit.YEAR))
        val result = saved(
            repo().completeSale(draft(SaleDraftLine(phoneId, 1_000, 10_000, warrantyValue = 1, warrantyUnit = WarrantyUnit.YEAR), received = 10_000))
        )
        // The product's default changes, then goes away.
        db.productDao().updateProduct(db.productDao().getProduct(phoneId)!!.copy(warrantyValue = 6, warrantyUnit = WarrantyUnit.MONTH))
        db.productDao().updateProduct(db.productDao().getProduct(phoneId)!!.copy(warrantyValue = null, warrantyUnit = null))

        val line = db.saleDao().getItems(result.saleId).single()
        assertEquals(1, line.warrantyValue)
        assertEquals(WarrantyUnit.YEAR, line.warrantyUnit)
        assertEquals(LocalDate.of(2027, 11, 13).toEpochDay(), line.warrantyUntil)
    }

    // =====================================================================================
    // Lists and reports put a back-dated sale in its own period
    // =====================================================================================

    @Test
    fun listsAndReportsUseTheSaleDate() = runBlocking {
        val phone = product("ফোন", price = 10_000, cost = 8_000)
        val late = saved(repo().completeSale(draft(SaleDraftLine(phone, 1_000, 10_000), received = 10_000, saleDay = nov10)))
        val (nov10Start, nov10End) = dayRange(nov10)
        val (todayStart, todayEnd) = dayRange(nov13)

        // The report and the totals of 10 Nov include it; today's do not (Home "today" is empty).
        assertEquals(1, db.reportDao().observeSalesReport(nov10Start, nov10End).first().count)
        assertEquals(10_000L, db.reportDao().observeSalesReport(nov10Start, nov10End).first().total)
        assertEquals(0, db.reportDao().observeSalesReport(todayStart, todayEnd).first().count)
        assertEquals(1, db.saleDao().observeActiveTotals(nov10Start, nov10End).first().count)
        assertEquals(0, db.saleDao().observeActiveTotals(todayStart, todayEnd).first().count)
        assertEquals(8_000L, db.reportDao().observeCostOfGoods(nov10Start, nov10End).first().cost)
        assertEquals(0L, db.reportDao().observeCostOfGoods(todayStart, todayEnd).first().cost)

        // The sales list of 10 Nov shows it, with its sale date; today's list is empty.
        val row = db.saleDao().observeList(nov10Start, nov10End).first().single()
        assertEquals(late.saleId, row.id)
        assertEquals(at(nov10), row.saleDate)
        assertEquals(clockNow, row.createdAt)
        assertTrue(db.saleDao().observeList(todayStart, todayEnd).first().isEmpty())
        assertEquals(1, db.saleDao().observeInRange(nov10Start, nov10End).first().size)
    }

    @Test
    fun theSalesListIsSortedBySaleDateNotByTheMomentOfSaving() = runBlocking {
        val phone = product("ফোন")
        clockNow = at(nov10, 12, 0)
        val first = saved(repo().completeSale(draft(SaleDraftLine(phone, 1_000, 10_000), received = 10_000)))
        clockNow = at(nov13)
        val backDated = saved(repo().completeSale(draft(SaleDraftLine(phone, 1_000, 10_000), received = 10_000, saleDay = LocalDate.of(2026, 11, 9))))
        val ids = db.saleDao().observeList(0, Long.MAX_VALUE).first().map { it.id }
        // Newest sale DATE first: the 10 Nov sale comes before the (later saved) 9 Nov sale.
        assertEquals(listOf(first.saleId, backDated.saleId), ids)
    }

    // =====================================================================================
    // Cancelling a back-dated sale
    // =====================================================================================

    @Test
    fun cancellingABackDatedSaleIsDatedNowAndTakesItOutOfItsPeriod() = runBlocking {
        val phone = product("ফোন")
        val karim = addCustomer("করিম")
        val sale = saved(repo().completeSale(draft(SaleDraftLine(phone, 1_000, 10_000), received = 4_000, customerId = karim, saleDay = nov10)))

        clockNow = at(nov13, 18, 0)
        assertTrue(repo().cancelSale(sale.saleId, null) is CancelResult.Cancelled)

        // The reversal and the stock coming back are dated by the moment of cancelling.
        val reversal = db.ledgerDao().observeForCustomer(karim).first().single { it.type == LedgerEntryType.CORRECTION_MINUS }
        assertEquals(6_000L, reversal.amount)
        assertEquals(clockNow, reversal.entryDate)
        val back = db.stockMovementDao().observeForProduct(phone).first().single { it.type == StockMovementType.SALE_CANCEL }
        assertEquals(clockNow, back.createdAt)
        assertEquals(0L, db.ledgerDao().getBalance(karim))

        // The sale leaves every total of its own day.
        val (start, end) = dayRange(nov10)
        assertEquals(0, db.reportDao().observeSalesReport(start, end).first().count)
        assertEquals(1, db.reportDao().observeSalesReport(start, end).first().cancelledCount)
        assertEquals(0, db.saleDao().observeActiveTotals(start, end).first().count)
    }

    // =====================================================================================
    // Double tap and crash recovery still work for a back-dated sale
    // =====================================================================================

    @Test
    fun aRetryAfterARestartFindsTheBackDatedSaleAndSavesNothingNew() = runBlocking {
        val phone = product("ফোন")
        val cart = draft(SaleDraftLine(phone, 1_000, 10_000), received = 10_000, saleDay = nov10, draftId = "cart-1")
        val first = saved(repo().completeSale(cart))
        // A new repository = the app was restarted: its memory is empty, the journal remembers.
        val again = saved(repo().completeSale(cart))
        assertEquals(first.saleId, again.saleId)
        assertEquals(first.receiptNo, again.receiptNo)
        assertEquals(1, countRows("sales"))
    }

    @Test
    fun aDoubleTapSavesABackDatedSaleOnce() = runBlocking {
        val phone = product("ফোন")
        val repository = repo()
        val cart = draft(SaleDraftLine(phone, 1_000, 10_000), received = 10_000, saleDay = nov10, draftId = "cart-2")
        val a = saved(repository.completeSale(cart))
        val b = saved(repository.completeSale(cart))
        assertEquals(a.saleId, b.saleId)
        assertEquals(1, countRows("sales"))
        assertEquals(1, countRows("sale_items"))
    }
}

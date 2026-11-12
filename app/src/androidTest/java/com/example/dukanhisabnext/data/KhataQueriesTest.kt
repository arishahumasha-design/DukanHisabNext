package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType
import com.example.dukanhisabnext.ledger.KhataPeriod
import com.example.dukanhisabnext.ledger.khataAllSummary
import com.example.dukanhisabnext.ledger.periodRange
import com.example.dukanhisabnext.ledger.periodSummary
import com.example.dukanhisabnext.ledger.signedAmount
import com.example.dukanhisabnext.util.DhakaTime
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phone tests for the two read-only Khata queries (M5b): the "সব" customer list (balance and the
 * newest entry date, every customer, one row each) and the entries of a time window (all
 * customers, with name, mobile, receipt and cancelled flag). In-memory database only.
 */
@RunWith(AndroidJUnit4::class)
class KhataQueriesTest : DatabaseTestBase() {

    // 14:30 on 12 Nov 2026 in Dhaka.
    private val nov12 = Instant.parse("2026-11-12T08:30:00Z").toEpochMilli()
    private val day = 24L * 60 * 60 * 1000

    private suspend fun add(
        customerId: Long,
        type: LedgerEntryType,
        amount: Long,
        entryDate: Long,
        createdAt: Long = entryDate,
        saleId: Long? = null
    ): LedgerEntry {
        val e = LedgerEntry(
            customerId = customerId, type = type, amount = amount,
            paymentMethod = if (type == LedgerEntryType.PAYMENT_RECEIVED) PaymentMethod.CASH else null,
            saleId = saleId, entryDate = entryDate, createdAt = createdAt
        )
        return e.copy(id = db.ledgerDao().insert(e))
    }

    // =====================================================================================
    // The "সব" list
    // =====================================================================================

    @Test
    fun everyCustomerIsListedOnceWithTheSharedBalance() = runBlocking {
        val karim = addCustomer("করিম", "01711000000")
        val rahim = addCustomer("রহিম")
        val alam = addCustomer("আলম") // no entries at all
        add(karim, LedgerEntryType.SALE_DUE, 30_000, nov12 - 2 * day)
        add(karim, LedgerEntryType.PAYMENT_RECEIVED, 10_000, nov12 - day)
        add(karim, LedgerEntryType.MANUAL_DUE, 5_000, nov12)
        add(rahim, LedgerEntryType.PAYMENT_RECEIVED, 7_000, nov12)
        add(rahim, LedgerEntryType.ADVANCE_RETURNED, 2_000, nov12)

        val rows = db.customerDao().observeKhataCustomers().first().associateBy { it.customer.id }

        assertEquals(3, rows.size) // one row per customer, also with many entries
        for (id in listOf(karim, rahim, alam)) {
            assertEquals("customer $id", db.ledgerDao().getBalance(id), rows.getValue(id).balance)
        }
        assertEquals(25_000L, rows.getValue(karim).balance)
        assertEquals(-5_000L, rows.getValue(rahim).balance)
        assertEquals(0L, rows.getValue(alam).balance)
        assertNull(rows.getValue(alam).lastEntryDate)
        assertEquals("01711000000", rows.getValue(karim).customer.phone)
    }

    @Test
    fun theLastDateIsTheNewestEntryDateNotTheNewestSaveTime() = runBlocking {
        val karim = addCustomer("করিম")
        add(karim, LedgerEntryType.MANUAL_DUE, 1_000, entryDate = nov12 - 3 * day)
        // A back-dated opening due, written today for a day long ago: it must not become "the last date".
        add(karim, LedgerEntryType.OPENING_DUE, 40_000, entryDate = nov12 - 300 * day, createdAt = nov12)

        val row = db.customerDao().observeKhataCustomers().first().single()
        assertEquals(nov12 - 3 * day, row.lastEntryDate)
    }

    @Test
    fun theAllSummaryMatchesTheTotalReceivableQuery() = runBlocking {
        val karim = addCustomer("করিম")
        val rahim = addCustomer("রহিম")
        val salma = addCustomer("সালমা")
        add(karim, LedgerEntryType.SALE_DUE, 30_000, nov12)
        add(rahim, LedgerEntryType.PAYMENT_RECEIVED, 7_000, nov12)
        add(salma, LedgerEntryType.MANUAL_DUE, 4_000, nov12)

        val summary = khataAllSummary(db.customerDao().observeKhataCustomers().first())
        assertEquals(db.ledgerDao().observeTotalReceivable().first(), summary.receivable)
        assertEquals(34_000L, summary.receivable)
        assertEquals(7_000L, summary.advance)
        assertEquals(27_000L, summary.net)
    }

    @Test
    fun theListFollowsNewEntriesLive() = runBlocking {
        val karim = addCustomer("করিম")
        assertEquals(0L, db.customerDao().observeKhataCustomers().first().single().balance)
        add(karim, LedgerEntryType.MANUAL_DUE, 9_000, nov12)
        val row = db.customerDao().observeKhataCustomers().first().single()
        assertEquals(9_000L, row.balance)
        assertEquals(nov12, row.lastEntryDate)
    }

    // =====================================================================================
    // The entries of a time window
    // =====================================================================================

    @Test
    fun theWindowIncludesItsStartAndExcludesItsEnd() = runBlocking {
        val karim = addCustomer("করিম")
        val range = periodRange(KhataPeriod.TODAY, nov12)!!
        add(karim, LedgerEntryType.MANUAL_DUE, 1, range.startMillis - 1)           // yesterday 23:59:59.999
        val first = add(karim, LedgerEntryType.MANUAL_DUE, 2, range.startMillis)   // today 00:00
        val last = add(karim, LedgerEntryType.MANUAL_DUE, 3, range.endMillisExclusive - 1)
        add(karim, LedgerEntryType.MANUAL_DUE, 4, range.endMillisExclusive)        // tomorrow 00:00

        val rows = db.ledgerDao().observeEntriesInRange(range.startMillis, range.endMillisExclusive).first()
        assertEquals(listOf(last.id, first.id), rows.map { it.entry.id }) // newest first
    }

    @Test
    fun entriesOfAllCustomersComeWithNameAndMobile() = runBlocking {
        val karim = addCustomer("করিম", "01711000000")
        val rahim = addCustomer("রহিম")
        add(karim, LedgerEntryType.MANUAL_DUE, 5_000, nov12 - 60_000)
        add(rahim, LedgerEntryType.PAYMENT_RECEIVED, 2_000, nov12)

        val range = periodRange(KhataPeriod.TODAY, nov12)!!
        val rows = LedgerRepository(db).observeEntriesInRange(range).first()

        assertEquals(listOf("রহিম", "করিম"), rows.map { it.customerName })
        assertEquals(listOf(null, "01711000000"), rows.map { it.customerPhone })
        assertTrue(rows.all { it.receiptNo == null && !it.saleCancelled })
    }

    @Test
    fun theOrderIsEntryDateThenSaveTimeThenId() = runBlocking {
        val karim = addCustomer("করিম")
        val a = add(karim, LedgerEntryType.MANUAL_DUE, 1, entryDate = nov12, createdAt = nov12)
        val b = add(karim, LedgerEntryType.MANUAL_DUE, 2, entryDate = nov12, createdAt = nov12 + 1_000)
        val c = add(karim, LedgerEntryType.MANUAL_DUE, 3, entryDate = nov12, createdAt = nov12)
        val d = add(karim, LedgerEntryType.MANUAL_DUE, 4, entryDate = nov12 + 5_000, createdAt = nov12)

        val range = periodRange(KhataPeriod.TODAY, nov12)!!
        val ids = db.ledgerDao().observeEntriesInRange(range.startMillis, range.endMillisExclusive).first().map { it.entry.id }
        assertEquals(listOf(d.id, b.id, c.id, a.id), ids)
    }

    @Test
    fun saleRowsKnowTheirReceiptAndWhetherTheSaleWasCancelled() = runBlocking {
        val karim = addCustomer("করিম")
        val rice = db.productDao().insertProduct(
            Product(name = "চাল", unit = ProductUnit.KG, sellPrice = 10_000, createdAt = now, updatedAt = now)
        )
        db.stockMovementDao().insert(StockMovement(productId = rice, type = StockMovementType.OPENING, quantity = 10_000, createdAt = now))
        var clock = nov12
        val sales = SaleRepository(db, { clock })
        val sale = sales.completeSale(
            SaleDraft("d1", listOf(SaleDraftLine(rice, 2_000, 10_000)), 0, 0, 0, PaymentMethod.CASH, karim)
        ) as SaleResult.Saved
        clock += 60_000
        sales.cancelSale(sale.saleId, null)

        val range = periodRange(KhataPeriod.TODAY, nov12)!!
        val rows = db.ledgerDao().observeEntriesInRange(range.startMillis, range.endMillisExclusive).first()

        assertEquals(2, rows.size)
        assertTrue(rows.all { it.receiptNo == sale.receiptNo && it.saleCancelled })
        assertEquals(LedgerEntryType.CORRECTION_MINUS, rows.first().entry.type) // the reversal is newer
        assertEquals(rows.first().entry, rows.first().asHistoryRow().entry)
    }

    @Test
    fun aPeriodCoveringEverythingHasANetEqualToTheSumOfAllBalances() = runBlocking {
        val karim = addCustomer("করিম")
        val rahim = addCustomer("রহিম")
        val entries = listOf(
            add(karim, LedgerEntryType.OPENING_DUE, 40_000, nov12 - 100 * day),
            add(karim, LedgerEntryType.SALE_DUE, 30_000, nov12 - 5 * day, saleId = null),
            add(karim, LedgerEntryType.PAYMENT_RECEIVED, 50_000, nov12 - 2 * day),
            add(rahim, LedgerEntryType.MANUAL_DUE, 8_000, nov12),
            add(rahim, LedgerEntryType.CORRECTION_MINUS, 1_000, nov12),
            add(rahim, LedgerEntryType.ADVANCE_RETURNED, 500, nov12)
        )
        val all = DhakaTime.TimeRange(0L, Long.MAX_VALUE)
        val rows = db.ledgerDao().observeEntriesInRange(all.startMillis, all.endMillisExclusive).first()
        val summary = periodSummary(rows.map { it.entry })

        assertEquals(entries.sumOf { signedAmount(it) }, summary.net)
        assertEquals(db.ledgerDao().getBalance(karim) + db.ledgerDao().getBalance(rahim), summary.net)
        assertEquals(summary.net, summary.linesTotal)
    }

    @Test
    fun theSevenDayWindowLeavesOutTheEighthDay() = runBlocking {
        val karim = addCustomer("করিম")
        val range = periodRange(KhataPeriod.LAST_7_DAYS, nov12)!!
        add(karim, LedgerEntryType.MANUAL_DUE, 1, nov12 - 6 * day) // 6 Nov: inside
        add(karim, LedgerEntryType.MANUAL_DUE, 2, nov12 - 7 * day) // 5 Nov: outside
        val rows = db.ledgerDao().observeEntriesInRange(range.startMillis, range.endMillisExclusive).first()
        assertEquals(listOf(1L), rows.map { it.entry.amount })
        assertFalse(rows.any { it.entry.amount == 2L })
    }
}

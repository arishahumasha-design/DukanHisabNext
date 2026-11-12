package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.dao.SaleListRow
import com.example.dukanhisabnext.ui.sales.SALES_PAGE_SIZE
import com.example.dukanhisabnext.ui.sales.buildSalesPageUi
import com.example.dukanhisabnext.ui.sales.filterSales
import com.example.dukanhisabnext.ui.sales.salesSearchParams
import com.example.dukanhisabnext.ui.sales.salesSummary
import com.example.dukanhisabnext.ui.sales.toSalesSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The capped sales list (M9): only the newest page of rows is loaded, the search runs in SQL, and the
 * count and total line covers the WHOLE window and search (from the summary query, never from the
 * capped rows). The SQL search must find exactly what the Kotlin matchesSalesQuery() finds.
 */
@RunWith(AndroidJUnit4::class)
class SalesListPageTest : DatabaseTestBase() {

    private val start = 10_000_000L
    private val count = 700
    private val end = start + count * 1_000L

    @Before
    fun fill() {
        val w = db.openHelper.writableDatabase
        w.execSQL("INSERT INTO customers (id, name, phone, createdAt, updatedAt) VALUES (1, 'করিম', '01711000001', 1, 1)")
        w.execSQL("INSERT INTO customers (id, name, phone, createdAt, updatedAt) VALUES (2, 'Rahim Store', '01811000002', 1, 1)")
        w.execSQL("INSERT INTO customers (id, name, phone, createdAt, updatedAt) VALUES (3, '50%_off', '01911000003', 1, 1)")
        w.execSQL("INSERT INTO customers (id, name, phone, createdAt, updatedAt) VALUES (4, 'a_b', NULL, 1, 1)")
        w.beginTransaction()
        try {
            // 700 sales in the window, one per second; plus 5 before and 5 after it.
            for (i in -5 until count + 5) {
                val id = i + 6L
                val customer = when (i.mod(5)) { 0 -> "NULL"; else -> (i.mod(5)).toString() }
                val receipt = "261112-" + (id).toString().padStart(3, '0')
                w.execSQL(
                    "INSERT INTO sales (id, receiptNo, customerId, subtotal, discount, deliveryCharge, total, paidAmount, paymentMethod, dueAmount, note, createdAt) " +
                        "VALUES ($id, '$receipt', $customer, ${1_000 + i}, 0, 0, ${1_000 + i}, ${1_000 + i}, 'CASH', 0, NULL, ${start + i * 1_000L})"
                )
                if (i >= 0 && i % 37 == 0) w.execSQL("INSERT INTO sale_cancellations (saleId, reason, createdAt) VALUES ($id, NULL, ${start + i * 1_000L + 1})")
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    private suspend fun everyRowInWindow(): List<SaleListRow> = db.saleDao().observeList(start, end).first()

    private suspend fun page(query: String, limit: Int): List<SaleListRow> {
        val p = salesSearchParams(query)
        return db.saleDao().observeListPage(start, end, p.query, p.compactQuery, p.digits, p.digitsWithout88, limit).first()
    }

    private suspend fun summary(query: String) = salesSearchParams(query).let { p ->
        db.saleDao().observeListSummary(start, end, p.query, p.compactQuery, p.digits, p.digitsWithout88).first().toSalesSummary()
    }

    @Test
    fun aPageIsTheNewestRowsInTheSameOrderAsTheFullList() = runBlocking {
        val all = everyRowInWindow()
        assertEquals(count, all.size)
        val firstPage = page("", SALES_PAGE_SIZE)
        assertEquals(SALES_PAGE_SIZE, firstPage.size)
        assertEquals(all.take(SALES_PAGE_SIZE), firstPage)
        assertEquals(all.take(2 * SALES_PAGE_SIZE), page("", 2 * SALES_PAGE_SIZE))
        assertEquals(all, page("", 10_000))
    }

    @Test
    fun theSummaryCoversTheWholeWindowNotTheCappedRows() = runBlocking {
        val all = everyRowInWindow()
        val whole = summary("")
        assertEquals(salesSummary(all), whole)
        assertTrue(whole.cancelledExcluded > 0)
        // The first page alone would give other numbers: the line must not come from it.
        val firstPage = page("", SALES_PAGE_SIZE)
        assertNotEquals(salesSummary(firstPage), whole)

        val ui = buildSalesPageUi(firstPage, whole, totalSalesInDb = count + 10)
        assertEquals(whole, ui.summary)
        assertTrue(ui.hasMore)
        assertFalse(buildSalesPageUi(page("", 10_000), whole, count + 10).hasMore)
    }

    @Test
    fun theSummaryAgreesWithTheReportTotals() = runBlocking {
        // The shared NOT_CANCELLED_SQL: the same count and total as observeActiveTotals.
        val totals = db.saleDao().observeActiveTotals(start, end).first()
        val whole = summary("")
        assertEquals(totals.count, whole.count)
        assertEquals(totals.total, whole.total)
    }

    @Test
    fun theSqlSearchFindsExactlyWhatTheKotlinSearchFinds() = runBlocking {
        val all = everyRowInWindow()
        val queries = listOf(
            "", "  ", "করিম", "rahim", "RAHIM STORE", "store", "01711", "০১৭১১", "+8801811", "8801911", "88",
            "261112-00", "26111200", "2611 1200", "-", "11", "%", "_", "50%", "%_o", "a_b", "a%b", "zzz", "\\"
        )
        for (q in queries) {
            val expected = filterSales(all, q)
            val found = page(q, 10_000)
            assertEquals("query '$q'", expected.map { it.id }, found.map { it.id })
            assertEquals("summary of '$q'", salesSummary(expected), summary(q))
        }
    }

    @Test
    fun wildcardsInTheSearchAreLiteral() = runBlocking {
        // "%" must not match every sale, "_" must not match any one character.
        val percent = page("%", 10_000)
        assertTrue(percent.isNotEmpty())
        assertTrue(percent.all { it.customerName == "50%_off" })
        val underscore = page("_", 10_000)
        assertTrue(underscore.all { it.customerName == "50%_off" || it.customerName == "a_b" })
        assertTrue(page("a%b", 10_000).isEmpty())
    }
}

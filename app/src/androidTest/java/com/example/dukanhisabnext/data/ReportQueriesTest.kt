package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.Expense
import com.example.dukanhisabnext.data.entity.ExpenseCategory
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.data.entity.SaleCancellation
import com.example.dukanhisabnext.data.entity.SaleItem
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType
import com.example.dukanhisabnext.expense.expenseSummary
import com.example.dukanhisabnext.ledger.khataAllSummary
import com.example.dukanhisabnext.ledger.periodSummary
import com.example.dukanhisabnext.ledger.signedAmount
import com.example.dukanhisabnext.report.costOfGoods
import com.example.dukanhisabnext.report.salesReportFrom
import com.example.dukanhisabnext.ui.products.StockStatus
import com.example.dukanhisabnext.ui.products.stockStatus
import com.example.dukanhisabnext.ui.sales.salesSummary
import com.example.dukanhisabnext.util.DhakaTime
import com.example.dukanhisabnext.util.SaleCalculator
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phone tests for the M7 report queries, and EVERY invariant of the plan: each report number equals
 * the number the matching screen already shows, because both come from the same shared condition
 * (NOT_CANCELLED_SQL, ACTIVE_EXPENSE_SQL, SHOP_EXPENSE_SQL, BALANCE_SUM_SQL / signedAmount), and the
 * cost SQL gives exactly SaleCalculator.lineTotal per line. In-memory database only.
 */
@RunWith(AndroidJUnit4::class)
class ReportQueriesTest : DatabaseTestBase() {

    // 14:30 on 12 Nov 2026 in Dhaka.
    private val nov12 = Instant.parse("2026-11-12T08:30:00Z").toEpochMilli()
    private val today = DhakaTime.today(nov12)
    private val day = 24L * 60 * 60 * 1000

    private fun reports() = ReportRepository(db)

    private var nextReceipt = 1

    /** A product with a given unit and opening stock (thousandths). */
    private suspend fun product(name: String, stock: Long = 0, lowLevel: Long? = null, active: Boolean = true): Long {
        val id = db.productDao().insertProduct(
            Product(name = name, unit = ProductUnit.KG, sellPrice = 1_000, lowStockLevel = lowLevel, isActive = active, createdAt = now, updatedAt = now)
        )
        if (stock != 0L) db.stockMovementDao().insert(StockMovement(productId = id, type = StockMovementType.OPENING, quantity = stock, createdAt = now))
        return id
    }

    /**
     * Saves a sale straight into the tables (so the cost snapshots can be anything), with its items.
     * [lines] = (quantity, unitPrice, costPrice?). Returns the sale with its id.
     */
    private suspend fun rawSale(
        productId: Long,
        lines: List<Triple<Long, Long, Long?>>,
        createdAt: Long = nov12,
        discount: Long = 0,
        delivery: Long = 0,
        paid: Long? = null,
        customerId: Long? = null
    ): Pair<Sale, List<SaleItem>> {
        val lineTotals = lines.map { (q, p, _) -> SaleCalculator.lineTotal(q, p) }
        val subtotal = lineTotals.sum()
        val total = subtotal - discount + delivery
        val paidAmount = paid ?: total
        val sale = Sale(
            receiptNo = "261112-%03d".format(nextReceipt++), customerId = customerId,
            subtotal = subtotal, discount = discount, deliveryCharge = delivery, total = total,
            paidAmount = paidAmount, paymentMethod = PaymentMethod.CASH, dueAmount = total - paidAmount, createdAt = createdAt
        )
        val saleId = db.saleDao().insertSale(sale)
        val items = lines.mapIndexed { i, (q, p, c) ->
            SaleItem(saleId = saleId, productId = productId, productNameSnapshot = "পণ্য", quantity = q, unitPrice = p,
                costPriceSnapshot = c, lineTotal = lineTotals[i])
        }
        db.saleDao().insertItems(items)
        return sale.copy(id = saleId) to db.saleDao().getItems(saleId)
    }

    private suspend fun cancel(saleId: Long, at: Long = nov12 + 60_000) {
        db.saleCancellationDao().insert(SaleCancellation(saleId = saleId, reason = null, createdAt = at))
    }

    // =====================================================================================
    // The cost SQL equals Kotlin lineTotal on tricky values
    // =====================================================================================

    @Test
    fun theCostSqlIsExactlyLineTotalPerLineOnTrickyValues() = runBlocking {
        val rice = product("চাল")
        val tricky = listOf(
            Triple(333L, 10_001L, 10_001L),        // 3330.333 -> 3330
            Triple(500L, 1L, 1L),                  // 0.5 -> 1 (half goes up)
            Triple(500L, 1L, 1L),                  // again: rounded per line, so 1 + 1 (not 1 for the sum)
            Triple(1L, 499L, 499L),                // 0.499 -> 0
            Triple(1L, 500L, 500L),                // 0.5 -> 1
            Triple(1L, 501L, 501L),                // 0.501 -> 1
            Triple(2_500L, 4_000L, 3_500L),        // exact
            Triple(1_234_567L, 98_765L, 87_654L),  // big and not round
            Triple(999_999L, 999_999L, 999_999L),  // almost ৳10,000 x almost 1,000 kg
            Triple(1_000L, 0L, 0L),                // a free item
            Triple(7L, 3L, null)                   // no cost price
        )
        val (_, items) = rawSale(rice, tricky)

        val sql = reports().observeCostOfGoods(today).first()
        val kotlin = costOfGoods(items)

        assertEquals(kotlin, sql)
        assertEquals(items.sumOf { if (it.costPriceSnapshot == null) 0L else SaleCalculator.lineTotal(it.quantity, it.costPriceSnapshot!!) }, sql.cost)
        assertEquals(11, sql.lineCount)
        assertEquals(1, sql.missingCostLines)
        assertEquals(SaleCalculator.lineTotal(7, 3), sql.missingCostSales)
    }

    @Test
    fun eachTrickyLineAloneGivesTheSameCostInSqlAndKotlin() = runBlocking {
        val rice = product("চাল")
        val cases = listOf(333L to 10_001L, 500L to 1L, 1L to 499L, 1L to 500L, 1L to 501L, 1_234_567L to 98_765L, 999_999L to 999_999L)
        cases.forEachIndexed { i, (q, c) ->
            val at = nov12 - (i + 1) * day // each sale on its own Dhaka day
            rawSale(rice, listOf(Triple(q, c, c)), createdAt = at)
            val sql = reports().observeCostOfGoods(DhakaTime.today(at)).first()
            assertEquals("$q x $c", SaleCalculator.lineTotal(q, c), sql.cost)
        }
    }

    @Test
    fun aCancelledSalesLinesAreNotACost() = runBlocking {
        val rice = product("চাল")
        rawSale(rice, listOf(Triple(1_000L, 10_000L, 8_000L)))
        val (gone, _) = rawSale(rice, listOf(Triple(5_000L, 10_000L, 8_000L), Triple(1_000L, 2_000L, null)))
        cancel(gone.id)

        val c = reports().observeCostOfGoods(today).first()
        assertEquals(8_000L, c.cost)
        assertEquals(1, c.lineCount)
        assertEquals(0, c.missingCostLines)
    }

    // =====================================================================================
    // Sales: report = sales list totals = Kotlin twin
    // =====================================================================================

    @Test
    fun theSalesReportEqualsTheSalesListAndTheTwinAndLeavesCancelledOut() = runBlocking {
        val rice = product("চাল")
        val karim = addCustomer("করিম")
        val a = rawSale(rice, listOf(Triple(2_500L, 4_000L, 3_500L)), discount = 500, delivery = 3_000).first
        val b = rawSale(rice, listOf(Triple(1_000L, 20_000L, null)), paid = 5_000, customerId = karim).first
        val c = rawSale(rice, listOf(Triple(3_000L, 9_000L, 8_000L)), createdAt = nov12 - 3 * 60 * 60 * 1000).first
        val gone = rawSale(rice, listOf(Triple(4_000L, 10_000L, 9_000L))).first
        // A sale cancelled LATER (tomorrow) is still left out of today's numbers.
        cancel(gone.id, at = nov12 + day)
        // A sale on another day is outside the window.
        rawSale(rice, listOf(Triple(1_000L, 50_000L, 1L)), createdAt = nov12 - 2 * day)

        val report = reports().observeSalesReport(today).first()
        val active = db.saleDao().observeActiveTotals(today.startMillis, today.endMillisExclusive).first()
        val list = salesSummary(db.saleDao().observeList(today.startMillis, today.endMillisExclusive).first())
        val twin = salesReportFrom(listOf(a, b, c, gone)) { it.id == gone.id }

        assertEquals(twin, report)
        assertEquals(active.total, report.total)
        assertEquals(active.count, report.count)
        assertEquals(list.total, report.total)
        assertEquals(list.count, report.count)
        assertEquals(list.cancelledExcluded, report.cancelledCount)
        assertEquals(1, report.cancelledCount)
        assertEquals(report.total, report.netSales + report.deliveryCharge)
        assertEquals(report.total, report.paid + report.due)
        assertEquals(3_000L, report.deliveryCharge)
        assertEquals(500L, report.discount)
        assertEquals(15_000L, report.due) // ৳200 - ৳50
    }

    @Test
    fun anEmptyWindowGivesZeros() = runBlocking {
        val r = reports().observeSalesReport(today).first()
        assertEquals(0, r.count)
        assertEquals(0L, r.total)
        assertEquals(0, r.cancelledCount)
        val c = reports().observeCostOfGoods(today).first()
        assertEquals(0L, c.cost)
        assertEquals(0, c.lineCount)
    }

    @Test
    fun theWindowIncludesItsStartAndExcludesItsEnd() = runBlocking {
        val rice = product("চাল")
        rawSale(rice, listOf(Triple(1_000L, 100L, 1L)), createdAt = today.startMillis - 1)
        rawSale(rice, listOf(Triple(1_000L, 200L, 1L)), createdAt = today.startMillis)
        rawSale(rice, listOf(Triple(1_000L, 400L, 1L)), createdAt = today.endMillisExclusive - 1)
        rawSale(rice, listOf(Triple(1_000L, 800L, 1L)), createdAt = today.endMillisExclusive)
        val r = reports().observeSalesReport(today).first()
        assertEquals(2, r.count)
        assertEquals(600L, r.total)
        assertEquals(2, reports().observeCostOfGoods(today).first().lineCount)
    }

    // =====================================================================================
    // Due: report = Khata পাবো; period net = sum of signedAmount
    // =====================================================================================

    @Test
    fun theDueNumbersEqualTheKhataNumbers() = runBlocking {
        val karim = addCustomer("করিম")
        val rahim = addCustomer("রহিম")
        val ledger = db.ledgerDao()
        val entries = listOf(
            LedgerEntry(customerId = karim, type = LedgerEntryType.SALE_DUE, amount = 30_000, entryDate = nov12, createdAt = nov12),
            LedgerEntry(customerId = karim, type = LedgerEntryType.PAYMENT_RECEIVED, amount = 10_000, paymentMethod = PaymentMethod.CASH, entryDate = nov12, createdAt = nov12),
            LedgerEntry(customerId = rahim, type = LedgerEntryType.PAYMENT_RECEIVED, amount = 7_000, paymentMethod = PaymentMethod.CASH, entryDate = nov12, createdAt = nov12),
            LedgerEntry(customerId = rahim, type = LedgerEntryType.OPENING_DUE, amount = 2_000, entryDate = nov12 - 40 * day, createdAt = nov12)
        )
        entries.forEach { ledger.insert(it) }

        val all = khataAllSummary(db.customerDao().observeKhataCustomers().first())
        assertEquals(ledger.observeTotalReceivable().first(), all.receivable)
        assertEquals(20_000L, all.receivable)
        assertEquals(5_000L, all.advance)

        val windowEntries = ledger.observeEntriesInRange(today.startMillis, today.endMillisExclusive).first().map { it.entry }
        val period = periodSummary(windowEntries)
        assertEquals(windowEntries.sumOf { signedAmount(it) }, period.net)
        assertEquals(period.net, period.linesTotal)
        assertEquals(30_000L - 10_000 - 7_000, period.net) // the old opening due is outside today
    }

    // =====================================================================================
    // Expenses: report = expense screen; shop part = SHOP_EXPENSE_SQL
    // =====================================================================================

    private suspend fun expenses(): Triple<Long, Long, Long> {
        val dao = db.expenseDao()
        val rent = dao.insertCategory(ExpenseCategory(name = "ভাড়া"))
        val family = dao.insertCategory(ExpenseCategory(name = "পরিবার", isPersonal = true))
        fun e(cat: Long, amount: Long, at: Long = nov12, deleted: Boolean = false) =
            Expense(expenseCategoryId = cat, amount = amount, expenseDate = at, isDeleted = deleted, createdAt = at, updatedAt = at)
        dao.insert(e(rent, 20_000))
        dao.insert(e(family, 7_000))
        dao.insert(e(rent, 99_000, deleted = true))     // deleted: never counts
        dao.insert(e(rent, 55_000, at = nov12 - 2 * day)) // another day
        return Triple(rent, family, 27_000L)
    }

    @Test
    fun theExpenseNumbersEqualTheExpenseScreen() = runBlocking {
        expenses()
        val dao = db.expenseDao()
        val rows = dao.observeRowsInRange(today.startMillis, today.endMillisExclusive).first()
        val summary = expenseSummary(rows)
        val total = dao.observeTotalInRange(today.startMillis, today.endMillisExclusive).first()
        val shop = dao.observeShopTotalInRange(today.startMillis, today.endMillisExclusive).first()

        assertEquals(total, summary.total)
        assertEquals(shop, summary.shop)
        assertEquals(total - shop, summary.personal)
        assertEquals(27_000L, total)
        assertEquals(20_000L, shop)
    }

    // =====================================================================================
    // Profit: net sales - cost - SHOP expenses; delivery and personal apart
    // =====================================================================================

    @Test
    fun theProfitUsesTheSameNumbersAsEverySection() = runBlocking {
        val rice = product("চাল")
        rawSale(rice, listOf(Triple(2_000L, 10_000L, 8_000L)), discount = 1_000, delivery = 4_000)  // net ৳190, cost ৳160
        rawSale(rice, listOf(Triple(1_000L, 5_000L, null)))                                          // net ৳50, cost unknown
        val gone = rawSale(rice, listOf(Triple(9_000L, 10_000L, 1_000L))).first
        cancel(gone.id)
        expenses()

        val p = reports().observeProfit(today).first()
        val sales = reports().observeSalesReport(today).first()
        val cost = reports().observeCostOfGoods(today).first()
        val shop = db.expenseDao().observeShopTotalInRange(today.startMillis, today.endMillisExclusive).first()
        val total = db.expenseDao().observeTotalInRange(today.startMillis, today.endMillisExclusive).first()

        assertEquals(sales.netSales, p.netSales)
        assertEquals(cost.cost, p.costOfGoods)
        assertEquals(shop, p.shopExpenses)
        assertEquals(sales.netSales - cost.cost - shop, p.netProfit)
        assertEquals(24_000L, p.netSales)       // 19,000 + 5,000 (the delivery is not in it; the cancelled sale is not in it)
        assertEquals(16_000L, p.costOfGoods)
        assertEquals(20_000L, p.shopExpenses)
        assertEquals(-12_000L, p.netProfit)     // a loss of ৳120
        assertTrue(p.isLoss)
        assertEquals(4_000L, p.deliveryCharge)
        assertEquals(total - shop, p.personalExpenses) // ৳70 personal: shown, not taken away
        assertTrue(p.isApproximate)
        assertEquals(1, p.missingCostLines)
        assertEquals(5_000L, p.missingCostSales)
    }

    // =====================================================================================
    // Low stock = the product tags; the dashboard
    // =====================================================================================

    @Test
    fun theLowStockCountEqualsTheProductsTaggedOnTheProductList() = runBlocking {
        product("ঋণাত্মক", stock = -1_000)                         // negative: counts
        product("কম", stock = 2_000, lowLevel = 5_000)              // below the alert: counts
        product("ঠিক সমান", stock = 5_000, lowLevel = 5_000)        // exactly at the alert: counts
        product("যথেষ্ট", stock = 6_000, lowLevel = 5_000)          // above: no
        product("সীমা নেই", stock = 0)                              // no alert level, not negative: no
        product("শূন্য সীমা", stock = 0, lowLevel = 0)              // 0 at a level of 0: counts
        product("আর্কাইভ", stock = -5_000, active = false)          // archived: never counts

        val sql = reports().observeLowStockCount().first()
        val tagged = db.productDao().observeProducts(isActive = true, query = "", categoryId = null).first()
            .count { stockStatus(it.stock, it.product.lowStockLevel) != StockStatus.NORMAL }

        assertEquals(tagged, sql)
        assertEquals(4, sql)
    }

    @Test
    fun theDashboardShowsTodaysNumbersFromTheSameQueries() = runBlocking {
        val rice = product("চাল", stock = -1_000)
        val karim = addCustomer("করিম")
        rawSale(rice, listOf(Triple(1_000L, 30_000L, 20_000L)), paid = 10_000, customerId = karim)
        val gone = rawSale(rice, listOf(Triple(1_000L, 99_000L, 1L))).first
        cancel(gone.id)
        rawSale(rice, listOf(Triple(1_000L, 77_000L, 1L)), createdAt = nov12 - day) // yesterday
        db.ledgerDao().insert(LedgerEntry(customerId = karim, type = LedgerEntryType.SALE_DUE, amount = 20_000, entryDate = nov12, createdAt = nov12))
        expenses()

        val d = reports().observeDashboard(today).first()

        assertEquals(1, d.todaySalesCount)
        assertEquals(30_000L, d.todaySalesTotal)
        assertEquals(reports().observeSalesReport(today).first().total, d.todaySalesTotal)
        assertEquals(27_000L, d.todayExpenseTotal)
        assertEquals(20_000L, d.todayShopExpense)
        assertEquals(7_000L, d.todayPersonalExpense)
        assertEquals(db.ledgerDao().observeTotalReceivable().first(), d.totalReceivable)
        assertEquals(20_000L, d.totalReceivable)
        assertEquals(1, d.lowStockCount)
    }

    @Test
    fun theDashboardFollowsANewSaleLive() = runBlocking {
        val rice = product("চাল")
        assertEquals(0L, reports().observeDashboard(today).first().todaySalesTotal)
        rawSale(rice, listOf(Triple(1_000L, 12_000L, 1L)))
        assertEquals(12_000L, reports().observeDashboard(today).first().todaySalesTotal)
        assertFalse(reports().observeDashboard(today).first().todaySalesCount == 0)
    }
}

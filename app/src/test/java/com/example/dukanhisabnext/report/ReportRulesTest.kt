package com.example.dukanhisabnext.report

import com.example.dukanhisabnext.data.dao.CostOfGoodsRow
import com.example.dukanhisabnext.data.dao.SalesReportRow
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.data.entity.SaleItem
import com.example.dukanhisabnext.util.SaleCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportRulesTest {

    private fun sale(id: Long, subtotal: Long, discount: Long = 0, delivery: Long = 0, paid: Long? = null): Sale {
        val total = subtotal - discount + delivery
        val p = paid ?: total
        return Sale(
            id = id, receiptNo = "261112-%03d".format(id), customerId = if (p < total) 3 else null,
            subtotal = subtotal, discount = discount, deliveryCharge = delivery, total = total,
            paidAmount = p, paymentMethod = PaymentMethod.CASH, dueAmount = total - p, createdAt = 0
        )
    }

    private fun item(quantity: Long, unitPrice: Long, cost: Long?, id: Long = 1) =
        SaleItem(id, 1, 11, "চাল", quantity, unitPrice, cost, SaleCalculator.lineTotal(quantity, unitPrice))

    // =====================================================================================
    // Sales
    // =====================================================================================

    @Test
    fun netSalesIsSubtotalMinusDiscountAndTheDeliveryIsApart() {
        val r = salesReportFrom(listOf(sale(1, 50_000, discount = 2_000, delivery = 3_000)), isCancelled = { false })
        assertEquals(48_000L, r.netSales)
        assertEquals(3_000L, r.deliveryCharge)
        assertEquals(51_000L, r.total)
        assertEquals(r.netSales + r.deliveryCharge, r.total)
    }

    @Test
    fun cancelledSalesAreLeftOutOfEveryAmountAndCounted() {
        val sales = listOf(sale(1, 10_000), sale(2, 20_000, paid = 5_000), sale(3, 99_000))
        val r = salesReportFrom(sales, isCancelled = { it.id == 3L })
        assertEquals(2, r.count)
        assertEquals(30_000L, r.total)
        assertEquals(15_000L, r.paid) // ৳100 paid in full + ৳50 of ৳200
        assertEquals(15_000L, r.due)  // the rest of sale 2
        assertEquals(1, r.cancelledCount)
    }

    @Test
    fun theSalesInvariantsHoldOnRandomData() {
        val random = java.util.Random(11)
        repeat(300) { round ->
            val sales = (1..random.nextInt(20)).map { i ->
                val sub = 100L + random.nextInt(1_000_000)
                val disc = random.nextInt((sub / 2).toInt() + 1).toLong()
                val del = random.nextInt(10_000).toLong()
                val total = sub - disc + del
                sale(i.toLong(), sub, disc, del, paid = random.nextLong().mod(total + 1))
            }
            val r = salesReportFrom(sales) { it.id % 4 == 0L }
            assertEquals("round $round", r.total, r.netSales + r.deliveryCharge)
            assertEquals("round $round", r.total, r.paid + r.due)
            assertEquals("round $round", sales.size, r.count + r.cancelledCount)
        }
    }

    @Test
    fun theSqlRowIsCopiedAsItIs() {
        val row = SalesReportRow(2, 50_000, 2_000, 3_000, 51_000, 30_000, 21_000, 1)
        val r = salesReportOf(row)
        assertEquals(SalesReport(2, 50_000, 2_000, 3_000, 51_000, 30_000, 21_000, 1), r)
        assertEquals(48_000L, r.netSales)
    }

    // =====================================================================================
    // Cost of goods sold
    // =====================================================================================

    @Test
    fun aLineCostIsRoundedHalfUpOnceLikeASaleLine() {
        assertEquals(10_000L, lineCost(item(2_500, 4_000, cost = 4_000)))   // 2.5 kg x ৳40 = ৳100 exactly
        assertEquals(3_330L, lineCost(item(333, 10_001, cost = 10_001)))     // 3330.333 -> 3330
        assertEquals(1L, lineCost(item(500, 1, cost = 1)))                   // 0.5 -> 1 (half goes up)
        assertEquals(0L, lineCost(item(1, 499, cost = 499)))                 // 0.499 -> 0
        assertEquals(1L, lineCost(item(1, 500, cost = 500)))                 // 0.5 -> 1
        assertEquals(0L, lineCost(item(2_000, 0, cost = 0)))                 // a free item costs nothing
        assertNull(lineCost(item(1_000, 5_000, cost = null)))
        for ((q, c) in listOf(333L to 10_001L, 500L to 1L, 1L to 499L, 1_234_567L to 98_765L)) {
            assertEquals(SaleCalculator.lineTotal(q, c), lineCost(item(q, 1, cost = c)))
        }
    }

    @Test
    fun costIsRoundedPerLineNotOnTheSum() {
        // Two lines of 0.5 paisa each: per line 1 + 1 = 2 (rounding the sum would give 1).
        val c = costOfGoods(listOf(item(500, 1, cost = 1, id = 1), item(500, 1, cost = 1, id = 2)))
        assertEquals(2L, c.cost)
    }

    @Test
    fun linesWithoutACostPriceAreCountedApart() {
        val items = listOf(
            item(2_000, 5_000, cost = 4_000, id = 1),   // sold ৳100, cost ৳80
            item(1_000, 3_000, cost = null, id = 2),    // sold ৳30, cost unknown
            item(500, 2_000, cost = null, id = 3)       // sold ৳10, cost unknown
        )
        val c = costOfGoods(items)
        assertEquals(8_000L, c.cost)
        assertEquals(3, c.lineCount)
        assertEquals(2, c.missingCostLines)
        assertEquals(4_000L, c.missingCostSales)
        assertTrue(c.isApproximate)
    }

    @Test
    fun withEveryCostKnownTheCostIsExact() {
        val c = costOfGoods(listOf(item(1_000, 5_000, cost = 4_000)))
        assertFalse(c.isApproximate)
        assertEquals(0, c.missingCostLines)
        assertEquals(0L, c.missingCostSales)
        assertEquals(CostOfGoods(0, 0, 0, 0), costOfGoods(emptyList()))
        assertEquals(CostOfGoods(500, 4, 1, 300), costOfGoodsOf(CostOfGoodsRow(500, 4, 1, 300)))
    }

    // =====================================================================================
    // Profit / loss
    // =====================================================================================

    private val sales = SalesReport(count = 3, subtotal = 100_000, discount = 5_000, deliveryCharge = 4_000,
        total = 99_000, paid = 80_000, due = 19_000, cancelledCount = 1)
    private val cost = CostOfGoods(cost = 60_000, lineCount = 5, missingCostLines = 0, missingCostSales = 0)

    @Test
    fun profitIsNetSalesMinusCostMinusShopExpensesOnly() {
        val p = profitReport(sales, cost, shopExpenses = 20_000, personalExpenses = 7_000)
        assertEquals(95_000L, p.netSales)       // the delivery charge is NOT in it
        assertEquals(60_000L, p.costOfGoods)
        assertEquals(35_000L, p.grossProfit)
        assertEquals(20_000L, p.shopExpenses)
        assertEquals(15_000L, p.netProfit)      // personal ৳70 is NOT taken away
        assertFalse(p.isLoss)
        assertEquals(4_000L, p.deliveryCharge)
        assertEquals(7_000L, p.personalExpenses)
    }

    @Test
    fun aNegativeResultIsALoss() {
        val p = profitReport(sales, cost, shopExpenses = 50_000, personalExpenses = 0)
        assertEquals(-15_000L, p.netProfit)
        assertTrue(p.isLoss)
    }

    @Test
    fun theProfitLinesAreInOrderAndAddUp() {
        val p = profitReport(sales, cost, shopExpenses = 20_000, personalExpenses = 7_000)
        val lines = profitLines(p)
        assertEquals(
            listOf(ProfitLineKind.NET_SALES, ProfitLineKind.COST_OF_GOODS, ProfitLineKind.GROSS_PROFIT, ProfitLineKind.SHOP_EXPENSES, ProfitLineKind.NET_PROFIT),
            lines.map { it.kind }
        )
        assertEquals(listOf(LineRole.START, LineRole.MINUS, LineRole.RESULT, LineRole.MINUS, LineRole.RESULT), lines.map { it.role })
        assertEquals(listOf(95_000L, 60_000L, 35_000L, 20_000L, 15_000L), lines.map { it.amount })
        // Working down the box gives each result line.
        assertEquals(lines[0].amount - lines[1].amount, lines[2].amount)
        assertEquals(lines[2].amount - lines[3].amount, lines[4].amount)
    }

    @Test
    fun deliveryAndPersonalAreInformationLinesShownWhenNotZero() {
        val p = profitReport(sales, cost, shopExpenses = 20_000, personalExpenses = 7_000)
        assertEquals(
            listOf(InfoLine(InfoLineKind.DELIVERY_CHARGE, 4_000), InfoLine(InfoLineKind.PERSONAL_EXPENSES, 7_000)),
            profitInfoLines(p)
        )
        val none = profitReport(sales.copy(deliveryCharge = 0, total = 95_000), cost, 20_000, 0)
        assertTrue(profitInfoLines(none).isEmpty())
    }

    @Test
    fun theApproximateNoteSaysHowMuchHadNoCostPrice() {
        val exact = profitReport(sales, cost, 0, 0)
        assertNull(approximateNote(exact))
        assertFalse(exact.isApproximate)

        val partial = profitReport(sales, cost.copy(missingCostLines = 2, missingCostSales = 4_000), 0, 0)
        assertTrue(partial.isApproximate)
        assertEquals(ApproximateNote(missingCostLines = 2, missingCostSales = 4_000), approximateNote(partial))
    }

    @Test
    fun theProfitAlwaysAddsUpOnRandomData() {
        val random = java.util.Random(5)
        repeat(300) { round ->
            val s = sales.copy(subtotal = random.nextInt(10_000_000).toLong(), discount = random.nextInt(100_000).toLong())
            val c = cost.copy(cost = random.nextInt(10_000_000).toLong())
            val shop = random.nextInt(1_000_000).toLong()
            val personal = random.nextInt(1_000_000).toLong()
            val p = profitReport(s, c, shop, personal)
            assertEquals("round $round", s.subtotal - s.discount - c.cost - shop, p.netProfit)
            assertEquals("round $round", p.isLoss, p.netProfit < 0)
        }
    }

    // =====================================================================================
    // Dashboard
    // =====================================================================================

    @Test
    fun theDashboardTakesTodaysNumbersAndSplitsTheExpense() {
        val d = dashboardOf(sales, expenseTotal = 27_000, shopExpense = 20_000, totalReceivable = 150_000, lowStockCount = 4)
        assertEquals(3, d.todaySalesCount)
        assertEquals(99_000L, d.todaySalesTotal) // cancelled ones are already left out of the report
        assertEquals(27_000L, d.todayExpenseTotal)
        assertEquals(20_000L, d.todayShopExpense)
        assertEquals(7_000L, d.todayPersonalExpense)
        assertEquals(d.todayExpenseTotal, d.todayShopExpense + d.todayPersonalExpense)
        assertEquals(150_000L, d.totalReceivable)
        assertEquals(4, d.lowStockCount)
    }

    @Test
    fun anEmptyDayIsAllZero() {
        val empty = SalesReport(0, 0, 0, 0, 0, 0, 0, 0)
        assertEquals(Dashboard(0, 0, 0, 0, 0, 0, 0), dashboardOf(empty, 0, 0, 0, 0))
    }
}

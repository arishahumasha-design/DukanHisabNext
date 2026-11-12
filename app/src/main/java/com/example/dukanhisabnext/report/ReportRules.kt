package com.example.dukanhisabnext.report

import com.example.dukanhisabnext.data.dao.CostOfGoodsRow
import com.example.dukanhisabnext.data.dao.SalesReportRow
import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.data.entity.SaleItem
import com.example.dukanhisabnext.util.SaleCalculator

// The rules of the reports (রিপোর্ট) and the Home dashboard, as plain Kotlin (no Android) so they
// can be unit-tested on the PC. Money is Long paisa. Every number comes from the shared rules:
// cancelled sales are left out by NOT_CANCELLED_SQL, deleted expenses by ACTIVE_EXPENSE_SQL, shop
// costs are SHOP_EXPENSE_SQL, balances are BALANCE_SUM_SQL / signedAmount, and a line's cost is
// rounded once per line exactly like SaleCalculator.lineTotal. There is no second formula here.

// =========================================================================================
// Sales
// =========================================================================================

/**
 * The sales of a period (cancelled sales left out of every amount):
 *  - [netSales] = subtotal - discount (what the goods sold for); the delivery charge is apart;
 *  - [total] = netSales + deliveryCharge = paid + due (true for each sale, so also for the sum);
 *  - [cancelledCount]: how many cancelled sales were left out ("(বাতিল N টি বাদ)").
 */
data class SalesReport(
    val count: Int,
    val subtotal: Long,
    val discount: Long,
    val deliveryCharge: Long,
    val total: Long,
    val paid: Long,
    val due: Long,
    val cancelledCount: Int
) {
    val netSales: Long get() = subtotal - discount
}

fun salesReportOf(row: SalesReportRow): SalesReport = SalesReport(
    count = row.count,
    subtotal = row.subtotal,
    discount = row.discount,
    deliveryCharge = row.deliveryCharge,
    total = row.total,
    paid = row.paid,
    due = row.due,
    cancelledCount = row.cancelledCount
)

/** The same report worked out in Kotlin from the sales themselves (the twin of the SQL; used by the tests). */
fun salesReportFrom(sales: List<Sale>, isCancelled: (Sale) -> Boolean): SalesReport {
    val active = sales.filterNot(isCancelled)
    return SalesReport(
        count = active.size,
        subtotal = active.sumOf { it.subtotal },
        discount = active.sumOf { it.discount },
        deliveryCharge = active.sumOf { it.deliveryCharge },
        total = active.sumOf { it.total },
        paid = active.sumOf { it.paidAmount },
        due = active.sumOf { it.dueAmount },
        cancelledCount = sales.size - active.size
    )
}

// =========================================================================================
// Cost of goods sold
// =========================================================================================

/**
 * The cost of the goods sold in a period. [cost] adds up the lines that HAVE a cost price; the lines
 * without one ([missingCostLines], worth [missingCostSales] in sales) are left out, so the profit is
 * then too high by their unknown cost: the report says "আনুমানিক".
 */
data class CostOfGoods(
    val cost: Long,
    val lineCount: Int,
    val missingCostLines: Int,
    val missingCostSales: Long
) {
    val isApproximate: Boolean get() = missingCostLines > 0
}

fun costOfGoodsOf(row: CostOfGoodsRow): CostOfGoods =
    CostOfGoods(row.cost, row.lineCount, row.missingCostLines, row.missingCostSales)

/** The cost of ONE sale line: quantity (thousandths) x cost price, rounded half-up once (as a sale line). null = no cost price. */
fun lineCost(item: SaleItem): Long? = item.costPriceSnapshot?.let { SaleCalculator.lineTotal(item.quantity, it) }

/** The same cost worked out in Kotlin from the sale lines (the twin of the SQL; used by the tests). */
fun costOfGoods(items: List<SaleItem>): CostOfGoods = CostOfGoods(
    cost = items.sumOf { lineCost(it) ?: 0L },
    lineCount = items.size,
    missingCostLines = items.count { it.costPriceSnapshot == null },
    missingCostSales = items.filter { it.costPriceSnapshot == null }.sumOf { it.lineTotal }
)

// =========================================================================================
// Profit / loss
// =========================================================================================

/**
 * লাভ-ক্ষতি of a period:
 *   নিট বিক্রি (subtotal - discount)
 * - পণ্যের ক্রয়মূল্য (cost of goods sold)
 * = মোট লাভ (gross profit)
 * - দোকানের খরচ (SHOP expenses only)
 * = নিট লাভ / ক্ষতি
 * The delivery charge and the personal expenses are shown as information lines only: neither is
 * part of the profit.
 */
data class ProfitReport(
    val netSales: Long,
    val costOfGoods: Long,
    val shopExpenses: Long,
    val deliveryCharge: Long,
    val personalExpenses: Long,
    val missingCostLines: Int,
    val missingCostSales: Long
) {
    val grossProfit: Long get() = netSales - costOfGoods
    val netProfit: Long get() = grossProfit - shopExpenses
    val isLoss: Boolean get() = netProfit < 0
    val isApproximate: Boolean get() = missingCostLines > 0
}

fun profitReport(sales: SalesReport, cost: CostOfGoods, shopExpenses: Long, personalExpenses: Long): ProfitReport = ProfitReport(
    netSales = sales.netSales,
    costOfGoods = cost.cost,
    shopExpenses = shopExpenses,
    deliveryCharge = sales.deliveryCharge,
    personalExpenses = personalExpenses,
    missingCostLines = cost.missingCostLines,
    missingCostSales = cost.missingCostSales
)

/** The lines of the profit box, in order. [MINUS] lines are taken away; [RESULT] lines are the sums so far. */
enum class ProfitLineKind { NET_SALES, COST_OF_GOODS, GROSS_PROFIT, SHOP_EXPENSES, NET_PROFIT }

enum class LineRole { START, MINUS, RESULT }

data class ProfitLine(val kind: ProfitLineKind, val role: LineRole, val amount: Long)

fun profitLines(p: ProfitReport): List<ProfitLine> = listOf(
    ProfitLine(ProfitLineKind.NET_SALES, LineRole.START, p.netSales),
    ProfitLine(ProfitLineKind.COST_OF_GOODS, LineRole.MINUS, p.costOfGoods),
    ProfitLine(ProfitLineKind.GROSS_PROFIT, LineRole.RESULT, p.grossProfit),
    ProfitLine(ProfitLineKind.SHOP_EXPENSES, LineRole.MINUS, p.shopExpenses),
    ProfitLine(ProfitLineKind.NET_PROFIT, LineRole.RESULT, p.netProfit)
)

/** The information lines under the profit box (not part of the profit). Shown only when not 0. */
enum class InfoLineKind { DELIVERY_CHARGE, PERSONAL_EXPENSES }

data class InfoLine(val kind: InfoLineKind, val amount: Long)

fun profitInfoLines(p: ProfitReport): List<InfoLine> = buildList {
    if (p.deliveryCharge != 0L) add(InfoLine(InfoLineKind.DELIVERY_CHARGE, p.deliveryCharge))
    if (p.personalExpenses != 0L) add(InfoLine(InfoLineKind.PERSONAL_EXPENSES, p.personalExpenses))
}

/** "আনুমানিক — ৳X বিক্রির (Nটি পণ্য-লাইন) ক্রয়মূল্য জানা নেই": null when every sold line had a cost price. */
data class ApproximateNote(val missingCostLines: Int, val missingCostSales: Long)

fun approximateNote(p: ProfitReport): ApproximateNote? =
    if (p.isApproximate) ApproximateNote(p.missingCostLines, p.missingCostSales) else null

// =========================================================================================
// The Home dashboard
// =========================================================================================

/**
 * The four Home cards:
 *  - আজকের বিক্রি: [todaySalesCount] and [todaySalesTotal] (cancelled sales left out);
 *  - আজকের খরচ: [todayExpenseTotal], split into [todayShopExpense] and [todayPersonalExpense];
 *  - মোট পাবো: [totalReceivable] (the Khata পাবো; advances are not subtracted);
 *  - কম স্টক: [lowStockCount] (active products with negative stock or stock at/below the alert level).
 */
data class Dashboard(
    val todaySalesCount: Int,
    val todaySalesTotal: Long,
    val todayExpenseTotal: Long,
    val todayShopExpense: Long,
    val todayPersonalExpense: Long,
    val totalReceivable: Long,
    val lowStockCount: Int
)

/**
 * [expenseTotal] is every (not deleted) expense of today; [shopExpense] the shop part
 * (SHOP_EXPENSE_SQL). The personal part is the rest, so the two always add up to the total.
 */
fun dashboardOf(
    todaySales: SalesReport,
    expenseTotal: Long,
    shopExpense: Long,
    totalReceivable: Long,
    lowStockCount: Int
): Dashboard = Dashboard(
    todaySalesCount = todaySales.count,
    todaySalesTotal = todaySales.total,
    todayExpenseTotal = expenseTotal,
    todayShopExpense = shopExpense,
    todayPersonalExpense = expenseTotal - shopExpense,
    totalReceivable = totalReceivable,
    lowStockCount = lowStockCount
)

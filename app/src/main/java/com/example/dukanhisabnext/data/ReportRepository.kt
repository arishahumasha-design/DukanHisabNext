package com.example.dukanhisabnext.data

import com.example.dukanhisabnext.report.CostOfGoods
import com.example.dukanhisabnext.report.Dashboard
import com.example.dukanhisabnext.report.ProfitReport
import com.example.dukanhisabnext.report.SalesReport
import com.example.dukanhisabnext.report.costOfGoodsOf
import com.example.dukanhisabnext.report.dashboardOf
import com.example.dukanhisabnext.report.profitReport
import com.example.dukanhisabnext.report.salesReportOf
import com.example.dukanhisabnext.util.DhakaTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Read-only numbers for the reports and the Home dashboard. Every number is live (a Flow): Room
 * runs the query again when one of its tables changes. Nothing here writes.
 * The due and expense reports reuse the Khata and expense queries directly (LedgerRepository,
 * ExpenseRepository), so they cannot drift from those screens.
 */
class ReportRepository(db: AppDatabase) {
    private val reportDao = db.reportDao()
    private val expenseDao = db.expenseDao()
    private val ledgerDao = db.ledgerDao()

    fun observeSalesReport(range: DhakaTime.TimeRange): Flow<SalesReport> =
        reportDao.observeSalesReport(range.startMillis, range.endMillisExclusive).map(::salesReportOf)

    fun observeCostOfGoods(range: DhakaTime.TimeRange): Flow<CostOfGoods> =
        reportDao.observeCostOfGoods(range.startMillis, range.endMillisExclusive).map(::costOfGoodsOf)

    fun observeLowStockCount(): Flow<Int> = reportDao.observeLowStockCount()

    /** লাভ-ক্ষতি: net sales - cost of goods - SHOP expenses; delivery and personal shown apart. */
    fun observeProfit(range: DhakaTime.TimeRange): Flow<ProfitReport> = combine(
        observeSalesReport(range),
        observeCostOfGoods(range),
        expenseDao.observeShopTotalInRange(range.startMillis, range.endMillisExclusive),
        expenseDao.observeTotalInRange(range.startMillis, range.endMillisExclusive)
    ) { sales, cost, shop, total ->
        profitReport(sales, cost, shopExpenses = shop, personalExpenses = total - shop)
    }

    /** The four Home cards for the Dhaka day [today]. */
    fun observeDashboard(today: DhakaTime.TimeRange): Flow<Dashboard> = combine(
        observeSalesReport(today),
        expenseDao.observeTotalInRange(today.startMillis, today.endMillisExclusive),
        expenseDao.observeShopTotalInRange(today.startMillis, today.endMillisExclusive),
        ledgerDao.observeTotalReceivable(),
        observeLowStockCount()
    ) { sales, expenseTotal, shop, receivable, lowStock ->
        dashboardOf(sales, expenseTotal, shop, receivable, lowStock)
    }
}

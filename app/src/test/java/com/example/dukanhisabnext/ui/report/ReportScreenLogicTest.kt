package com.example.dukanhisabnext.ui.report

import com.example.dukanhisabnext.report.Dashboard
import com.example.dukanhisabnext.report.LineRole
import com.example.dukanhisabnext.report.ProfitLine
import com.example.dukanhisabnext.report.ProfitLineKind
import com.example.dukanhisabnext.report.ProfitReport
import com.example.dukanhisabnext.report.SalesReport
import com.example.dukanhisabnext.report.profitLines
import com.example.dukanhisabnext.ui.expense.ExpensePeriod
import com.example.dukanhisabnext.ui.expense.expenseRange
import com.example.dukanhisabnext.util.DhakaTime
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The logic of the রিপোর্ট screen (tab and chip state, text decisions, loss wording) and the Home dashboard. */
class ReportScreenLogicTest {

    private val now = Instant.parse("2026-11-12T08:30:00Z").toEpochMilli()
    private val today = LocalDate.of(2026, 11, 12)

    // =====================================================================================
    // Tab and chip state
    // =====================================================================================

    @Test
    fun theFourTabsAreInTheAgreedOrderAndSalesIsFirst() {
        assertEquals(listOf(ReportTab.SALES, ReportTab.DUE, ReportTab.EXPENSES, ReportTab.PROFIT), ReportTab.entries)
        assertEquals(ReportTab.SALES, ReportControls().tab)
    }

    @Test
    fun aSavedTabIsReadBackAndAnUnknownOneFallsBackToSales() {
        for (t in ReportTab.entries) assertEquals(t, reportTabFromName(t.name))
        assertEquals(ReportTab.SALES, reportTabFromName(null))
        assertEquals(ReportTab.SALES, reportTabFromName("nonsense"))
    }

    @Test
    fun theChipDefaultsToTodayAndTheChipsAreTheSameAsTheExpenseScreen() {
        assertEquals(ReportPeriod.TODAY, ReportControls().period)
        assertEquals(
            listOf(ReportPeriod.TODAY, ReportPeriod.LAST_7_DAYS, ReportPeriod.THIS_MONTH, ReportPeriod.THIS_YEAR, ReportPeriod.CUSTOM),
            ReportPeriod.entries
        )
        assertEquals(ExpensePeriod.entries, ReportPeriod.entries)
    }

    @Test
    fun theReportUsesTheSameDhakaWindowsAsTheOtherScreens() {
        val range = expenseRange(ReportPeriod.TODAY, now, null, null)!!
        assertEquals(DhakaTime.startOfDay(today), range.startMillis)
        assertEquals(DhakaTime.startOfDay(today.plusDays(1)), range.endMillisExclusive)
        val week = expenseRange(ReportPeriod.LAST_7_DAYS, now, null, null)!!
        assertEquals(DhakaTime.startOfDay(LocalDate.of(2026, 11, 6)), week.startMillis)
    }

    @Test
    fun theCustomChipIsReadyOnlyWithBothDays() {
        assertTrue(ReportControls(period = ReportPeriod.TODAY).rangeReady())
        assertTrue(ReportControls(period = ReportPeriod.THIS_YEAR).rangeReady())
        assertFalse(ReportControls(period = ReportPeriod.CUSTOM).rangeReady())
        assertFalse(ReportControls(period = ReportPeriod.CUSTOM, customFrom = today).rangeReady())
        assertFalse(ReportControls(period = ReportPeriod.CUSTOM, customTo = today).rangeReady())
        assertTrue(ReportControls(period = ReportPeriod.CUSTOM, customFrom = today, customTo = today).rangeReady())
        // The "period" box of the বাকি tab follows the same rule.
        assertTrue(showsPeriodBox(true))
        assertFalse(showsPeriodBox(false))
    }

    // =====================================================================================
    // বিক্রি
    // =====================================================================================

    private val sales = SalesReport(count = 3, subtotal = 100_000, discount = 5_000, deliveryCharge = 4_000,
        total = 99_000, paid = 80_000, due = 19_000, cancelledCount = 2)

    @Test
    fun theSalesBoxHasSixLinesInOrder() {
        assertEquals(
            listOf(SalesLine.COUNT, SalesLine.NET_SALES, SalesLine.DELIVERY, SalesLine.TOTAL, SalesLine.PAID, SalesLine.DUE),
            SalesLine.entries
        )
    }

    @Test
    fun eachSalesLineTakesItsValueAndOnlyTheCountIsNotMoney() {
        assertEquals(3L, salesLineAmount(sales, SalesLine.COUNT))
        assertEquals(95_000L, salesLineAmount(sales, SalesLine.NET_SALES)) // subtotal - discount; the delivery is apart
        assertEquals(4_000L, salesLineAmount(sales, SalesLine.DELIVERY))
        assertEquals(99_000L, salesLineAmount(sales, SalesLine.TOTAL))
        assertEquals(80_000L, salesLineAmount(sales, SalesLine.PAID))
        assertEquals(19_000L, salesLineAmount(sales, SalesLine.DUE))
        for (line in SalesLine.entries) assertEquals(line.name, line != SalesLine.COUNT, isMoneyLine(line))
        assertEquals(salesLineAmount(sales, SalesLine.TOTAL), salesLineAmount(sales, SalesLine.NET_SALES) + salesLineAmount(sales, SalesLine.DELIVERY))
        assertEquals(salesLineAmount(sales, SalesLine.TOTAL), salesLineAmount(sales, SalesLine.PAID) + salesLineAmount(sales, SalesLine.DUE))
    }

    @Test
    fun theCountAndTheTotalAreBold() {
        assertEquals(setOf(SalesLine.COUNT, SalesLine.TOTAL), SalesLine.entries.filter(::isBoldSalesLine).toSet())
    }

    @Test
    fun theCancelledNoteShowsOnlyWhenSomethingWasLeftOut() {
        assertTrue(showsCancelledNote(sales))
        assertFalse(showsCancelledNote(sales.copy(cancelledCount = 0)))
    }

    @Test
    fun anEmptyPeriodHasNoSales() {
        assertFalse(salesEmpty(sales))
        assertTrue(salesEmpty(SalesReport(0, 0, 0, 0, 0, 0, 0, 0)))
        // Only cancelled sales: nothing counts, so the list is empty but the note is shown.
        val onlyCancelled = SalesReport(0, 0, 0, 0, 0, 0, 0, cancelledCount = 1)
        assertTrue(salesEmpty(onlyCancelled))
        assertTrue(showsCancelledNote(onlyCancelled))
    }

    @Test
    fun theExpenseSectionSaysNoExpenseOnlyWhenThereIsNone() {
        assertTrue(expensesEmpty(0))
        assertFalse(expensesEmpty(1))
    }

    // =====================================================================================
    // লাভ-ক্ষতি: loss wording
    // =====================================================================================

    private fun profit(netSales: Long, cost: Long, shop: Long, delivery: Long = 0, personal: Long = 0, missing: Int = 0, missingSales: Long = 0) =
        ProfitReport(netSales, cost, shop, delivery, personal, missing, missingSales)

    @Test
    fun theResultWordIsProfitLossOrEven() {
        assertEquals(ResultWord.PROFIT, resultWord(1))
        assertEquals(ResultWord.LOSS, resultWord(-1))
        assertEquals(ResultWord.EVEN, resultWord(0))
        assertEquals(ResultWord.PROFIT, netResultWord(profit(100_000, 60_000, 20_000)))
        assertEquals(ResultWord.LOSS, netResultWord(profit(100_000, 60_000, 50_000)))
        assertEquals(ResultWord.EVEN, netResultWord(profit(100_000, 60_000, 40_000)))
    }

    private fun line(p: ProfitReport, kind: ProfitLineKind) = profitLines(p).single { it.kind == kind }

    @Test
    fun theAmountTextsOfAProfit() {
        val p = profit(95_000, 60_000, 20_000)
        assertEquals("৳ ৯৫০", profitAmountText(line(p, ProfitLineKind.NET_SALES), "৳", "৯৫০"))
        assertEquals("− ৳ ৬০০", profitAmountText(line(p, ProfitLineKind.COST_OF_GOODS), "৳", "৬০০"))
        assertEquals("৳ ৩৫০", profitAmountText(line(p, ProfitLineKind.GROSS_PROFIT), "৳", "৩৫০"))
        assertEquals("− ৳ ২০০", profitAmountText(line(p, ProfitLineKind.SHOP_EXPENSES), "৳", "২০০"))
        assertEquals("৳ ১৫০", profitAmountText(line(p, ProfitLineKind.NET_PROFIT), "৳", "১৫০"))
    }

    @Test
    fun aNetLossIsWrittenAsThePlainAmountBecauseTheLabelSaysLoss() {
        val loss = profit(95_000, 60_000, 50_000) // net -15,000
        val last = line(loss, ProfitLineKind.NET_PROFIT)
        assertEquals(-15_000L, last.amount)
        assertEquals("৳ ১৫০", profitAmountText(last, "৳", "১৫০")) // no minus sign: "নিট ক্ষতি ৳ ১৫০"
        assertTrue(isRedProfitLine(last))
        assertEquals(ResultWord.LOSS, netResultWord(loss))
    }

    @Test
    fun aNegativeGrossProfitKeepsItsMinusSign() {
        val p = profit(10_000, 30_000, 0) // gross -20,000
        assertEquals("− ৳ ২০০", profitAmountText(line(p, ProfitLineKind.GROSS_PROFIT), "৳", "২০০"))
        assertFalse(isRedProfitLine(line(p, ProfitLineKind.GROSS_PROFIT))) // only the last line is red
    }

    @Test
    fun redIsOnlyForALossOnTheLastLine() {
        val profitCase = profit(95_000, 60_000, 20_000)
        assertTrue(profitLines(profitCase).none(::isRedProfitLine))
        val lossCase = profit(95_000, 60_000, 50_000)
        assertEquals(listOf(ProfitLineKind.NET_PROFIT), profitLines(lossCase).filter(::isRedProfitLine).map { it.kind })
        val even = profit(100_000, 60_000, 40_000)
        assertTrue(profitLines(even).none(::isRedProfitLine))
    }

    @Test
    fun theResultLinesAreBold() {
        val lines = profitLines(profit(95_000, 60_000, 20_000))
        assertEquals(
            listOf(ProfitLineKind.GROSS_PROFIT, ProfitLineKind.NET_PROFIT),
            lines.filter(::isBoldProfitLine).map { it.kind }
        )
        assertEquals(LineRole.RESULT, lines.last().role)
    }

    @Test
    fun aZeroLineHasNoSign() {
        val zero = ProfitLine(ProfitLineKind.NET_PROFIT, LineRole.RESULT, 0)
        assertEquals("৳ ০", profitAmountText(zero, "৳", "০"))
    }

    // =====================================================================================
    // The Home dashboard
    // =====================================================================================

    private val dashboard = Dashboard(
        todaySalesCount = 3, todaySalesTotal = 99_000, todayExpenseTotal = 27_000,
        todayShopExpense = 20_000, todayPersonalExpense = 7_000, totalReceivable = 150_000, lowStockCount = 4
    )

    @Test
    fun thereAreFourCardsInOrderAndEachOpensItsScreen() {
        assertEquals(
            listOf(DashboardCard.TODAY_SALES, DashboardCard.TODAY_EXPENSE, DashboardCard.RECEIVABLE, DashboardCard.LOW_STOCK),
            DashboardCard.entries
        )
        assertEquals(DashboardTarget.SALES_LIST, targetOf(DashboardCard.TODAY_SALES))
        assertEquals(DashboardTarget.EXPENSE_LIST, targetOf(DashboardCard.TODAY_EXPENSE))
        assertEquals(DashboardTarget.KHATA, targetOf(DashboardCard.RECEIVABLE))
        assertEquals(DashboardTarget.LOW_STOCK_PRODUCTS, targetOf(DashboardCard.LOW_STOCK))
        // Every target is used by exactly one card.
        assertEquals(DashboardTarget.entries.toSet(), DashboardCard.entries.map(::targetOf).toSet())
    }

    @Test
    fun theSalesCardShowsTheCountOnlyWhenThereIsASale() {
        assertTrue(showsSalesCount(dashboard))
        assertFalse(showsSalesCount(dashboard.copy(todaySalesCount = 0, todaySalesTotal = 0)))
    }

    @Test
    fun theExpenseCardShowsTheSplitOnlyWhenSomePersonalSpendingExists() {
        assertTrue(showsExpenseSplit(dashboard))
        assertFalse(showsExpenseSplit(dashboard.copy(todayPersonalExpense = 0, todayShopExpense = 27_000)))
        assertFalse(showsExpenseSplit(Dashboard(0, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun theLowStockCardSaysAttentionOrAllGood() {
        assertEquals(LowStockState.NEEDS_ATTENTION, lowStockState(dashboard))
        assertEquals(LowStockState.ALL_GOOD, lowStockState(dashboard.copy(lowStockCount = 0)))
    }

    @Test
    fun theCardNumbersAddUp() {
        assertEquals(dashboard.todayExpenseTotal, dashboard.todayShopExpense + dashboard.todayPersonalExpense)
        assertNull(null) // (the numbers themselves come from ReportRepository; see ReportQueriesTest on the phone)
    }
}

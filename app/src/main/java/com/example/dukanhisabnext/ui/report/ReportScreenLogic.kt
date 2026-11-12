package com.example.dukanhisabnext.ui.report

import com.example.dukanhisabnext.report.Dashboard
import com.example.dukanhisabnext.report.LineRole
import com.example.dukanhisabnext.report.ProfitLine
import com.example.dukanhisabnext.report.ProfitLineKind
import com.example.dukanhisabnext.report.ProfitReport
import com.example.dukanhisabnext.report.SalesReport
import com.example.dukanhisabnext.ui.expense.ExpensePeriod
import java.time.LocalDate

// The logic of the রিপোর্ট screen and the Home dashboard, as plain Kotlin (no Android) so it can be
// unit-tested on the PC. The numbers themselves come from report/ReportRules.kt and the shared
// Khata / expense rules; nothing here works out money a second way.

// =========================================================================================
// What the report screen remembers (kept in the SavedStateHandle)
// =========================================================================================

/** The four sections. The first is the default. */
enum class ReportTab { SALES, DUE, EXPENSES, PROFIT }

fun reportTabFromName(name: String?): ReportTab = ReportTab.entries.firstOrNull { it.name == name } ?: ReportTab.entries.first()

/** The time chips are the same as on the expense screen (আজ is the default). */
typealias ReportPeriod = ExpensePeriod

/** Everything the user chose on the report screen. The tab and the chip are shared by all four sections. */
data class ReportControls(
    val tab: ReportTab = ReportTab.SALES,
    val period: ReportPeriod = ReportPeriod.TODAY,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null
)

// =========================================================================================
// বিক্রি
// =========================================================================================

/** The lines of the sales box, in order. [COUNT] is a number of sales, the others are money. */
enum class SalesLine { COUNT, NET_SALES, DELIVERY, TOTAL, PAID, DUE }

fun salesLineAmount(report: SalesReport, line: SalesLine): Long = when (line) {
    SalesLine.COUNT -> report.count.toLong()
    SalesLine.NET_SALES -> report.netSales
    SalesLine.DELIVERY -> report.deliveryCharge
    SalesLine.TOTAL -> report.total
    SalesLine.PAID -> report.paid
    SalesLine.DUE -> report.due
}

fun isMoneyLine(line: SalesLine): Boolean = line != SalesLine.COUNT

/** The bold lines: the count and the total. */
fun isBoldSalesLine(line: SalesLine): Boolean = line == SalesLine.COUNT || line == SalesLine.TOTAL

/** "(বাতিল N টি বাদ)" is shown when at least one cancelled sale was left out. */
fun showsCancelledNote(report: SalesReport): Boolean = report.cancelledCount > 0

/** The sales list under the box is empty (there is no sale in the period). */
fun salesEmpty(report: SalesReport): Boolean = report.count == 0

// =========================================================================================
// বাকি
// =========================================================================================

/** The "period" box of the বাকি section needs both days of "তারিখ বাছুন"; the "now" box never does. */
fun showsPeriodBox(rangeReady: Boolean): Boolean = rangeReady

// =========================================================================================
// খরচ
// =========================================================================================

/** The expense section says "no expense" under the summary when the period has none. */
fun expensesEmpty(expenseCount: Int): Boolean = expenseCount == 0

// =========================================================================================
// লাভ-ক্ষতি
// =========================================================================================

/** The result line of the profit box says profit (লাভ), loss (ক্ষতি) or neither (0). */
enum class ResultWord { PROFIT, LOSS, EVEN }

fun resultWord(amount: Long): ResultWord = when {
    amount > 0 -> ResultWord.PROFIT
    amount < 0 -> ResultWord.LOSS
    else -> ResultWord.EVEN
}

/** The last line is written "নিট লাভ" for a profit and a zero, and "নিট ক্ষতি" for a loss (shown in red). */
fun netResultWord(p: ProfitReport): ResultWord = resultWord(p.netProfit)

/**
 * The text of one profit line's amount:
 *  - the first line: "৳ X";
 *  - the taken-away lines: "− ৳ X";
 *  - a result line: "৳ X"; a negative gross profit is "− ৳ X"; a net LOSS is shown as the plain
 *    positive amount, because the label already says "ক্ষতি".
 * [formatted] is the money without the taka sign, of the ABSOLUTE amount.
 */
fun profitAmountText(line: ProfitLine, taka: String, formatted: String): String = when {
    line.role == LineRole.MINUS -> "− $taka $formatted"
    line.kind == ProfitLineKind.NET_PROFIT -> "$taka $formatted"
    line.role == LineRole.RESULT && line.amount < 0 -> "− $taka $formatted"
    else -> "$taka $formatted"
}

/** The result lines are bold. */
fun isBoldProfitLine(line: ProfitLine): Boolean = line.role == LineRole.RESULT

/** The red colour is used for a loss on the last line (and nowhere else). */
fun isRedProfitLine(line: ProfitLine): Boolean = line.kind == ProfitLineKind.NET_PROFIT && line.amount < 0

// =========================================================================================
// The Home dashboard
// =========================================================================================

/** The four cards of the Home screen, in order. */
enum class DashboardCard { TODAY_SALES, TODAY_EXPENSE, RECEIVABLE, LOW_STOCK }

/** Where a card leads. */
enum class DashboardTarget { SALES_LIST, EXPENSE_LIST, KHATA, LOW_STOCK_PRODUCTS }

fun targetOf(card: DashboardCard): DashboardTarget = when (card) {
    DashboardCard.TODAY_SALES -> DashboardTarget.SALES_LIST
    DashboardCard.TODAY_EXPENSE -> DashboardTarget.EXPENSE_LIST
    DashboardCard.RECEIVABLE -> DashboardTarget.KHATA
    DashboardCard.LOW_STOCK -> DashboardTarget.LOW_STOCK_PRODUCTS
}

/** The small "দোকান ৳X · ব্যক্তিগত ৳Y" line of the expense card: only when some personal spending exists. */
fun showsExpenseSplit(d: Dashboard): Boolean = d.todayPersonalExpense > 0

/** The low-stock card says how many products need attention, or that all is well. */
enum class LowStockState { ALL_GOOD, NEEDS_ATTENTION }

fun lowStockState(d: Dashboard): LowStockState = if (d.lowStockCount > 0) LowStockState.NEEDS_ATTENTION else LowStockState.ALL_GOOD

/** The sales card says how many sales were made today ("N টি বিক্রি"), or that there is none yet. */
fun showsSalesCount(d: Dashboard): Boolean = d.todaySalesCount > 0

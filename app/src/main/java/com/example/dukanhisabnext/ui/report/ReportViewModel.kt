package com.example.dukanhisabnext.ui.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.ExpenseRepository
import com.example.dukanhisabnext.data.LedgerRepository
import com.example.dukanhisabnext.data.ReportRepository
import com.example.dukanhisabnext.data.SaleRepository
import com.example.dukanhisabnext.data.dao.KhataCustomerRow
import com.example.dukanhisabnext.data.dao.SaleListRow
import com.example.dukanhisabnext.expense.ExpenseSummary
import com.example.dukanhisabnext.expense.expenseSummary
import com.example.dukanhisabnext.ledger.KhataAllSummary
import com.example.dukanhisabnext.ledger.KhataSort
import com.example.dukanhisabnext.ledger.PeriodSummary
import com.example.dukanhisabnext.ledger.customDays
import com.example.dukanhisabnext.ledger.khataAllSummary
import com.example.dukanhisabnext.ledger.periodSummary
import com.example.dukanhisabnext.ledger.visibleKhataCustomers
import com.example.dukanhisabnext.report.ProfitReport
import com.example.dukanhisabnext.report.SalesReport
import com.example.dukanhisabnext.ui.expense.ExpensePeriod
import com.example.dukanhisabnext.ui.expense.expensePeriodFromName
import com.example.dukanhisabnext.ui.expense.expenseRange
import com.example.dukanhisabnext.ui.khata.NO_DAY
import com.example.dukanhisabnext.ui.khata.dayOrNull
import com.example.dukanhisabnext.util.DhakaTime
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the body of the report screen shows (one per tab). */
sealed interface ReportBody {
    /** "তারিখ বাছুন" before both days are chosen (the বাকি tab still shows its "now" box). */
    data object PickDates : ReportBody

    data class Sales(val report: SalesReport, val rows: List<SaleListRow>) : ReportBody

    /** [period] is null while "তারিখ বাছুন" has no range yet. */
    data class Due(val now: KhataAllSummary, val owing: List<KhataCustomerRow>, val period: PeriodSummary?) : ReportBody

    data class Expenses(val summary: ExpenseSummary, val count: Int) : ReportBody

    data class Profit(val report: ProfitReport) : ReportBody
}

data class ReportUi(val controls: ReportControls, val rangeReady: Boolean, val body: ReportBody)

/** Both days of "তারিখ বাছুন" are chosen (every other chip is always ready). */
fun ReportControls.rangeReady(): Boolean = period != ReportPeriod.CUSTOM || (customFrom != null && customTo != null)

/**
 * Logic of the রিপোর্ট screen. The tab, the chip and the custom days are kept in the
 * [SavedStateHandle] (rotation, Back, the app closed in the background) and are shared by the four
 * tabs. Only the shown tab loads its data, with a few queries; every number comes from the same
 * rule as the screen that already shows it (see report/ReportRules.kt).
 */
class ReportViewModel(
    private val reports: ReportRepository,
    private val ledger: LedgerRepository,
    private val expenses: ExpenseRepository,
    private val sales: SaleRepository,
    private val state: SavedStateHandle,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val tab: StateFlow<ReportTab> = state.getStateFlow(KEY_TAB, ReportTab.entries.first().name)
        .map(::reportTabFromName)
        .stateIn(viewModelScope, SharingStarted.Eagerly, reportTabFromName(state[KEY_TAB]))
    private val period: StateFlow<ReportPeriod> = state.getStateFlow(KEY_PERIOD, ExpensePeriod.TODAY.name)
        .map(::expensePeriodFromName)
        .stateIn(viewModelScope, SharingStarted.Eagerly, expensePeriodFromName(state[KEY_PERIOD]))
    private val customFrom: StateFlow<LocalDate?> = state.getStateFlow(KEY_FROM, NO_DAY)
        .map(::dayOrNull)
        .stateIn(viewModelScope, SharingStarted.Eagerly, dayOrNull(state[KEY_FROM] ?: NO_DAY))
    private val customTo: StateFlow<LocalDate?> = state.getStateFlow(KEY_TO, NO_DAY)
        .map(::dayOrNull)
        .stateIn(viewModelScope, SharingStarted.Eagerly, dayOrNull(state[KEY_TO] ?: NO_DAY))

    private val controls: Flow<ReportControls> = combine(tab, period, customFrom, customTo) { t, p, f, to ->
        ReportControls(t, p, f, to)
    }

    /** Only the shown tab's queries run: switching the tab or the chip drops the old ones and starts new ones. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val body: Flow<ReportBody> = controls.distinctUntilChanged().flatMapLatest { c ->
        val range = expenseRange(c.period, clock(), c.customFrom, c.customTo)
        when {
            c.tab != ReportTab.DUE && range == null -> flowOf(ReportBody.PickDates)

            c.tab == ReportTab.SALES -> combine(
                reports.observeSalesReport(range!!),
                sales.observeSalesList(range).map { rows -> rows.filterNot { it.isCancelled } } // cancelled sales are left out
            ) { report, rows -> ReportBody.Sales(report, rows) }

            c.tab == ReportTab.DUE -> combine(
                ledger.observeKhataCustomers(),
                if (range == null) flowOf(null) else ledger.observeEntriesInRange(range)
            ) { customers, entries ->
                ReportBody.Due(
                    now = khataAllSummary(customers), // the whole shop, the same as the Khata সব view
                    owing = visibleKhataCustomers(customers, query = "", onlyOwing = true, sort = KhataSort.AMOUNT_HIGH_TO_LOW),
                    period = entries?.let { periodSummary(it.map { row -> row.entry }) } // the same as the Khata period summary
                )
            }

            c.tab == ReportTab.EXPENSES -> expenses.observeRowsInRange(range!!)
                .map { rows -> ReportBody.Expenses(expenseSummary(rows), rows.size) } // the same as the expense screen

            else -> reports.observeProfit(range!!).map { ReportBody.Profit(it) }
        }
    }

    /** What the screen shows. null while loading. The sums run off the main thread. */
    val ui: StateFlow<ReportUi?> = combine(controls, body) { c, b -> ReportUi(c, c.rangeReady(), b) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onTabChange(value: ReportTab) {
        state[KEY_TAB] = value.name
    }

    fun onPeriodChange(value: ReportPeriod) {
        state[KEY_PERIOD] = value.name
    }

    /** Both days of "তারিখ বাছুন" were picked: no future day, and a backwards pair is swapped. */
    fun onCustomRange(from: LocalDate, to: LocalDate) {
        val (a, b) = customDays(from, to, today()) ?: return
        state[KEY_FROM] = a.toEpochDay()
        state[KEY_TO] = b.toEpochDay()
        state[KEY_PERIOD] = ExpensePeriod.CUSTOM.name
    }

    /** The Dhaka "today" for the date pickers. */
    fun today(): LocalDate = DhakaTime.localDate(clock())

    private companion object {
        const val KEY_TAB = "report_tab"
        const val KEY_PERIOD = "report_period"
        const val KEY_FROM = "report_from"
        const val KEY_TO = "report_to"
    }
}

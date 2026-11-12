package com.example.dukanhisabnext.ui.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.ExpenseRepository
import com.example.dukanhisabnext.expense.ExpenseSummary
import com.example.dukanhisabnext.expense.ExpenseDay
import com.example.dukanhisabnext.expense.expenseSummary
import com.example.dukanhisabnext.expense.groupExpensesByDay
import com.example.dukanhisabnext.expense.searchedExpenses
import com.example.dukanhisabnext.ledger.customDays
import com.example.dukanhisabnext.data.dao.ExpenseRow
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the expense list shows. */
data class ExpenseListUi(
    val controls: ExpenseControls,
    /** false for "তারিখ বাছুন" until both days are picked. */
    val rangeReady: Boolean,
    /** The summary of the WHOLE period (it ignores the search). */
    val summary: ExpenseSummary,
    /** The rows that match the search, grouped by Dhaka day. */
    val days: List<ExpenseDay>,
    val windowCount: Int,
    val shownCount: Int
)

/**
 * Logic of the expense list. The chip, the custom days and the search text are kept in the
 * [SavedStateHandle], so they survive rotation, Back and the app being closed in the background.
 * Each chip loads its expenses with ONE query; typing in the search box only filters the loaded list.
 */
class ExpenseListViewModel(
    private val repo: ExpenseRepository,
    private val state: SavedStateHandle,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val period: StateFlow<ExpensePeriod> = state.getStateFlow(KEY_PERIOD, ExpensePeriod.TODAY.name)
        .map(::expensePeriodFromName)
        .stateIn(viewModelScope, SharingStarted.Eagerly, expensePeriodFromName(state[KEY_PERIOD]))
    private val customFrom: StateFlow<LocalDate?> = state.getStateFlow(KEY_FROM, NO_DAY)
        .map(::dayOrNull)
        .stateIn(viewModelScope, SharingStarted.Eagerly, dayOrNull(state[KEY_FROM] ?: NO_DAY))
    private val customTo: StateFlow<LocalDate?> = state.getStateFlow(KEY_TO, NO_DAY)
        .map(::dayOrNull)
        .stateIn(viewModelScope, SharingStarted.Eagerly, dayOrNull(state[KEY_TO] ?: NO_DAY))
    val query: StateFlow<String> = state.getStateFlow(KEY_QUERY, "")

    private val controls: Flow<ExpenseControls> = combine(period, customFrom, customTo, query) { p, f, t, q ->
        ExpenseControls(p, f, t, q)
    }

    /** The expenses of the chosen window (one query per window; not reloaded while typing in the search box). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val windowRows: Flow<List<ExpenseRow>?> = combine(period, customFrom, customTo) { p, f, t -> Triple(p, f, t) }
        .distinctUntilChanged()
        .flatMapLatest { (p, f, t) ->
            val range = expenseRange(p, clock(), f, t)
            if (range == null) flowOf<List<ExpenseRow>?>(null) else repo.observeRowsInRange(range)
        }

    /** What the screen shows. null while loading. The search, sums and grouping run off the main thread. */
    val ui: StateFlow<ExpenseListUi?> = combine(controls, windowRows) { c, rows ->
        val window = rows.orEmpty()
        val shown = searchedExpenses(window, c.query)
        ExpenseListUi(
            controls = c,
            rangeReady = rows != null,
            summary = expenseSummary(window), // the whole period: ignores the search
            days = groupExpensesByDay(shown),
            windowCount = window.size,
            shownCount = shown.size
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onPeriodChange(value: ExpensePeriod) {
        state[KEY_PERIOD] = value.name
    }

    /** Both days of "তারিখ বাছুন" were picked: no future day, and a backwards pair is swapped. */
    fun onCustomRange(from: LocalDate, to: LocalDate) {
        val (a, b) = customDays(from, to, today()) ?: return
        state[KEY_FROM] = a.toEpochDay()
        state[KEY_TO] = b.toEpochDay()
        state[KEY_PERIOD] = ExpensePeriod.CUSTOM.name
    }

    fun onQueryChange(text: String) {
        state[KEY_QUERY] = text
    }

    /** The Dhaka "today" for the date pickers. */
    fun today(): LocalDate = DhakaTime.localDate(clock())

    /** The undo of a delete ("ফিরিয়ে আনুন"). */
    fun undoDelete(expenseId: Long) {
        viewModelScope.launch { repo.restoreExpense(expenseId) }
    }

    private companion object {
        const val KEY_PERIOD = "expense_period"
        const val KEY_FROM = "expense_from"
        const val KEY_TO = "expense_to"
        const val KEY_QUERY = "expense_query"
    }
}

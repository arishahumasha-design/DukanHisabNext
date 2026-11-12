package com.example.dukanhisabnext.ui.khata

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.LedgerRepository
import com.example.dukanhisabnext.data.dao.KhataCustomerRow
import com.example.dukanhisabnext.data.dao.KhataEntryRow
import com.example.dukanhisabnext.ledger.KhataAllSummary
import com.example.dukanhisabnext.ledger.KhataPeriod
import com.example.dukanhisabnext.ledger.KhataSort
import com.example.dukanhisabnext.ledger.PeriodSummary
import com.example.dukanhisabnext.ledger.customDays
import com.example.dukanhisabnext.ledger.khataAllSummary
import com.example.dukanhisabnext.ledger.periodRange
import com.example.dukanhisabnext.ledger.periodSummary
import com.example.dukanhisabnext.ledger.visibleKhataCustomers
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

/** What the body of the screen shows. */
sealed interface KhataBody {
    /** The "সব" view: the customers. */
    data class All(
        val summary: KhataAllSummary,
        val rows: List<KhataCustomerRow>,
        val totalCustomers: Int
    ) : KhataBody

    /** A period chip: the entries of the window, grouped by day. [rangeReady] is false until both custom days are picked. */
    data class Period(
        val rangeReady: Boolean,
        val summary: PeriodSummary,
        val days: List<EntryDay>,
        val windowCount: Int,
        val shownCount: Int
    ) : KhataBody
}

data class KhataUi(val controls: KhataControls, val body: KhataBody, val totalCustomers: Int)

/**
 * Logic of the "বাকির খাতা" tab. The chip, the custom days, the sort, the "যাদের বাকি আছে" toggle and
 * the search text are kept in the [SavedStateHandle], so they survive rotation, Back and the app being
 * closed in the background.
 *
 * Each chip loads its data with ONE query (the customers for সব, the entries of the window for
 * the others); typing in the search box only filters the loaded list (no new query).
 */
class KhataViewModel(
    private val ledger: LedgerRepository,
    private val state: SavedStateHandle,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val period: StateFlow<KhataPeriod> = mapState(KEY_PERIOD, KhataPeriod.ALL.name, ::periodFromName)
    private val customFrom: StateFlow<LocalDate?> = mapState(KEY_FROM, NO_DAY, ::dayOrNull)
    private val customTo: StateFlow<LocalDate?> = mapState(KEY_TO, NO_DAY, ::dayOrNull)
    private val sort: StateFlow<KhataSort> = mapState(KEY_SORT, KhataSort.entries.first().name, ::sortFromName)
    private val onlyOwing: StateFlow<Boolean> = state.getStateFlow(KEY_OWING, false)
    val query: StateFlow<String> = state.getStateFlow(KEY_QUERY, "")

    private fun <T, R> mapState(key: String, initial: T, convert: (T) -> R): StateFlow<R> =
        state.getStateFlow(key, initial).map(convert).stateIn(viewModelScope, SharingStarted.Eagerly, convert(state[key] ?: initial))

    private val controls: Flow<KhataControls> = combine(
        combine(period, customFrom, customTo) { p, f, t -> Triple(p, f, t) },
        combine(sort, onlyOwing, query) { s, o, q -> Triple(s, o, q) }
    ) { (p, f, t), (s, o, q) -> KhataControls(p, f, t, s, o, q) }

    private val customers: Flow<List<KhataCustomerRow>> = ledger.observeKhataCustomers()

    /** Every customer (for the "নতুন বাকি" picker), A to Z. */
    val allCustomers: StateFlow<List<KhataCustomerRow>?> = customers
        .map { customersAtoZ(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The entries of the chosen window (one query per window; not reloaded while typing in the search box). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val windowEntries: Flow<List<KhataEntryRow>?> = combine(period, customFrom, customTo) { p, f, t -> Triple(p, f, t) }
        .distinctUntilChanged()
        .flatMapLatest { (p, f, t) ->
            val range = periodRange(p, clock(), f, t)
            if (range == null) flowOf<List<KhataEntryRow>?>(null) else ledger.observeEntriesInRange(range)
        }

    /** What the screen shows. null while loading. The search, sort and sums run off the main thread. */
    val ui: StateFlow<KhataUi?> = combine(controls, customers, windowEntries) { c, all, entries ->
        val body: KhataBody = if (c.isAll) {
            KhataBody.All(
                summary = khataAllSummary(all), // the whole shop: ignores the search and the filter
                rows = visibleKhataCustomers(all, c.query, c.onlyOwing, c.sort),
                totalCustomers = all.size
            )
        } else {
            val window = entries.orEmpty()
            val shown = searchedEntries(window, c.query)
            KhataBody.Period(
                rangeReady = entries != null,
                summary = periodSummary(window.map { it.entry }), // the whole window: ignores the search
                days = groupEntriesByDay(shown),
                windowCount = window.size,
                shownCount = shown.size
            )
        }
        KhataUi(c, body, all.size)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onPeriodChange(value: KhataPeriod) {
        state[KEY_PERIOD] = value.name
    }

    /** Both days of "তারিখ বাছুন" were picked: no future day, and a backwards pair is swapped. */
    fun onCustomRange(from: LocalDate, to: LocalDate) {
        val today = DhakaTime.localDate(clock())
        val (a, b) = customDays(from, to, today) ?: return
        state[KEY_FROM] = a.toEpochDay()
        state[KEY_TO] = b.toEpochDay()
        state[KEY_PERIOD] = KhataPeriod.CUSTOM.name
    }

    fun onSortChange(value: KhataSort) {
        state[KEY_SORT] = value.name
    }

    fun onOnlyOwingChange(value: Boolean) {
        state[KEY_OWING] = value
    }

    fun onQueryChange(text: String) {
        state[KEY_QUERY] = text
    }

    /** The Dhaka "today" for the date pickers. */
    fun today(): LocalDate = DhakaTime.localDate(clock())

    private companion object {
        const val KEY_PERIOD = "khata_period"
        const val KEY_FROM = "khata_from"
        const val KEY_TO = "khata_to"
        const val KEY_SORT = "khata_sort"
        const val KEY_OWING = "khata_owing"
        const val KEY_QUERY = "khata_query"
    }
}

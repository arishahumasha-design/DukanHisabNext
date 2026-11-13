package com.example.dukanhisabnext.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.SaleRepository
import com.example.dukanhisabnext.ledger.customDays
import com.example.dukanhisabnext.util.DhakaTime
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * Logic for the read-only sales list. The chosen filter, the search text and how many rows are
 * shown live here, so they are kept when a receipt is opened and you come back with Back, and when
 * the phone is rotated.
 *
 * Only the newest [SALES_PAGE_SIZE] sales of the window are loaded ("আরও দেখান" loads more), so a
 * year of sales stays light. The count and total line comes from a separate summary query over the
 * WHOLE window and search, never from the loaded rows.
 */
class SalesListViewModel(private val sales: SaleRepository) : ViewModel() {

    private val _filter = MutableStateFlow(SalesFilter.TODAY) // the list opens on আজ
    val filter: StateFlow<SalesFilter> = _filter.asStateFlow()

    /** The two days of "তারিখ বাছুন" (the same day twice = one day), or null before they are picked. */
    private val _from = MutableStateFlow<LocalDate?>(null)
    val customFrom: StateFlow<LocalDate?> = _from.asStateFlow()
    private val _to = MutableStateFlow<LocalDate?>(null)
    val customTo: StateFlow<LocalDate?> = _to.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** How many rows are loaded now. Back to one page whenever the filter or the search changes. */
    private val _limit = MutableStateFlow(SALES_PAGE_SIZE)

    private data class Request(
        val filter: SalesFilter,
        val query: String,
        val limit: Int,
        val from: LocalDate?,
        val to: LocalDate?
    )

    /**
     * What the screen shows. null while loading. While the search box has text, the chip is ignored
     * and ALL sales are searched (by the database); clearing the box brings the chip's window back.
     * The Dhaka day or month window is worked out each time the request changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<SalesListUi?> =
        combine(_filter, _query, _limit, _from, _to) { chip, query, limit, from, to ->
            Request(effectiveSalesFilter(chip, query), query.trim(), limit, from, to)
        }
            .distinctUntilChanged()
            .flatMapLatest { request ->
                // "তারিখ বাছুন" before both days are picked: nothing to load, the screen asks for the dates.
                if (needsDatesPicked(request.filter, request.from, request.to)) {
                    return@flatMapLatest flowOf(SalesListUi(emptyList(), SalesSummary(0, 0), EmptyKind.PICK_DATES))
                }
                val range = salesFilterRange(request.filter, System.currentTimeMillis(), request.from, request.to)
                val search = salesSearchParams(request.query)
                combine(
                    sales.observeSalesPage(range, search, request.limit),
                    sales.observeSalesPageSummary(range, search),
                    sales.observeSalesCount()
                ) { rows, summary, totalSalesInDb ->
                    buildSalesPageUi(rows, summary.toSalesSummary(), totalSalesInDb)
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onFilterChange(filter: SalesFilter) {
        _filter.value = filter
        _limit.value = SALES_PAGE_SIZE
    }

    /** Both days of "তারিখ বাছুন" were picked (one day twice, or a range): no future day, a backwards pair is swapped. */
    fun onCustomRange(from: LocalDate, to: LocalDate) {
        val (a, b) = customDays(from, to, today()) ?: return
        _from.value = a
        _to.value = b
        _filter.value = SalesFilter.CUSTOM
        _limit.value = SALES_PAGE_SIZE
    }

    /** The Dhaka "today" for the date pickers. */
    fun today(): LocalDate = DhakaTime.localDate(System.currentTimeMillis())

    fun onQueryChange(text: String) {
        _query.value = text
        _limit.value = SALES_PAGE_SIZE
    }

    /** "আরও দেখান": one more page of older sales. */
    fun onShowMore() {
        _limit.value += SALES_PAGE_SIZE
    }
}

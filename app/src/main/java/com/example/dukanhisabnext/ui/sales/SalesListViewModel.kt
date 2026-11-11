package com.example.dukanhisabnext.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.SaleRepository
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
 * Logic for the read-only sales list. The chosen filter and the search text live here, so they
 * are kept when a receipt is opened and you come back with Back, and when the phone is rotated.
 */
class SalesListViewModel(private val sales: SaleRepository) : ViewModel() {

    private val _filter = MutableStateFlow(SalesFilter.TODAY) // the list opens on আজ
    val filter: StateFlow<SalesFilter> = _filter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * The sales of the chosen filter, newest first. The Dhaka day or month window is worked out
     * each time the filter is chosen (and when the list is opened again), and the database does
     * the cutting, so only the sales of that window are loaded.
     * While the search box has text, the chip is ignored and ALL sales are searched; clearing the
     * box brings the chip's window back.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val rows = combine(_filter, _query) { chip, query -> effectiveSalesFilter(chip, query) }
        .distinctUntilChanged() // typing more letters while searching does not load again
        .flatMapLatest { filter ->
            sales.observeSalesList(salesFilterRange(filter, System.currentTimeMillis()))
        }

    /** What the screen shows. null while loading. The search, grouping and totals run off the main thread. */
    val ui: StateFlow<SalesListUi?> =
        combine(rows, _query, sales.observeSalesCount()) { rows, query, totalSalesInDb ->
            buildSalesListUi(rows, query, totalSalesInDb)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onFilterChange(filter: SalesFilter) {
        _filter.value = filter
    }

    fun onQueryChange(text: String) {
        _query.value = text
    }
}

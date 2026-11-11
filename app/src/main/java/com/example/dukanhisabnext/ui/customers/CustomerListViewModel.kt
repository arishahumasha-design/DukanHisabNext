package com.example.dukanhisabnext.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.CustomerRepository
import com.example.dukanhisabnext.data.dao.CustomerWithBalance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Holds what the Khata list needs: the search text, the filter chip, the live list, and the total receivable. */
class CustomerListViewModel(repo: CustomerRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** The "যাদের বাকি আছে" chip. Kept here, so rotation and coming back from a customer keep it. */
    private val _onlyOwing = MutableStateFlow(false)
    val onlyOwing: StateFlow<Boolean> = _onlyOwing.asStateFlow()

    /**
     * Customers with their balances, biggest due first (the database sorts them); with the chip on,
     * only those who owe. null = still loading.
     * When the search text changes, the old database query is dropped and a new one starts.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val customers: StateFlow<List<CustomerWithBalance>?> = combine(
        _query.flatMapLatest { repo.observeCustomers(it) },
        _onlyOwing
    ) { all, onlyOwing -> visibleCustomers(all, onlyOwing) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Total receivable (pabo) shown at the top. Computed from the ledger. It ignores advances. */
    val totalReceivable: StateFlow<Long> = repo.observeTotalReceivable()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun onOnlyOwingChange(value: Boolean) {
        _onlyOwing.value = value
    }
}

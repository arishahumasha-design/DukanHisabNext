package com.example.dukanhisabnext.ui.customers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.CustomerRepository
import com.example.dukanhisabnext.data.LedgerRepository
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.ledger.HistoryDay
import com.example.dukanhisabnext.ledger.LedgerAction
import com.example.dukanhisabnext.ledger.buttonActions
import com.example.dukanhisabnext.ledger.groupHistoryByDhakaDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Logic for the customer detail screen: the customer, the live balance, the history grouped by
 * Dhaka day, and which action buttons to show.
 */
class CustomerDetailViewModel(
    repo: CustomerRepository,
    ledger: LedgerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val customerId: Long = requireNotNull(savedStateHandle.get<Long>("customerId")) {
        "customer_detail needs a customerId"
    }

    /** The customer. Updates by itself after an edit. null while loading. */
    val customer: StateFlow<Customer?> = repo.observeCustomer(customerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The balance in paisa, always computed from the ledger. null while loading. */
    val balance: StateFlow<Long?> = repo.observeBalance(customerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The history, newest day first. Grouped off the main thread. null while loading. */
    val history: StateFlow<List<HistoryDay>?> = ledger.observeHistory(customerId)
        .map { groupHistoryByDhakaDay(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The buttons to show: পুরনো বাকি only while there is none, অগ্রিম ফেরত only while the customer
     * has an advance. Empty while loading.
     */
    val actions: StateFlow<List<LedgerAction>> =
        combine(ledger.observeBalance(customerId), ledger.observeHasOpeningDue(customerId)) { balance, hasOpening ->
            buttonActions(balance, hasOpening)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

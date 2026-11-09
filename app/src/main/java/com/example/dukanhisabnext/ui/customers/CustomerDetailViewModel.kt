package com.example.dukanhisabnext.ui.customers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.CustomerRepository
import com.example.dukanhisabnext.data.entity.Customer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Logic for the customer detail screen: the customer and the live balance.
 * M5 will add the due/payment history here.
 */
class CustomerDetailViewModel(
    repo: CustomerRepository,
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
}

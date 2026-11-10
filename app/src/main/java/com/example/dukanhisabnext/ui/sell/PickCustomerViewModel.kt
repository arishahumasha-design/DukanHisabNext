package com.example.dukanhisabnext.ui.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.CustomerRepository
import com.example.dukanhisabnext.data.dao.CustomerWithBalance
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.ui.customers.CustomerFormErrors
import com.example.dukanhisabnext.ui.customers.CustomerFormInput
import com.example.dukanhisabnext.ui.customers.CustomerFormResult
import com.example.dukanhisabnext.ui.customers.validateCustomerForm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The small "new customer" pop-up: a name and an optional mobile. */
data class QuickCustomerState(
    val open: Boolean = false,
    val name: String = "",
    val phone: String = "",
    val errors: CustomerFormErrors = CustomerFormErrors(),
    val isSaving: Boolean = false
)

/**
 * Logic for choosing a customer for the sale in progress: search, pick, or quick-add a new one.
 * The choice is stored in the shared [CartStore], where the payment screen reads it.
 */
class PickCustomerViewModel(
    private val customers: CustomerRepository,
    private val cart: CartStore
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Customers with their current balances, searched by name or mobile. null = loading. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val customerList: StateFlow<List<CustomerWithBalance>?> = _query
        .flatMapLatest { customers.observeCustomers(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _quick = MutableStateFlow(QuickCustomerState())
    val quick: StateFlow<QuickCustomerState> = _quick.asStateFlow()

    /** Becomes true once a customer is chosen: the screen then closes itself. */
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun onPick(customerId: Long) {
        cart.setCustomer(customerId)
        _finished.value = true
    }

    // ---- Quick add ----

    fun openQuickAdd() = _quick.update { QuickCustomerState(open = true) }

    fun closeQuickAdd() = _quick.update { it.copy(open = false) }

    fun onQuickNameChange(text: String) = _quick.update { it.copy(name = text, errors = it.errors.copy(name = null)) }

    fun onQuickPhoneChange(text: String) = _quick.update { it.copy(phone = text, errors = it.errors.copy(phone = null)) }

    /** Same rules as the customer form in the Khata tab (M3): a name is needed, the mobile is optional but checked. */
    fun confirmQuickAdd() {
        val state = _quick.value
        if (state.isSaving) return

        when (val result = validateCustomerForm(CustomerFormInput(state.name, state.phone, address = "", note = ""))) {
            is CustomerFormResult.Invalid -> _quick.update { it.copy(errors = result.errors) }

            is CustomerFormResult.Valid -> {
                _quick.update { it.copy(isSaving = true) }
                val checked = result.customer
                viewModelScope.launch {
                    val newId = customers.addCustomer(
                        Customer(name = checked.name, phone = checked.phone, createdAt = 0, updatedAt = 0)
                    )
                    _quick.update { it.copy(open = false, isSaving = false) }
                    onPick(newId) // the new customer is chosen for this sale right away
                }
            }
        }
    }
}

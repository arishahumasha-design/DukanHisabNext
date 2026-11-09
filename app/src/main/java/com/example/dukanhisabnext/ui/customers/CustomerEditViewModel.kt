package com.example.dukanhisabnext.ui.customers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.CustomerRepository
import com.example.dukanhisabnext.data.entity.Customer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the customer form shows. */
data class CustomerFormState(
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val note: String = "",
    val errors: CustomerFormErrors = CustomerFormErrors(),
    /** A gentle warning (not a block): another customer has this mobile number. */
    val phoneWarning: Boolean = false,
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    /** Becomes true after a successful save: the screen then closes itself. */
    val finished: Boolean = false
)

/**
 * Logic for the add/edit customer screen. The customer id comes from the navigation route
 * (SavedStateHandle holds the route arguments). No id (-1) means "new customer".
 */
class CustomerEditViewModel(
    private val repo: CustomerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val customerId: Long? = savedStateHandle.get<Long>("customerId")?.takeIf { it >= 0 }

    /** The customer as it was loaded. An edit copies it and changes only the form fields. */
    private var loaded: Customer? = null

    private val _state = MutableStateFlow(
        CustomerFormState(isNew = customerId == null, isLoading = customerId != null)
    )
    val state: StateFlow<CustomerFormState> = _state.asStateFlow()

    init {
        if (customerId != null) load(customerId)
    }

    private fun load(id: Long) {
        viewModelScope.launch {
            val customer = repo.getCustomer(id)
            if (customer == null) {
                _state.update { it.copy(isLoading = false, finished = true) } // nothing to edit
                return@launch
            }
            loaded = customer
            _state.update {
                it.copy(
                    name = customer.name,
                    phone = customer.phone.orEmpty(),
                    address = customer.address.orEmpty(),
                    note = customer.note.orEmpty(),
                    isLoading = false
                )
            }
        }
    }

    // ---- Typing in the form. Typing in a box clears that box's old error. ----

    fun onNameChange(text: String) =
        _state.update { it.copy(name = text, errors = it.errors.copy(name = null)) }

    fun onPhoneChange(text: String) {
        _state.update { it.copy(phone = text, errors = it.errors.copy(phone = null)) }
        viewModelScope.launch {
            // Only a valid number can be a duplicate. The check ignores this customer itself.
            val normalized = normalizeMobile(text)
            val exists = normalized != null && repo.phoneExists(normalized, excludeId = customerId)
            _state.update { if (it.phone == text) it.copy(phoneWarning = exists) else it }
        }
    }

    fun onAddressChange(text: String) = _state.update { it.copy(address = text) }

    fun onNoteChange(text: String) = _state.update { it.copy(note = text) }

    // ---- Save ----

    fun save() {
        val s = _state.value
        if (s.isSaving || s.isLoading) return

        when (val result = validateCustomerForm(CustomerFormInput(s.name, s.phone, s.address, s.note))) {
            is CustomerFormResult.Invalid -> _state.update { it.copy(errors = result.errors) }

            is CustomerFormResult.Valid -> {
                _state.update { it.copy(errors = CustomerFormErrors(), isSaving = true) }
                val checked = result.customer
                viewModelScope.launch {
                    val original = loaded
                    if (customerId == null || original == null) {
                        // createdAt/updatedAt are placeholders: the repository sets the real times.
                        repo.addCustomer(
                            Customer(
                                name = checked.name,
                                phone = checked.phone,
                                address = checked.address,
                                note = checked.note,
                                createdAt = 0,
                                updatedAt = 0
                            )
                        )
                    } else {
                        repo.updateCustomer(
                            original.copy(
                                name = checked.name,
                                phone = checked.phone,
                                address = checked.address,
                                note = checked.note
                            )
                        )
                    }
                    _state.update { it.copy(isSaving = false, finished = true) }
                }
            }
        }
    }
}

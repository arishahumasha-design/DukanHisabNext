package com.example.dukanhisabnext.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.ShopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OwnerFormState(
    val ownerName: String = "",
    val phone: String = "",
    val errors: OwnerFormErrors = OwnerFormErrors(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    /** True right after a successful save: the "saved" message shows until something is edited. */
    val saved: Boolean = false
)

/**
 * Logic for the small owner form. It writes to the SAME single ShopProfile row as "দোকানের তথ্য",
 * changing only the owner name and the mobile (the shop name and address are kept).
 * What is typed lives here, so it survives rotating the phone.
 */
class OwnerViewModel(private val repo: ShopRepository) : ViewModel() {

    private val _state = MutableStateFlow(OwnerFormState())
    val state: StateFlow<OwnerFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = repo.get() // null the first time: the boxes stay empty
            _state.update {
                it.copy(
                    ownerName = profile?.ownerName.orEmpty(),
                    phone = profile?.phone.orEmpty(),
                    isLoading = false
                )
            }
        }
    }

    fun onOwnerNameChange(text: String) = _state.update { it.copy(ownerName = text, saved = false) }

    fun onPhoneChange(text: String) =
        _state.update { it.copy(phone = text, saved = false, errors = OwnerFormErrors()) }

    fun save() {
        val s = _state.value
        if (s.isSaving || s.isLoading) return

        when (val result = validateOwnerForm(OwnerFormInput(s.ownerName, s.phone))) {
            is OwnerFormResult.Invalid -> _state.update { it.copy(errors = result.errors, saved = false) }

            is OwnerFormResult.Valid -> {
                _state.update { it.copy(errors = OwnerFormErrors(), isSaving = true, saved = false) }
                val owner = result.owner
                viewModelScope.launch {
                    repo.updateOwner(owner.ownerName, owner.phone)
                    // Show the cleaned values (for example the mobile as 11 digits) and the message.
                    _state.update {
                        it.copy(
                            ownerName = owner.ownerName.orEmpty(),
                            phone = owner.phone.orEmpty(),
                            isSaving = false,
                            saved = true
                        )
                    }
                }
            }
        }
    }
}

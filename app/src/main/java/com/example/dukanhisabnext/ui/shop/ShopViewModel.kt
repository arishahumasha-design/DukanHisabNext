package com.example.dukanhisabnext.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.ShopRepository
import com.example.dukanhisabnext.data.entity.ShopProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the "দোকানের তথ্য" form shows. */
data class ShopFormState(
    val name: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val address: String = "",
    val errors: ShopFormErrors = ShopFormErrors(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    /** True right after a successful save: the "saved" message shows until something is edited. */
    val saved: Boolean = false
)

/** Logic for the "দোকানের তথ্য" screen: load the one shop profile row, edit it, save it. */
class ShopViewModel(private val repo: ShopRepository) : ViewModel() {

    private val _state = MutableStateFlow(ShopFormState())
    val state: StateFlow<ShopFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = repo.get() // null the first time: the boxes stay empty
            _state.update {
                it.copy(
                    name = profile?.name.orEmpty(),
                    ownerName = profile?.ownerName.orEmpty(),
                    phone = profile?.phone.orEmpty(),
                    address = profile?.address.orEmpty(),
                    isLoading = false
                )
            }
        }
    }

    // ---- Typing: clears that box's old error and the "saved" message ----

    fun onNameChange(text: String) =
        _state.update { it.copy(name = text, saved = false, errors = it.errors.copy(name = null)) }

    fun onOwnerChange(text: String) = _state.update { it.copy(ownerName = text, saved = false) }

    fun onPhoneChange(text: String) =
        _state.update { it.copy(phone = text, saved = false, errors = it.errors.copy(phone = null)) }

    fun onAddressChange(text: String) = _state.update { it.copy(address = text, saved = false) }

    fun save() {
        val s = _state.value
        if (s.isSaving || s.isLoading) return

        when (val result = validateShopForm(ShopFormInput(s.name, s.ownerName, s.phone, s.address))) {
            is ShopFormResult.Invalid -> _state.update { it.copy(errors = result.errors, saved = false) }

            is ShopFormResult.Valid -> {
                _state.update { it.copy(errors = ShopFormErrors(), isSaving = true, saved = false) }
                val shop = result.shop
                viewModelScope.launch {
                    repo.save(
                        ShopProfile(
                            name = shop.name,
                            ownerName = shop.ownerName,
                            phone = shop.phone,
                            address = shop.address
                        )
                    )
                    // Show the cleaned values (for example the mobile as 11 digits) and the message.
                    _state.update {
                        it.copy(
                            name = shop.name,
                            ownerName = shop.ownerName.orEmpty(),
                            phone = shop.phone.orEmpty(),
                            address = shop.address.orEmpty(),
                            isSaving = false,
                            saved = true
                        )
                    }
                }
            }
        }
    }
}

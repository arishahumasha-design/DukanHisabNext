package com.example.dukanhisabnext.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.entity.ShopProfile
import com.example.dukanhisabnext.shop.ShopChange
import com.example.dukanhisabnext.shop.ShopDetails
import com.example.dukanhisabnext.shop.ShopManager
import com.example.dukanhisabnext.shop.ShopNameProblem
import com.example.dukanhisabnext.ui.products.FormError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the shop form shows. */
data class ShopFormState(
    val name: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val address: String = "",
    val errors: ShopFormErrors = ShopFormErrors(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    /** True right after a successful save: the "saved" message shows until something is edited. */
    val saved: Boolean = false,
    /** A new shop that was just made (its id): the screen goes back and offers to open it. */
    val createdId: Int? = null,
    /** "Too many shops": a new one cannot be made. */
    val tooMany: Boolean = false
)

/** Where the shop form was opened for. */
sealed interface ShopFormTarget {
    /** A new shop: the owner name and mobile are taken from the open shop. */
    data object New : ShopFormTarget

    /** An existing shop (the open one or another). */
    data class Existing(val id: Int) : ShopFormTarget
}

/**
 * Logic of the shop form ("দোকানের তথ্য"): a new shop, the open shop, or another shop (which is opened only
 * briefly to write its own profile). The name must be unique among the shops (case and spacing ignored).
 */
class ShopViewModel(
    private val manager: ShopManager,
    private val target: ShopFormTarget,
    private val openShopId: Int,
    private val readOpenProfile: suspend () -> ShopProfile?
) : ViewModel() {

    private val _state = MutableStateFlow(ShopFormState())
    val state: StateFlow<ShopFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val details = when (target) {
                ShopFormTarget.New -> manager.details(openShopId, readOpenProfile)?.let { open ->
                    ShopDetails(name = "", address = null, ownerName = open.ownerName, phone = open.phone)
                }
                is ShopFormTarget.Existing -> manager.details(target.id, readOpenProfile)
            }
            _state.update {
                it.copy(
                    name = details?.name.orEmpty(),
                    ownerName = details?.ownerName.orEmpty(),
                    phone = details?.phone.orEmpty(),
                    address = details?.address.orEmpty(),
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
                val details = ShopDetails(shop.name, shop.address, shop.ownerName, shop.phone)
                viewModelScope.launch {
                    val change = when (target) {
                        ShopFormTarget.New -> manager.create(details)
                        is ShopFormTarget.Existing -> manager.edit(target.id, details)
                    }
                    when (change) {
                        is ShopChange.Done -> _state.update {
                            it.copy(
                                name = shop.name,
                                ownerName = shop.ownerName.orEmpty(),
                                phone = shop.phone.orEmpty(),
                                address = shop.address.orEmpty(),
                                isSaving = false,
                                saved = target is ShopFormTarget.Existing,
                                createdId = if (target is ShopFormTarget.New) change.shop.id else null
                            )
                        }
                        is ShopChange.BadName -> _state.update {
                            val error = if (change.problem == ShopNameProblem.DUPLICATE) FormError.DUPLICATE else FormError.REQUIRED
                            it.copy(isSaving = false, errors = it.errors.copy(name = error))
                        }
                        ShopChange.TooMany -> _state.update { it.copy(isSaving = false, tooMany = true) }
                    }
                }
            }
        }
    }
}

package com.example.dukanhisabnext.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.ShopRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the Settings screen needs from the single shop profile row. Any part may be null. */
data class SettingsUi(
    val ownerName: String?,
    val ownerPhone: String?,
    val shopName: String?
)

/**
 * Logic for the Settings screen. It watches the shop profile row, so when "দোকানের তথ্য" or the
 * owner form is saved, the owner card and the shop button update by themselves.
 */
class SettingsViewModel(repo: ShopRepository) : ViewModel() {

    /** null while loading (so the screen does not flash the wrong shop button label). */
    val ui: StateFlow<SettingsUi?> = repo.observe()
        .map { profile ->
            SettingsUi(
                ownerName = profile?.ownerName,
                ownerPhone = profile?.phone,
                shopName = profile?.name
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

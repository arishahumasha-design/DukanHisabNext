package com.example.dukanhisabnext.ui.sell

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.SaleRepository
import com.example.dukanhisabnext.data.entity.Sale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * A temporary receipt page, read from the saved sale. The real receipt screen comes in the
 * next step. The change (ফেরত) is not stored in the database, so it arrives in the route.
 */
class ReceiptPlaceholderViewModel(
    sales: SaleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val saleId: Long = requireNotNull(savedStateHandle.get<Long>("saleId")) {
        "receipt needs a saleId"
    }

    /** The change that was given back (paisa). 0 when there was none or the receipt is opened later. */
    val change: Long = savedStateHandle.get<Long>("change") ?: 0L

    /** The saved sale. null while loading. */
    val sale: StateFlow<Sale?> = flow { emit(sales.getSale(saleId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

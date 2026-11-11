package com.example.dukanhisabnext.ui.receipt

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.ReceiptRepository
import com.example.dukanhisabnext.receipt.ReceiptData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

sealed interface ReceiptUiState {
    data object Loading : ReceiptUiState
    /** No sale with this id (or no id at all). */
    data object NotFound : ReceiptUiState
    data class Loaded(val data: ReceiptData) : ReceiptUiState
}

/**
 * Logic for the receipt screen. It loads a SAVED sale by the id in the route, never the cart,
 * so the same screen works right after a save and later from a sales list.
 * The route also carries the change (ফেরত): 0 when the receipt is opened later.
 */
class ReceiptViewModel(
    repo: ReceiptRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val saleId: Long? = savedStateHandle.get<Long>("saleId")
    private val change: Long = savedStateHandle.get<Long>("change") ?: 0L

    val state: StateFlow<ReceiptUiState> = flow {
        val id = saleId
        val data = if (id == null) {
            null
        } else {
            try {
                repo.load(id, change)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // a read problem is shown as the same calm "not found" message
            }
        }
        emit(if (data == null) ReceiptUiState.NotFound else ReceiptUiState.Loaded(data))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiptUiState.Loading)
}

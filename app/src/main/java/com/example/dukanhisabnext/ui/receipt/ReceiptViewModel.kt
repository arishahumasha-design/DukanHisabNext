package com.example.dukanhisabnext.ui.receipt

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.cancel.CancelFeedback
import com.example.dukanhisabnext.cancel.cancelFeedback
import com.example.dukanhisabnext.cancel.closesDialog
import com.example.dukanhisabnext.cancel.limitReasonInput
import com.example.dukanhisabnext.data.ReceiptRepository
import com.example.dukanhisabnext.data.SaleRepository
import com.example.dukanhisabnext.receipt.ReceiptData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ReceiptUiState {
    data object Loading : ReceiptUiState
    /** No sale with this id (or no id at all). */
    data object NotFound : ReceiptUiState
    data class Loaded(val data: ReceiptData) : ReceiptUiState
}

/** The cancel dialog and what happened last. */
data class CancelUiState(
    val dialogOpen: Boolean = false,
    val reason: String = "",
    /** True while the cancel is running: the buttons are disabled so a double tap does nothing. */
    val cancelling: Boolean = false,
    val feedback: CancelFeedback? = null
)

/**
 * Logic for the receipt screen. It loads a SAVED sale by the id in the route, never the cart,
 * so the same screen works right after a save and later from a sales list.
 * The route also carries the change (ফেরত): 0 when the receipt is opened later.
 * It also runs "বিক্রি বাতিল": after a cancel the receipt is loaded again and shows the banner.
 */
class ReceiptViewModel(
    private val repo: ReceiptRepository,
    private val sales: SaleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val saleId: Long? = savedStateHandle.get<Long>("saleId")
    private val change: Long = savedStateHandle.get<Long>("change") ?: 0L

    /** Every change of this number loads the receipt again (after a cancel). */
    private val reload = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ReceiptUiState> = reload.flatMapLatest {
        flow {
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
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiptUiState.Loading)

    private val _cancel = MutableStateFlow(CancelUiState())
    val cancel: StateFlow<CancelUiState> = _cancel.asStateFlow()

    fun onCancelClick() {
        _cancel.update { if (it.cancelling) it else CancelUiState(dialogOpen = true) }
    }

    fun onDismissDialog() {
        _cancel.update { if (it.cancelling) it else it.copy(dialogOpen = false, reason = "") }
    }

    fun onReasonChange(text: String) {
        _cancel.update { it.copy(reason = limitReasonInput(text), feedback = null) }
    }

    fun onMessageShown() {
        _cancel.update { it.copy(feedback = null) }
    }

    /** "হ্যাঁ, বাতিল করুন". A second tap while the first is running does nothing. */
    fun onConfirm() {
        val id = saleId ?: return
        val current = _cancel.value
        if (current.cancelling) return
        _cancel.update { it.copy(cancelling = true, feedback = null) }

        viewModelScope.launch {
            try {
                val result = sales.cancelSale(id, current.reason)
                val feedback = cancelFeedback(result)
                if (closesDialog(result)) {
                    // Cancelled now, or already cancelled before: load the receipt again, so the banner shows.
                    reload.update { it + 1 }
                    _cancel.value = CancelUiState(feedback = feedback)
                } else {
                    _cancel.update { it.copy(cancelling = false, feedback = feedback) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The repository saves everything in one transaction, so nothing was changed.
                _cancel.value = CancelUiState(feedback = CancelFeedback.UNEXPECTED)
            }
        }
    }
}

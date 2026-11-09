package com.example.dukanhisabnext.ui.products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.ProductRepository
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.StockMovement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StockFormState(
    val mode: StockMode = StockMode.PURCHASE,
    val quantity: String = "",
    val unitCost: String = "",
    /** The "also update the product's cost price" checkbox. ON by default. */
    val updateCostPrice: Boolean = true,
    val counted: String = "",
    val note: String = "",
    val errors: StockFormErrors = StockFormErrors(),
    val isSaving: Boolean = false
)

/** Logic for the stock screen: add stock (purchase) or correct stock, plus the history. */
class StockAdjustViewModel(
    private val repo: ProductRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val productId: Long = requireNotNull(savedStateHandle.get<Long>("productId")) {
        "stock_adjust needs a productId"
    }

    /** The product (name and unit are shown at the top). null while loading. */
    val product: StateFlow<Product?> = flow { emit(repo.getProduct(productId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Current stock in thousandths, always the SUM of the movements. null while loading. */
    val stock: StateFlow<Long?> = repo.observeStock(productId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The history list, newest first. */
    val movements: StateFlow<List<StockMovement>> = repo.observeMovements(productId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _form = MutableStateFlow(StockFormState())
    val form: StateFlow<StockFormState> = _form.asStateFlow()

    fun onModeChange(mode: StockMode) = _form.update { it.copy(mode = mode, errors = StockFormErrors()) }

    fun onQuantityChange(text: String) =
        _form.update { it.copy(quantity = text, errors = it.errors.copy(quantity = null)) }

    fun onUnitCostChange(text: String) =
        _form.update { it.copy(unitCost = text, errors = it.errors.copy(unitCost = null)) }

    fun onUpdateCostPriceChange(checked: Boolean) = _form.update { it.copy(updateCostPrice = checked) }

    fun onCountedChange(text: String) =
        _form.update { it.copy(counted = text, errors = it.errors.copy(counted = null)) }

    fun onNoteChange(text: String) = _form.update { it.copy(note = text) }

    fun save() {
        val f = _form.value
        val currentStock = stock.value
        if (f.isSaving || currentStock == null) return

        val note = f.note.trim().ifEmpty { null }
        when (val result = validateStockForm(f.mode, f.quantity, f.unitCost, f.counted, currentStock)) {
            is StockFormResult.Invalid -> _form.update { it.copy(errors = result.errors) }

            is StockFormResult.Purchase -> {
                _form.update { it.copy(isSaving = true) }
                viewModelScope.launch {
                    repo.addPurchase(
                        productId = productId,
                        quantity = result.quantity,
                        unitCost = result.unitCost, // always saved on the movement
                        updateCostPrice = f.updateCostPrice, // only matters if a price was entered
                        note = note
                    )
                    resetFormKeepingMode(f.mode)
                }
            }

            is StockFormResult.Correction -> {
                _form.update { it.copy(isSaving = true) }
                viewModelScope.launch {
                    repo.correctStock(productId, result.counted, note)
                    resetFormKeepingMode(f.mode)
                }
            }
        }
    }

    /** After saving we stay on the screen: the new line appears in the history right away. */
    private fun resetFormKeepingMode(mode: StockMode) {
        _form.value = StockFormState(mode = mode)
    }
}

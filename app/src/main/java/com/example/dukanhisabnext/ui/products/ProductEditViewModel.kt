package com.example.dukanhisabnext.ui.products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.ProductRepository
import com.example.dukanhisabnext.data.entity.Category
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.util.formatQuantityPlain
import com.example.dukanhisabnext.util.formatTakaPlain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the add/edit form shows. Numbers are kept as the TEXT the user typed. */
data class ProductFormState(
    val name: String = "",
    val categoryId: Long? = null,
    val unit: ProductUnit = ProductUnit.PIECE,
    val sellPrice: String = "",
    val costPrice: String = "",
    val openingStock: String = "",
    val lowStockLevel: String = "",
    val errors: ProductFormErrors = ProductFormErrors(),
    val duplicateNameWarning: Boolean = false,
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    /** Becomes true after a successful save/archive: the screen then closes itself. */
    val finished: Boolean = false,
    // The "new category" pop-up lives here too, so it survives rotation.
    val categoryDialogOpen: Boolean = false,
    val categoryName: String = "",
    val categoryError: FormError? = null
)

/**
 * Logic for the add/edit product screen. The product id comes from the navigation route
 * (SavedStateHandle holds the route arguments). No id (-1) means "new product".
 */
class ProductEditViewModel(
    private val repo: ProductRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val productId: Long? = savedStateHandle.get<Long>("productId")?.takeIf { it >= 0 }

    /** The product as it was loaded. An edit copies it and changes only the form fields. */
    private var loaded: Product? = null

    private val _state = MutableStateFlow(
        ProductFormState(isNew = productId == null, isLoading = productId != null)
    )
    val state: StateFlow<ProductFormState> = _state.asStateFlow()

    val categories: StateFlow<List<Category>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (productId != null) load(productId)
    }

    private fun load(id: Long) {
        viewModelScope.launch {
            val product = repo.getProduct(id)
            if (product == null) {
                _state.update { it.copy(isLoading = false, finished = true) } // nothing to edit
                return@launch
            }
            loaded = product
            _state.update {
                it.copy(
                    name = product.name,
                    categoryId = product.categoryId,
                    unit = product.unit,
                    sellPrice = formatTakaPlain(product.sellPrice),
                    costPrice = product.costPrice?.let(::formatTakaPlain).orEmpty(),
                    lowStockLevel = product.lowStockLevel?.let(::formatQuantityPlain).orEmpty(),
                    isLoading = false
                )
            }
        }
    }

    // ---- Typing in the form. Typing in a box clears that box's old error. ----

    fun onNameChange(text: String) {
        _state.update { it.copy(name = text, errors = it.errors.copy(name = null)) }
        // Gentle warning (not a block): another active product has the same name.
        viewModelScope.launch {
            val exists = text.isNotBlank() && repo.productNameExists(text, excludeId = productId)
            _state.update { if (it.name == text) it.copy(duplicateNameWarning = exists) else it }
        }
    }

    fun onCategorySelected(categoryId: Long?) = _state.update { it.copy(categoryId = categoryId) }

    fun onUnitSelected(unit: ProductUnit) = _state.update { it.copy(unit = unit) }

    fun onSellPriceChange(text: String) =
        _state.update { it.copy(sellPrice = text, errors = it.errors.copy(sellPrice = null)) }

    fun onCostPriceChange(text: String) =
        _state.update { it.copy(costPrice = text, errors = it.errors.copy(costPrice = null)) }

    fun onOpeningStockChange(text: String) =
        _state.update { it.copy(openingStock = text, errors = it.errors.copy(openingStock = null)) }

    fun onLowStockLevelChange(text: String) =
        _state.update { it.copy(lowStockLevel = text, errors = it.errors.copy(lowStockLevel = null)) }

    // ---- New category pop-up ----

    fun openCategoryDialog() =
        _state.update { it.copy(categoryDialogOpen = true, categoryName = "", categoryError = null) }

    fun closeCategoryDialog() = _state.update { it.copy(categoryDialogOpen = false) }

    fun onCategoryNameChange(text: String) =
        _state.update { it.copy(categoryName = text, categoryError = null) }

    fun confirmNewCategory() {
        val name = _state.value.categoryName.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(categoryError = FormError.REQUIRED) }
            return
        }
        viewModelScope.launch {
            val newId = repo.addCategory(name)
            if (newId == null) {
                _state.update { it.copy(categoryError = FormError.DUPLICATE) }
            } else {
                // Close the pop-up and select the category we just made.
                _state.update { it.copy(categoryDialogOpen = false, categoryId = newId) }
            }
        }
    }

    // ---- Save and archive ----

    fun save() {
        val s = _state.value
        if (s.isSaving || s.isLoading) return

        val result = validateProductForm(
            ProductFormInput(
                name = s.name,
                sellPrice = s.sellPrice,
                costPrice = s.costPrice,
                openingStock = s.openingStock,
                lowStockLevel = s.lowStockLevel,
                isNew = s.isNew
            )
        )
        when (result) {
            is ProductFormResult.Invalid -> _state.update { it.copy(errors = result.errors) }
            is ProductFormResult.Valid -> {
                _state.update { it.copy(errors = ProductFormErrors(), isSaving = true) }
                val checked = result.product
                viewModelScope.launch {
                    val original = loaded
                    if (productId == null || original == null) {
                        // createdAt/updatedAt are placeholders: the repository sets the real times.
                        repo.createProduct(
                            Product(
                                name = checked.name,
                                categoryId = s.categoryId,
                                unit = s.unit,
                                sellPrice = checked.sellPrice,
                                costPrice = checked.costPrice,
                                lowStockLevel = checked.lowStockLevel,
                                createdAt = 0,
                                updatedAt = 0
                            ),
                            openingStock = checked.openingStock
                        )
                    } else {
                        // The unit is locked after creation, so it is copied from the original.
                        repo.updateProduct(
                            original.copy(
                                name = checked.name,
                                categoryId = s.categoryId,
                                sellPrice = checked.sellPrice,
                                costPrice = checked.costPrice,
                                lowStockLevel = checked.lowStockLevel
                            )
                        )
                    }
                    _state.update { it.copy(isSaving = false, finished = true) }
                }
            }
        }
    }

    fun archive() {
        val id = productId ?: return
        viewModelScope.launch {
            repo.setArchived(id, archived = true)
            _state.update { it.copy(finished = true) }
        }
    }
}

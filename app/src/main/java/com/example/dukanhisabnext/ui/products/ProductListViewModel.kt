package com.example.dukanhisabnext.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.ProductRepository
import com.example.dukanhisabnext.data.dao.ProductWithStock
import com.example.dukanhisabnext.data.entity.Category
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds what the product list screen needs: the search text, the chosen category,
 * the "show archived" switch, and the live list. The screen only draws this.
 * A ViewModel survives screen rotation, so nothing is lost when the phone turns.
 */
class ProductListViewModel(private val repo: ProductRepository) : ViewModel() {

    // MutableStateFlow = a box holding the current value that screens can watch.
    // We keep the writable box private and give the screen a read-only view.
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    val categories: StateFlow<List<Category>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The product list. null means "still loading". Whenever the search text, category or
     * switch changes, flatMapLatest drops the old database query and starts a new one.
     * Whenever the database changes (a stock movement, an edit...), the list re-emits by itself.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val products: StateFlow<List<ProductWithStock>?> =
        combine(_query, _selectedCategoryId, _showArchived) { query, categoryId, archived ->
            Triple(query.trim(), categoryId, archived)
        }
            .flatMapLatest { (query, categoryId, archived) ->
                repo.observeProducts(isActive = !archived, query = query, categoryId = categoryId)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun onCategorySelected(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun onShowArchivedChange(show: Boolean) {
        _showArchived.value = show
    }

    fun restore(productId: Long) {
        viewModelScope.launch { repo.setArchived(productId, archived = false) }
    }
}

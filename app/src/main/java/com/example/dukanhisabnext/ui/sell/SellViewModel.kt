package com.example.dukanhisabnext.ui.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.ProductRepository
import com.example.dukanhisabnext.data.dao.ProductWithStock
import com.example.dukanhisabnext.data.entity.Category
import com.example.dukanhisabnext.data.entity.Product
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Logic for screen A, the product picker. The cart itself lives in [CartStore] (app-wide),
 * so this ViewModel only holds the search text and the category filter, and passes taps
 * on to the cart.
 */
class SellViewModel(
    repo: ProductRepository,
    private val cart: CartStore
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    val categories: StateFlow<List<Category>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Products you may sell (archived ones are hidden), searched by name and filtered by
     * category. null = still loading.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val products: StateFlow<List<ProductWithStock>?> =
        combine(_query, _selectedCategoryId) { query, categoryId -> query.trim() to categoryId }
            .flatMapLatest { (query, categoryId) ->
                repo.observeProducts(isActive = true, query = query, categoryId = categoryId)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What is in the cart (for the "কার্টে: N" tag on each product). */
    val cartState: StateFlow<CartState> = cart.state

    /** The cart worked out: the bottom bar shows the total and how many lines there are. */
    val summary: StateFlow<CartSummary> = cart.state
        .map(::summarize)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), summarize(cart.state.value))

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun onCategorySelected(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun onProductTapped(product: Product) = cart.addProduct(product)
}

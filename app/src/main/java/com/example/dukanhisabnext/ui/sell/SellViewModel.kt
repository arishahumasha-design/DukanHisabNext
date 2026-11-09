package com.example.dukanhisabnext.ui.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.ProductRepository
import com.example.dukanhisabnext.data.dao.ProductWithStock
import com.example.dukanhisabnext.data.entity.Product
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Logic for the Sell screen. The cart itself lives in [CartStore] (app-wide), so this
 * ViewModel only holds the search text and passes the screen's actions to the cart.
 */
class SellViewModel(
    repo: ProductRepository,
    private val cart: CartStore
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Products you may sell (archived ones are hidden), searched by name. null = loading. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val products: StateFlow<List<ProductWithStock>?> = _query
        .flatMapLatest { repo.observeProducts(isActive = true, query = it.trim(), categoryId = null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Current stock per product, for the "more than in stock" warning on cart lines. */
    val stocks: StateFlow<Map<Long, Long>> = repo.observeStocks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** What was typed in the cart. */
    val cartState: StateFlow<CartState> = cart.state

    /** The cart read and worked out: line totals, subtotal, total, may-continue. */
    val summary: StateFlow<CartSummary> = cart.state
        .map(::summarize)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), summarize(cart.state.value))

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun onProductTapped(product: Product) = cart.addProduct(product)

    fun onQuantityChange(productId: Long, text: String) = cart.setQuantityText(productId, text)

    fun onUnitPriceChange(productId: Long, text: String) = cart.setUnitPriceText(productId, text)

    fun onRemoveLine(productId: Long) = cart.removeLine(productId)

    fun onDiscountChange(text: String) = cart.setDiscountText(text)

    fun onDeliveryChange(text: String) = cart.setDeliveryText(text)
}

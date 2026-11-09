package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.data.entity.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the cart for the whole app. It lives in DokanHisabApp, not in a screen, so the cart
 * survives rotating the phone AND switching tabs. The next steps (payment, receipt) read the
 * same cart. It is lost only when the app process is closed (fine for the MVP).
 */
class CartStore {
    private val _state = MutableStateFlow(CartState())
    val state: StateFlow<CartState> = _state.asStateFlow()

    /** Tap on a product: new line (quantity 1) or +1 on its existing line. */
    fun addProduct(product: Product) = _state.update {
        it.withProductAdded(product.id, product.name, product.unit, product.sellPrice)
    }

    fun setQuantityText(productId: Long, text: String) = _state.update { it.withQuantityText(productId, text) }

    fun setUnitPriceText(productId: Long, text: String) = _state.update { it.withUnitPriceText(productId, text) }

    fun removeLine(productId: Long) = _state.update { it.withoutLine(productId) }

    fun setDiscountText(text: String) = _state.update { it.withDiscountText(text) }

    fun setDeliveryText(text: String) = _state.update { it.withDeliveryText(text) }

    /** Empties the cart (used after a sale is saved, from step 4). */
    fun clear() {
        _state.value = CartState()
    }
}

package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.data.entity.Product
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the sale in progress for the whole app: the cart, the chosen customer, and the
 * "draft id". It lives in DokanHisabApp, not in a screen, so it survives rotating the phone
 * AND switching tabs, and the cart, payment and picker screens all see the same data.
 * It is lost only when the app process is closed (fine for the MVP).
 *
 * [newDraftId] makes a new id; tests pass their own.
 */
class CartStore(private val newDraftId: () -> String = { UUID.randomUUID().toString() }) {
    private val _state = MutableStateFlow(CartState())
    val state: StateFlow<CartState> = _state.asStateFlow()

    private val _customerId = MutableStateFlow<Long?>(null)

    /** The customer chosen for this sale (on the payment screen), or null. */
    val customerId: StateFlow<Long?> = _customerId.asStateFlow()

    private val _unavailableProductIds = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * Cart lines whose product could not be sold when saving (archived or missing).
     * The cart screen marks them so the shopkeeper can remove them.
     */
    val unavailableProductIds: StateFlow<Set<Long>> = _unavailableProductIds.asStateFlow()

    /**
     * ONE id per cart. Saving the same cart twice (a double tap, or a retry after the phone
     * hung) sends the same id, and SaleRepository then returns the first sale instead of
     * saving again. The id is made when the cart is created and replaced by [clear] - never on
     * a tap or an edit.
     */
    @Volatile
    var draftId: String = newDraftId()
        private set

    /** Tap on a product: new line (quantity 1) or +1 on its existing line. */
    fun addProduct(product: Product) = _state.update {
        it.withProductAdded(product.id, product.name, product.unit, product.sellPrice)
    }

    fun setQuantityText(productId: Long, text: String) = _state.update { it.withQuantityText(productId, text) }

    fun setUnitPriceText(productId: Long, text: String) = _state.update { it.withUnitPriceText(productId, text) }

    fun removeLine(productId: Long) {
        _state.update { it.withoutLine(productId) }
        _unavailableProductIds.update { it - productId } // a removed line is no longer a problem
    }

    fun setDiscountOn(on: Boolean) = _state.update { it.withDiscountOn(on) }

    fun setDiscountUnit(unit: DiscountUnit) = _state.update { it.withDiscountUnit(unit) }

    fun setDiscountText(text: String) = _state.update { it.withDiscountText(text) }

    fun setDeliveryOn(on: Boolean) = _state.update { it.withDeliveryOn(on) }

    fun setDeliveryText(text: String) = _state.update { it.withDeliveryText(text) }

    fun setCustomer(customerId: Long?) {
        _customerId.value = customerId
    }

    /** Marks lines that could not be sold, so the cart screen can show them. */
    fun markUnavailable(productIds: Collection<Long>) {
        _unavailableProductIds.update { it + productIds }
    }

    /** A new, empty cart: no lines, no customer, no marks, and a NEW draft id. */
    fun clear() {
        _state.value = CartState()
        _customerId.value = null
        _unavailableProductIds.value = emptySet()
        draftId = newDraftId()
    }
}

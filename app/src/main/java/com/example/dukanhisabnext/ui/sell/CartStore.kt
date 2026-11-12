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
 *
 * Since M9 it also survives Android closing the app in the background: every change is written
 * to [storage] (a small file), and the cart is read back when the app starts. The draft id comes
 * back too, so saving the same cart after a restart can never save it twice.
 *
 * [newDraftId] makes a new id; tests pass their own.
 */
class CartStore(
    private val storage: CartStorage = CartStorage.None,
    private val newDraftId: () -> String = { UUID.randomUUID().toString() } // last, so CartStore { "id" } works
) {
    private val restored = storage.load()

    private val _state = MutableStateFlow(restored?.state ?: CartState())
    val state: StateFlow<CartState> = _state.asStateFlow()

    private val _customerId = MutableStateFlow(restored?.customerId)

    /** The customer chosen for this sale (on the payment screen), or null. */
    val customerId: StateFlow<Long?> = _customerId.asStateFlow()

    private val _unavailableProductIds = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * Cart lines whose product could not be sold when saving (archived or missing).
     * The cart screen marks them so the shopkeeper can remove them.
     */
    val unavailableProductIds: StateFlow<Set<Long>> = _unavailableProductIds.asStateFlow()

    private val _removedOnRestart = MutableStateFlow<List<String>>(emptyList())

    /**
     * Names of products that were in the saved cart but can no longer be sold (deleted or archived
     * while the app was closed); they were taken out. The sell and cart screens show a note once.
     */
    val removedOnRestart: StateFlow<List<String>> = _removedOnRestart.asStateFlow()

    /**
     * ONE id per cart. Saving the same cart twice (a double tap, or a retry after the phone
     * hung or the app was closed) sends the same id, and SaleRepository then returns the first
     * sale instead of saving again. The id is made when the cart is created and replaced by
     * [clear] - never on a tap or an edit.
     */
    @Volatile
    var draftId: String = restored?.draftId ?: newDraftId()
        private set

    /** Writes the current cart to the storage (the newest state always wins). */
    @Synchronized
    private fun persist() {
        storage.save(SavedCart(_state.value, _customerId.value, draftId))
    }

    private inline fun change(block: () -> Unit) {
        block()
        persist()
    }

    /** Tap on a product: new line (quantity 1) or +1 on its existing line. */
    fun addProduct(product: Product) = change {
        _state.update { it.withProductAdded(product.id, product.name, product.unit, product.sellPrice) }
    }

    fun setQuantityText(productId: Long, text: String) = change { _state.update { it.withQuantityText(productId, text) } }

    fun setUnitPriceText(productId: Long, text: String) = change { _state.update { it.withUnitPriceText(productId, text) } }

    fun removeLine(productId: Long) = change {
        _state.update { it.withoutLine(productId) }
        _unavailableProductIds.update { it - productId } // a removed line is no longer a problem
    }

    fun setDiscountOn(on: Boolean) = change { _state.update { it.withDiscountOn(on) } }

    fun setDiscountUnit(unit: DiscountUnit) = change { _state.update { it.withDiscountUnit(unit) } }

    fun setDiscountText(text: String) = change { _state.update { it.withDiscountText(text) } }

    fun setDeliveryOn(on: Boolean) = change { _state.update { it.withDeliveryOn(on) } }

    fun setDeliveryText(text: String) = change { _state.update { it.withDeliveryText(text) } }

    fun setCustomer(customerId: Long?) = change {
        _customerId.value = customerId
    }

    /** Marks lines that could not be sold, so the cart screen can show them. */
    fun markUnavailable(productIds: Collection<Long>) {
        _unavailableProductIds.update { it + productIds }
    }

    /** A new, empty cart: no lines, no customer, no marks, and a NEW draft id. */
    fun clear() = change {
        _state.value = CartState()
        _customerId.value = null
        _unavailableProductIds.value = emptySet()
        _removedOnRestart.value = emptyList()
        draftId = newDraftId()
    }

    /**
     * After a restart: takes out the lines whose product is gone or archived, and forgets a
     * customer who no longer exists. [sellableIds] gives, for some product ids, the ones that can
     * still be sold; [customerExists] checks a customer. The names taken out go to [removedOnRestart].
     */
    suspend fun dropWhatCannotBeSold(
        sellableIds: suspend (List<Long>) -> Set<Long>,
        customerExists: suspend (Long) -> Boolean
    ) {
        val lines = _state.value.lines
        val sellable = if (lines.isEmpty()) emptySet() else sellableIds(lines.map { it.productId })
        val gone = lines.filter { it.productId !in sellable }
        val customer = _customerId.value
        val customerGone = customer != null && !customerExists(customer)
        if (gone.isEmpty() && !customerGone) return
        change {
            if (gone.isNotEmpty()) {
                val goneIds = gone.map { it.productId }.toSet()
                _state.update { s -> s.copy(lines = s.lines.filterNot { it.productId in goneIds }) }
                _removedOnRestart.value = gone.map { it.name }
            }
            if (customerGone) _customerId.value = null
        }
    }

    /** The note about removed products was read. */
    fun dismissRemovedNote() {
        _removedOnRestart.value = emptyList()
    }
}

package com.example.dukanhisabnext.ui.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.ProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Logic for screen B, the cart. It shares the SAME [CartStore] as the picker, so going back
 * and forth (or rotating, or switching tabs) never loses anything. This ViewModel only passes
 * the screen's actions on to the cart.
 */
class CartViewModel(
    repo: ProductRepository,
    private val cart: CartStore
) : ViewModel() {

    /** Current stock per product, for the "more than in stock" warning on cart lines. */
    val stocks: StateFlow<Map<Long, Long>> = repo.observeStocks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * The buying price of each product in the cart that has one, for the "below cost" warning.
     * Read again whenever the set of products in the cart changes (not on every keystroke).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val costPrices: StateFlow<Map<Long, Long>> = cart.state
        .map { state -> state.lines.map { it.productId }.distinct().sorted() }
        .distinctUntilChanged()
        .mapLatest { ids -> repo.costPricesOf(ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** What was typed in the cart (the text of each box and the switches). */
    val cartState: StateFlow<CartState> = cart.state

    /** The cart read and worked out: line totals, subtotal, discount, delivery, total. */
    val summary: StateFlow<CartSummary> = cart.state
        .map(::summarize)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), summarize(cart.state.value))

    /** Lines whose product could not be sold when saving (archived or missing): marked in red. */
    val unavailable: StateFlow<Set<Long>> = cart.unavailableProductIds

    /** The continue button: the cart must be fine AND have no marked (unsellable) line. */
    val canContinue: StateFlow<Boolean> = combine(summary, cart.unavailableProductIds) { s, marked ->
        s.canContinue && s.lines.none { it.input.productId in marked }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Today in Dhaka: the sale day is checked against it. */
    fun today(): java.time.LocalDate = cart.today()

    /** True once after a restart when a saved sale day had to go back to today. */
    val dateReset: StateFlow<Boolean> = cart.dateReset

    fun onDateResetShown() = cart.dismissDateReset()

    fun onSaleDayPicked(day: java.time.LocalDate) = cart.setSaleDay(day)

    fun onOldDateConfirmed(day: java.time.LocalDate) = cart.confirmOldDate(day)

    fun onLineWarranty(productId: Long, value: Int?, unit: com.example.dukanhisabnext.data.entity.WarrantyUnit?) =
        cart.setLineWarranty(productId, value, unit)

    fun onQuantityChange(productId: Long, text: String) = cart.setQuantityText(productId, text)

    fun onUnitPriceChange(productId: Long, text: String) = cart.setUnitPriceText(productId, text)

    fun onRemoveLine(productId: Long) = cart.removeLine(productId)

    fun onDiscountOnChange(on: Boolean) = cart.setDiscountOn(on)

    fun onDiscountUnitChange(unit: DiscountUnit) = cart.setDiscountUnit(unit)

    fun onDiscountChange(text: String) = cart.setDiscountText(text)

    fun onDeliveryOnChange(on: Boolean) = cart.setDeliveryOn(on)

    fun onDeliveryChange(text: String) = cart.setDeliveryText(text)
}

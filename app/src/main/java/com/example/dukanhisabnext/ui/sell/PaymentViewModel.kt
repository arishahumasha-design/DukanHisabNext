package com.example.dukanhisabnext.ui.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.CustomerRepository
import com.example.dukanhisabnext.data.SaleRepository
import com.example.dukanhisabnext.data.SaleResult
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.util.formatTakaPlain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the payment screen keeps besides the cart: the typed amount, the method, and the save state. */
data class PaymentFormState(
    val receivedText: String,
    val method: PaymentMethod = PaymentMethod.CASH,
    /** Set when Save is pressed while a customer is still missing: the hint turns red. */
    val triedToSave: Boolean = false,
    val isSaving: Boolean = false,
    val error: PaymentError? = null,
    /** Set after a successful save: the screen then moves on to the receipt. */
    val savedSaleId: Long? = null,
    /** The change (ফেরত) of the sale just saved. It is not stored in the database. */
    val savedChange: Long = 0
)

/**
 * Logic for the payment screen. It reads the cart from the shared [CartStore], and saves the
 * sale with [SaleRepository.completeSale].
 */
class PaymentViewModel(
    private val sales: SaleRepository,
    customers: CustomerRepository,
    private val cart: CartStore
) : ViewModel() {

    /** The cart worked out (total, discount, delivery, lines). */
    val summary: StateFlow<CartSummary> = cart.state
        .map(::summarize)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), summarize(cart.state.value))

    // The amount received starts as the whole total (most sales are paid in full).
    private val _form = MutableStateFlow(
        PaymentFormState(receivedText = formatTakaPlain(summarize(cart.state.value).total))
    )
    val form: StateFlow<PaymentFormState> = _form.asStateFlow()

    /** Change (ফেরত) or due (বাকি), and whether saving is allowed. Updates live while typing. */
    val view: StateFlow<PaymentView> = combine(summary, _form, cart.customerId) { s, f, customerId ->
        paymentView(s, f.receivedText, hasCustomer = customerId != null)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        paymentView(summary.value, _form.value.receivedText, cart.customerId.value != null)
    )

    /** The customer chosen for this sale, or null. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val customer: StateFlow<Customer?> = cart.customerId
        .flatMapLatest { id -> if (id == null) flowOf(null) else customers.observeCustomer(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The chosen customer's balance before this sale (paisa), for the customer card. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val customerBalance: StateFlow<Long> = cart.customerId
        .flatMapLatest { id -> if (id == null) flowOf(0L) else customers.observeBalance(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun onReceivedChange(text: String) = _form.update { it.copy(receivedText = text, error = null) }

    /** "পুরো টাকা": put the whole total back in the box. */
    fun onFullAmount() = _form.update {
        it.copy(receivedText = formatTakaPlain(summarize(cart.state.value).total), error = null)
    }

    fun onMethodChange(method: PaymentMethod) = _form.update { it.copy(method = method) }

    fun onRemoveCustomer() = cart.setCustomer(null)

    fun save() {
        val f = _form.value
        // Disabled while saving, and never again after a saved sale (a double tap does nothing).
        if (f.isSaving || f.savedSaleId != null) return

        // One snapshot of the cart for this attempt.
        val cartSummary = summarize(cart.state.value)
        val customerId = cart.customerId.value
        val current = paymentView(cartSummary, f.receivedText, hasCustomer = customerId != null)
        if (!current.readyToSave) return
        if (current.needsCustomer) {
            _form.update { it.copy(triedToSave = true) } // the hint under the customer turns red
            return
        }

        _form.update { it.copy(isSaving = true, error = null) }
        // The cart's own id: made once per cart, so a retry or double tap is recognised.
        val draft = buildDraft(cartSummary, current.received!!, f.method, customerId, cart.draftId)
        val namesById = cartSummary.lines.associate { it.input.productId to it.input.name }

        viewModelScope.launch {
            val result = try {
                sales.completeSale(draft)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // unexpected: the transaction rolled back, so nothing was saved
            }

            when (result) {
                null -> _form.update { it.copy(isSaving = false, error = PaymentError.SaveFailed) }

                is SaleResult.Saved -> {
                    // First say "saved" (the screen then shows nothing and moves on), then empty
                    // the cart. This order stops the screen flashing an empty cart.
                    _form.update {
                        it.copy(isSaving = false, savedSaleId = result.saleId, savedChange = result.amounts.change)
                    }
                    cart.clear() // also makes a NEW draft id for the next cart
                }

                else -> {
                    if (result is SaleResult.ProductsUnavailable) cart.markUnavailable(result.productIds)
                    if (result is SaleResult.CustomerNotFound) cart.setCustomer(null)
                    _form.update { it.copy(isSaving = false, error = paymentErrorOf(result, namesById)) }
                }
            }
        }
    }
}

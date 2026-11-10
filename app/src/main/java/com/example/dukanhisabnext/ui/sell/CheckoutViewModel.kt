package com.example.dukanhisabnext.ui.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.CustomerRepository
import com.example.dukanhisabnext.data.SaleRepository
import com.example.dukanhisabnext.data.SaleResult
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.ui.customers.CustomerFormErrors
import com.example.dukanhisabnext.ui.customers.ValidatedCustomer
import com.example.dukanhisabnext.util.formatTakaPlain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which screen started the save (that screen moves on to the receipt). */
enum class CheckoutScreen { PAYMENT, DUE }

/**
 * The customer boxes (shown while nobody is selected). The selected customer's id lives in the
 * CartStore; when one is selected, a card is shown instead of these boxes.
 */
data class CustomerInputState(
    val on: Boolean = false,          // the switch (the নতুন বাকি screen always shows the picker)
    val name: String = "",
    val phone: String = "",           // normal digits; shown after a fixed "+88"
    val address: String = "",
    val errors: CustomerFormErrors = CustomerFormErrors()
)

data class CheckoutState(
    val receivedText: String,         // "পেয়েছি", starts as the total
    val note: String = "",            // "মন্তব্য", saved as Sale.note
    val customer: CustomerInputState = CustomerInputState(),
    /** The due that has no customer yet (for the message), or null. */
    val customerNeededFor: Long? = null,
    val dueText: String = "",         // "বাকির পরিমাণ" on the নতুন বাকি screen
    val mobileChoiceOpen: Boolean = false,
    /** The "কাস্টমার তালিকা" dialog. */
    val listOpen: Boolean = false,
    val listQuery: String = "",
    val isSaving: Boolean = false,
    val error: PaymentError? = null,
    val savedSaleId: Long? = null,
    /** The change (ফেরত) of the sale just saved. It is not stored in the database. */
    val savedChange: Long = 0,
    val savedFrom: CheckoutScreen? = null
)

/**
 * Logic for BOTH the payment screen and the "নতুন বাকি" screen. The two screens share ONE
 * instance (the নতুন বাকি screen borrows the payment screen's), so the customer, the note, the
 * amounts and the save state are the same on both - and are kept while the add-customer form is
 * open on top. Saving always goes through SaleRepository.completeSale with the cart's single
 * draft id.
 */
class CheckoutViewModel(
    private val sales: SaleRepository,
    private val customers: CustomerRepository,
    private val cart: CartStore
) : ViewModel() {

    /** The cart worked out (total, discount, delivery, lines). */
    val summary: StateFlow<CartSummary> = cart.state
        .map(::summarize)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), summarize(cart.state.value))

    private val _state = MutableStateFlow(
        CheckoutState(receivedText = formatTakaPlain(summarize(cart.state.value).total))
    )
    val state: StateFlow<CheckoutState> = _state.asStateFlow()

    /** Change or due for the typed "পেয়েছি". */
    val view: StateFlow<PaymentView> = combine(summary, _state) { s, st ->
        paymentView(s, st.receivedText, hasCustomer = true)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        paymentView(summary.value, _state.value.receivedText, hasCustomer = true)
    )

    /** The typed "বাকির পরিমাণ", checked live. */
    val due: StateFlow<DueView> = combine(summary, _state) { s, st -> dueView(s.total, st.dueText) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), dueView(summary.value.total, ""))

    /** The existing customer selected for this cart (picked, or created earlier for it), or null. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedCustomer: StateFlow<Customer?> = cart.customerId
        .flatMapLatest { id -> if (id == null) flowOf(null) else customers.observeCustomer(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Up to 5 existing customers matching what is typed in the name box (name or mobile, the same
     * search as the customer list), while nobody is selected.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val suggestions: StateFlow<List<Customer>> =
        combine(_state.map { it.customer.name.trim() }.distinctUntilChanged(), cart.customerId) { name, selected ->
            name to selected
        }
            .flatMapLatest { (name, selected) ->
                if (selected != null || name.isEmpty()) flowOf(emptyList())
                else customers.observeCustomerList(name).map { it.take(5) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The rows of the "কাস্টমার তালিকা" dialog: searched, sorted by name A to Z. null = loading. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val customerRows: StateFlow<List<Customer>?> = _state
        .map { it.listQuery }
        .distinctUntilChanged()
        .flatMapLatest { customers.observeCustomerList(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // =====================================================================================
    // Typing
    // =====================================================================================

    fun onReceivedChange(text: String) =
        _state.update { it.copy(receivedText = text, error = null, customerNeededFor = null) }

    fun onNoteChange(text: String) = _state.update { it.copy(note = text) }

    /**
     * The "কাস্টমার তথ্য" switch. OFF means no customer for this sale (a chosen customer is only
     * hidden, not forgotten, so switching on again brings the card back).
     */
    fun onCustomerSwitch(on: Boolean) = _state.update {
        it.copy(customer = it.customer.copy(on = on), customerNeededFor = if (on) it.customerNeededFor else null)
    }

    fun onCustomerNameChange(text: String) = _state.update {
        it.copy(
            customer = it.customer.copy(name = text, errors = it.customer.errors.copy(name = null)),
            customerNeededFor = if (text.isNotBlank()) null else it.customerNeededFor
        )
    }

    fun onCustomerPhoneChange(text: String) = _state.update {
        it.copy(customer = it.customer.copy(phone = text, errors = it.customer.errors.copy(phone = null)))
    }

    fun onCustomerAddressChange(text: String) =
        _state.update { it.copy(customer = it.customer.copy(address = text)) }

    // ---- Selecting a customer ----

    /** A suggestion or a dialog row was tapped: select that customer (a card replaces the boxes). */
    fun onPickCustomer(customer: Customer) {
        cart.setCustomer(customer.id)
        _state.update {
            it.copy(
                customer = CustomerInputState(on = true),
                customerNeededFor = null,
                listOpen = false,
                listQuery = ""
            )
        }
    }

    /** "বদলান": clear the selection and show the empty boxes again. */
    fun onChangeCustomer() {
        cart.setCustomer(null)
        _state.update { it.copy(customer = CustomerInputState(on = true)) }
    }

    // ---- The "কাস্টমার তালিকা" dialog ----

    fun onOpenCustomerList() = _state.update { it.copy(listOpen = true, listQuery = "") }

    fun onCloseCustomerList() = _state.update { it.copy(listOpen = false, listQuery = "") }

    fun onListQueryChange(text: String) = _state.update { it.copy(listQuery = text) }

    /** Creates the customer and REMEMBERS the id for this cart, so a retry never makes a second one. */
    private suspend fun createCustomer(customer: ValidatedCustomer): Long {
        val id = customers.addCustomer(
            Customer(
                name = customer.name, phone = customer.phone, address = customer.address,
                createdAt = 0, updatedAt = 0 // the repository sets the real times
            )
        )
        cart.setCustomer(id)
        return id
    }

    // =====================================================================================
    // The three buttons and the নতুন বাকি screen
    // =====================================================================================

    fun onCashTap() = saveNow(PaymentMethod.CASH)

    fun onMobileTap() {
        if (!isBusy()) _state.update { it.copy(mobileChoiceOpen = true) }
    }

    fun onMobileDismiss() = _state.update { it.copy(mobileChoiceOpen = false) }

    /** "বিকাশ" or "নগদ (Nagad)" was chosen in the small choice: save at once with it. */
    fun onMobileChosen(method: PaymentMethod) {
        _state.update { it.copy(mobileChoiceOpen = false) }
        saveNow(method)
    }

    /** Called just before the নতুন বাকি screen opens: set its starting due. */
    fun prepareDue() {
        val total = summarize(cart.state.value).total
        _state.update {
            it.copy(dueText = formatTakaPlain(defaultDuePaisa(total, it.receivedText)), error = null)
        }
    }

    fun onDueChange(text: String) = _state.update { it.copy(dueText = text, error = null) }

    /** The confirm button on the নতুন বাকি screen. */
    fun confirmDue() {
        if (isBusy()) return
        val cartSummary = summarize(cart.state.value)
        if (!cartSummary.canContinue) {
            _state.update { it.copy(error = PaymentError.SaleInvalid) }
            return
        }
        val checked = dueView(cartSummary.total, _state.value.dueText)
        val dueAmount = checked.due ?: return // the box shows its error
        save(
            from = CheckoutScreen.DUE,
            cartSummary = cartSummary,
            received = cartSummary.total - dueAmount, // paid = total - due
            method = PaymentMethod.CASH,              // only has meaning when paid > 0
            dueAmount = dueAmount,
            sectionOn = true                          // the customer picker is always shown there
        )
    }

    /** "নগদ টাকা" and the mobile choice: save at once. paid = min(received, total). */
    private fun saveNow(method: PaymentMethod) {
        if (isBusy()) return
        val cartSummary = summarize(cart.state.value)
        if (!cartSummary.canContinue) {
            _state.update { it.copy(error = PaymentError.SaleInvalid) }
            return
        }
        val current = paymentView(cartSummary, _state.value.receivedText, hasCustomer = true)
        val received = current.received ?: return // the "পেয়েছি" box shows its error
        if (!current.readyToSave) return
        save(
            from = CheckoutScreen.PAYMENT,
            cartSummary = cartSummary,
            received = received,
            method = method,
            dueAmount = current.due,
            sectionOn = _state.value.customer.on
        )
    }

    private fun isBusy(): Boolean = _state.value.let { it.isSaving || it.savedSaleId != null }

    /** The one way a sale is saved, from either screen. */
    private fun save(
        from: CheckoutScreen,
        cartSummary: CartSummary,
        received: Long,
        method: PaymentMethod,
        dueAmount: Long,
        sectionOn: Boolean
    ) {
        val st = _state.value
        val decision = customerDecision(
            sectionOn = sectionOn,
            selectedId = cart.customerId.value,
            name = st.customer.name,
            phone = st.customer.phone,
            address = st.customer.address,
            required = dueAmount > 0
        )
        if (decision is CustomerDecision.Missing) {
            // Do not save. Show why. When the switch is off it stays off: the screen then offers
            // a button to switch it on again.
            _state.update {
                it.copy(
                    customer = it.customer.copy(errors = decision.errors),
                    customerNeededFor = if (dueAmount > 0 && decision.errors.name != null) dueAmount else null
                )
            }
            return
        }

        // All buttons are disabled from here until the save has finished.
        _state.update { it.copy(isSaving = true, error = null, customerNeededFor = null, mobileChoiceOpen = false) }
        val namesById = cartSummary.lines.associate { it.input.productId to it.input.name }
        val note = st.note.trim().ifEmpty { null }

        viewModelScope.launch {
            try {
                val customerId = when (decision) {
                    is CustomerDecision.UseExisting -> decision.id
                    is CustomerDecision.CreateNew -> createCustomer(decision.customer) // remembered for a retry
                    CustomerDecision.NoCustomer -> null
                    is CustomerDecision.Missing -> error("handled above")
                }
                // The cart's single draft id: a double tap or a retry is recognised by the repository.
                val draft = buildDraft(cartSummary, received, method, customerId, cart.draftId, note)
                handleResult(sales.completeSale(draft), from, namesById)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Unexpected: the transaction rolled back, so nothing was saved.
                _state.update { it.copy(isSaving = false, error = PaymentError.SaveFailed) }
            }
        }
    }

    private fun handleResult(result: SaleResult, from: CheckoutScreen, namesById: Map<Long, String>) {
        when (result) {
            is SaleResult.Saved -> {
                // First mark it saved (the screen shows nothing and moves on), then empty the cart.
                _state.update {
                    it.copy(
                        isSaving = false,
                        savedSaleId = result.saleId,
                        savedChange = result.amounts.change,
                        savedFrom = from
                    )
                }
                cart.clear() // also forgets the customer and makes a NEW draft id
            }

            else -> {
                if (result is SaleResult.ProductsUnavailable) cart.markUnavailable(result.productIds)
                if (result is SaleResult.CustomerNotFound) {
                    cart.setCustomer(null)
                    _state.update { it.copy(customer = it.customer.copy(on = true)) }
                }
                _state.update { it.copy(isSaving = false, error = paymentErrorOf(result, namesById)) }
            }
        }
    }
}

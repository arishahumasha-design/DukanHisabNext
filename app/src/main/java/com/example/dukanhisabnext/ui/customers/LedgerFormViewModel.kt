package com.example.dukanhisabnext.ui.customers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.CustomerRepository
import com.example.dukanhisabnext.data.LedgerRepository
import com.example.dukanhisabnext.data.LedgerSaveResult
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.ledger.LedgerAction
import com.example.dukanhisabnext.ledger.LedgerError
import com.example.dukanhisabnext.ledger.LedgerFormState
import com.example.dukanhisabnext.ledger.SaveProblem
import com.example.dukanhisabnext.ledger.actionFromRoute
import com.example.dukanhisabnext.ledger.inputOf
import com.example.dukanhisabnext.ledger.limitNoteInput
import com.example.dukanhisabnext.ledger.newFormState
import com.example.dukanhisabnext.ledger.withCorrectionSign
import com.example.dukanhisabnext.util.DhakaTime
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the shared entry form shows. It lives in the ViewModel, so rotation keeps the typed values. */
data class LedgerFormUi(
    val form: LedgerFormState,
    /** Problems from the last save attempt. A box's problem goes away when that box is edited. */
    val errors: Set<LedgerError> = emptySet(),
    val problem: SaveProblem? = null,
    /** True while saving: the save button is disabled. */
    val saving: Boolean = false,
    /** True after a successful save: the screen goes back to the customer. */
    val saved: Boolean = false
)

/**
 * Logic of the ONE entry form used by টাকা পেলাম, বাকি দিলাম, পুরনো বাকি, সংশোধন and অগ্রিম ফেরত
 * দিলাম. A form id is made when the form opens, so a double tap on the save button (or a retry)
 * saves only once (LedgerRepository remembers the id).
 */
class LedgerFormViewModel(
    private val ledger: LedgerRepository,
    customers: CustomerRepository,
    savedStateHandle: SavedStateHandle,
    clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val customerId: Long = requireNotNull(savedStateHandle.get<Long>("customerId")) {
        "ledger_entry needs a customerId"
    }
    private val startAction: LedgerAction = actionFromRoute(savedStateHandle.get<String>("action")) ?: LedgerAction.PAYMENT

    /** Made once when the form opens. The same id is sent on every tap of the save button. */
    private val formId: String = UUID.randomUUID().toString()

    private val _ui = MutableStateFlow(LedgerFormUi(newFormState(startAction, DhakaTime.localDate(clock()))))
    val ui: StateFlow<LedgerFormUi> = _ui.asStateFlow()

    val customer: StateFlow<Customer?> = customers.observeCustomer(customerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The customer's balance now (null while loading). */
    val balance: StateFlow<Long?> = ledger.observeBalance(customerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The date picker never offers a day after this one. */
    val today: LocalDate = DhakaTime.localDate(clock())

    private fun edit(clearing: Set<LedgerError>, change: (LedgerFormState) -> LedgerFormState) {
        _ui.update { it.copy(form = change(it.form), errors = it.errors - clearing, problem = null) }
    }

    fun onAmountChange(text: String) = edit(AMOUNT_ERRORS) { it.copy(amountText = text) }
    fun onDateChange(date: LocalDate) = edit(DATE_ERRORS) { it.copy(date = date) }
    fun onMethodChange(method: PaymentMethod) = edit(METHOD_ERRORS) { it.copy(method = method) }
    fun onNoteChange(text: String) = edit(NOTE_ERRORS) { it.copy(note = limitNoteInput(text)) }

    /** For a correction: the (+) or (−) choice. */
    fun onCorrectionSign(plus: Boolean) = edit(emptySet()) { it.copy(action = withCorrectionSign(it.action, plus)) }

    fun onSave() {
        val current = _ui.value
        if (current.saving || current.saved) return
        _ui.update { it.copy(saving = true, errors = emptySet(), problem = null) }

        viewModelScope.launch {
            try {
                when (val result = ledger.save(formId, customerId, inputOf(current.form))) {
                    is LedgerSaveResult.Saved -> _ui.update { it.copy(saving = false, saved = true) }
                    is LedgerSaveResult.Invalid -> _ui.update { it.copy(saving = false, errors = result.errors) }
                    LedgerSaveResult.CustomerNotFound ->
                        _ui.update { it.copy(saving = false, problem = SaveProblem.CUSTOMER_NOT_FOUND) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The repository saves in one transaction, so nothing was saved.
                _ui.update { it.copy(saving = false, problem = SaveProblem.UNEXPECTED) }
            }
        }
    }

    private companion object {
        val AMOUNT_ERRORS = setOf(
            LedgerError.AMOUNT_MISSING, LedgerError.AMOUNT_TOO_LARGE, LedgerError.NO_ADVANCE, LedgerError.MORE_THAN_ADVANCE
        )
        val DATE_ERRORS = setOf(LedgerError.DATE_IN_FUTURE)
        val METHOD_ERRORS = setOf(LedgerError.METHOD_REQUIRED)
        val NOTE_ERRORS = setOf(LedgerError.NOTE_REQUIRED, LedgerError.NOTE_TOO_LONG)
    }
}

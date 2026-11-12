package com.example.dukanhisabnext.ui.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.ExpenseDeleteResult
import com.example.dukanhisabnext.data.ExpenseRepository
import com.example.dukanhisabnext.data.ExpenseSaveResult
import com.example.dukanhisabnext.data.entity.ExpenseCategory
import com.example.dukanhisabnext.expense.ExpenseError
import com.example.dukanhisabnext.expense.pickableCategories
import com.example.dukanhisabnext.util.DhakaTime
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the expense form shows. It lives in the ViewModel, so rotation keeps the typed values. */
data class ExpenseFormUi(
    val form: ExpenseFormState,
    val isEdit: Boolean,
    /** In edit mode: false until the saved expense is loaded into the form. */
    val loaded: Boolean,
    /** Problems from the last save attempt. A box's problem goes away when that box is edited. */
    val errors: Set<ExpenseError> = emptySet(),
    val problem: ExpenseProblem? = null,
    /** True while saving or deleting: the buttons are disabled. */
    val busy: Boolean = false,
    /** True after a successful save: the screen goes back to the list. */
    val saved: Boolean = false,
    /** The id of the expense that was just deleted: the screen goes back and offers the undo. */
    val deletedId: Long? = null,
    val confirmDeleteOpen: Boolean = false
)

/**
 * Logic of the ONE expense form (add and edit). A form id is made when the form opens, so a double
 * tap on the save button (or a retry) saves only once (ExpenseRepository remembers the id).
 * The route may carry an "expenseId": then the form edits that expense.
 */
class ExpenseFormViewModel(
    private val repo: ExpenseRepository,
    savedStateHandle: SavedStateHandle,
    clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    /** null = a new expense; a value = edit that expense (the route uses -1 for "none"). */
    private val expenseId: Long? = savedStateHandle.get<Long>("expenseId")?.takeIf { it > 0 }

    /** Made once when the form opens. The same id is sent on every tap of the save button. */
    private val formId: String = UUID.randomUUID().toString()

    val today: LocalDate = DhakaTime.localDate(clock())

    private val _ui = MutableStateFlow(
        ExpenseFormUi(form = newExpenseForm(today), isEdit = expenseId != null, loaded = expenseId == null)
    )
    val ui: StateFlow<ExpenseFormUi> = _ui.asStateFlow()

    /** The category the edited expense already has (it stays pickable even if hidden). */
    private val _currentCategoryId = MutableStateFlow<Long?>(null)

    /** The categories to pick from: shown ones, plus the edited expense's own. */
    val categories: StateFlow<List<ExpenseCategory>> = combine(repo.observeCategories(), _currentCategoryId) { all, current ->
        pickableCategories(all, current)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (expenseId != null) {
            viewModelScope.launch {
                val expense = repo.getExpense(expenseId)
                if (expense == null || expense.isDeleted) {
                    _ui.update { it.copy(loaded = true, problem = ExpenseProblem.NOT_FOUND) }
                } else {
                    _currentCategoryId.value = expense.expenseCategoryId
                    _ui.update { it.copy(form = formFromExpense(expense), loaded = true) }
                }
            }
        } else {
            // A new form preselects the first shown category as soon as the categories exist.
            viewModelScope.launch {
                repo.observeCategories().collect { all ->
                    val first = defaultCategoryId(all)
                    _ui.update { if (it.form.categoryId == null && first != null) it.copy(form = it.form.copy(categoryId = first)) else it }
                }
            }
        }
    }

    private fun edit(clearing: Set<ExpenseError>, change: (ExpenseFormState) -> ExpenseFormState) {
        _ui.update { it.copy(form = change(it.form), errors = it.errors - clearing, problem = null) }
    }

    fun onCategoryChange(id: Long) = edit(setOf(ExpenseError.CATEGORY_MISSING, ExpenseError.CATEGORY_HIDDEN)) { it.copy(categoryId = id) }
    fun onAmountChange(text: String) = edit(setOf(ExpenseError.AMOUNT_MISSING, ExpenseError.AMOUNT_TOO_LARGE)) { it.copy(amountText = text) }
    fun onDateChange(date: LocalDate) = edit(setOf(ExpenseError.DATE_IN_FUTURE)) { it.copy(date = date) }
    fun onReasonChange(text: String) = edit(setOf(ExpenseError.REASON_TOO_LONG)) { it.copy(reason = limitReasonText(text)) }

    fun onSave() {
        val current = _ui.value
        if (current.busy || current.saved || current.deletedId != null || !current.loaded) return
        _ui.update { it.copy(busy = true, errors = emptySet(), problem = null) }

        viewModelScope.launch {
            try {
                val input = inputOf(current.form)
                val result = if (expenseId == null) repo.addExpense(formId, input) else repo.editExpense(formId, expenseId, input)
                when (result) {
                    is ExpenseSaveResult.Saved -> _ui.update { it.copy(busy = false, saved = true) }
                    is ExpenseSaveResult.Invalid -> _ui.update { it.copy(busy = false, errors = result.errors) }
                    ExpenseSaveResult.NotFound -> _ui.update { it.copy(busy = false, problem = ExpenseProblem.NOT_FOUND) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The repository saves in one transaction, so nothing was saved.
                _ui.update { it.copy(busy = false, problem = ExpenseProblem.UNEXPECTED) }
            }
        }
    }

    fun onDeleteClick() {
        _ui.update { if (it.busy || !it.isEdit) it else it.copy(confirmDeleteOpen = true) }
    }

    fun onDeleteDismiss() {
        _ui.update { it.copy(confirmDeleteOpen = false) }
    }

    /** "হ্যাঁ, মুছুন": marks the expense as deleted, then the screen goes back and offers the undo. */
    fun onDeleteConfirm() {
        val id = expenseId ?: return
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, confirmDeleteOpen = false, problem = null) }
        viewModelScope.launch {
            try {
                when (repo.deleteExpense(id)) {
                    ExpenseDeleteResult.DELETED -> _ui.update { it.copy(busy = false, deletedId = id) }
                    // Already gone: nothing to undo, just say so.
                    ExpenseDeleteResult.ALREADY_DELETED, ExpenseDeleteResult.NOT_FOUND ->
                        _ui.update { it.copy(busy = false, problem = ExpenseProblem.NOT_FOUND) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, problem = ExpenseProblem.UNEXPECTED) }
            }
        }
    }
}

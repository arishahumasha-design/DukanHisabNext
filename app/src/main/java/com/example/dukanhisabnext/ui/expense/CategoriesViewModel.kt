package com.example.dukanhisabnext.ui.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.data.CategorySaveResult
import com.example.dukanhisabnext.data.ExpenseRepository
import com.example.dukanhisabnext.data.entity.ExpenseCategory
import com.example.dukanhisabnext.expense.CategoryError
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The add / rename dialog and the last message of the categories screen. */
data class CategoriesUi(
    /** null = the dialog is closed. */
    val dialog: CategoryForm? = null,
    /** Problems from the last save of the dialog. */
    val errors: Set<CategoryError> = emptySet(),
    /** A message for a hide that was refused (LAST_VISIBLE) or an unexpected error. */
    val screenError: CategoryError? = null,
    val unexpected: Boolean = false,
    val busy: Boolean = false
)

/** Logic of the categories screen: add, rename, the personal flag, hide and show. No delete. */
class CategoriesViewModel(private val repo: ExpenseRepository) : ViewModel() {

    /** Shown categories first, then the hidden ones. null while loading. */
    val categories: StateFlow<List<ExpenseCategory>?> = repo.observeCategories()
        .map { categoriesForScreen(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _ui = MutableStateFlow(CategoriesUi())
    val ui: StateFlow<CategoriesUi> = _ui.asStateFlow()

    /** Made when the "new category" dialog opens, so a double tap on its save button adds one. */
    private var formId: String = UUID.randomUUID().toString()

    fun onAddClick() {
        formId = UUID.randomUUID().toString()
        _ui.value = CategoriesUi(dialog = CategoryForm())
    }

    fun onEditClick(category: ExpenseCategory) {
        _ui.value = CategoriesUi(dialog = categoryFormFor(category))
    }

    fun onDismiss() {
        _ui.update { if (it.busy) it else it.copy(dialog = null, errors = emptySet()) }
    }

    fun onNameChange(text: String) {
        _ui.update { s -> s.dialog?.let { s.copy(dialog = it.copy(name = limitCategoryName(text)), errors = emptySet()) } ?: s }
    }

    fun onPersonalChange(value: Boolean) {
        _ui.update { s -> s.dialog?.let { s.copy(dialog = it.copy(isPersonal = value)) } ?: s }
    }

    fun onSave() {
        val current = _ui.value
        val form = current.dialog ?: return
        if (current.busy) return
        _ui.update { it.copy(busy = true, errors = emptySet(), unexpected = false) }

        viewModelScope.launch {
            try {
                val result = if (form.isNew) {
                    repo.addCategory(formId, form.name, form.isPersonal)
                } else {
                    repo.updateCategory(requireNotNull(form.editingId), form.name, form.isPersonal)
                }
                when (result) {
                    is CategorySaveResult.Saved -> _ui.value = CategoriesUi() // closes the dialog
                    is CategorySaveResult.Invalid -> _ui.update { it.copy(busy = false, errors = result.errors) }
                    CategorySaveResult.NotFound -> _ui.update { it.copy(busy = false, dialog = null, unexpected = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, unexpected = true) }
            }
        }
    }

    /** "লুকান" / "দেখান". The last shown category cannot be hidden: a message says so. */
    fun onToggleVisible(category: ExpenseCategory) {
        if (_ui.value.busy) return
        _ui.update { it.copy(screenError = null, unexpected = false) }
        viewModelScope.launch {
            try {
                when (val result = repo.setCategoryVisible(category.id, visible = !category.isActive)) {
                    is CategorySaveResult.Invalid -> _ui.update { it.copy(screenError = result.errors.firstOrNull()) }
                    CategorySaveResult.NotFound -> _ui.update { it.copy(unexpected = true) }
                    is CategorySaveResult.Saved -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(unexpected = true) }
            }
        }
    }

    fun onMessageShown() {
        _ui.update { it.copy(screenError = null, unexpected = false) }
    }
}

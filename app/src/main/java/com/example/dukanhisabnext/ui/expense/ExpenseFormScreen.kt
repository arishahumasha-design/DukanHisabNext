package com.example.dukanhisabnext.ui.expense

import com.example.dukanhisabnext.ui.components.LoadingIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.example.dukanhisabnext.ui.components.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.example.dukanhisabnext.ui.components.AppTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.expense.MAX_EXPENSE_REASON_LENGTH
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.BengaliDigitsTransformation
import com.example.dukanhisabnext.ui.components.DayField
import com.example.dukanhisabnext.ui.components.DropdownField
import com.example.dukanhisabnext.ui.components.ConfirmAmountDialog
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.components.FormCard
import com.example.dukanhisabnext.ui.components.SaveBar
import com.example.dukanhisabnext.ui.components.DialogTextButton
import com.example.dukanhisabnext.ui.components.DestructiveTextButton
import com.example.dukanhisabnext.ui.components.needsMoneyConfirm
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.parseTakaToPaisa
import com.example.dukanhisabnext.ui.components.EXPENSE_CONFIRM_PAISA
import com.example.dukanhisabnext.ui.components.sanitizeNumberInput
import com.example.dukanhisabnext.util.toBengaliDigits

/**
 * The ONE expense form (add and edit): category, amount, date (today by default, never a future
 * day) and an optional reason (200 characters, with a counter). In edit mode there is also a "মুছুন"
 * button with a confirm dialog. [onSaved] goes back to the list. [onDeleted] gets the deleted id: the
 * list then shows the "ফিরিয়ে আনুন" undo bar.
 */
@Composable
fun ExpenseFormScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: (Long) -> Unit,
    viewModel: ExpenseFormViewModel = appViewModel { app, args -> ExpenseFormViewModel(app.expenseRepository, args) }
) {
    EnsureDefaultCategories()

    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val form = ui.form

    LaunchedEffect(ui.saved) {
        if (ui.saved) onSaved()
    }
    LaunchedEffect(ui.deletedId) {
        ui.deletedId?.let(onDeleted)
    }

    // A big amount (৳5,000 or more) asks "ঠিক আছে?" before saving (ConfirmRules.kt).
    var confirmOpen by rememberSaveable { mutableStateOf(false) }
    val amountPaisa = parseTakaToPaisa(form.amountText)

    SubScreenScaffold(
        title = stringResource(if (ui.isEdit) R.string.title_expense_edit else R.string.title_expense_new),
        onBack = onBack
    ) { padding ->
        if (!ui.loaded) {
            LoadingIndicator(Modifier.padding(padding).fillMaxSize()) // loading the expense to edit
            return@SubScreenScaffold
        }

        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FormCard {
                // ---- Category (shown ones, plus the edited expense's own) ----
                val selected = categories.firstOrNull { it.id == form.categoryId }
                val hiddenWord = stringResource(R.string.cat_tag_hidden)
                val personalWord = stringResource(R.string.cat_tag_personal)
                DropdownField(
                    label = stringResource(R.string.expense_category_label),
                    selectedText = selected?.name.orEmpty(),
                    enabled = !ui.busy
                ) { close ->
                    categories.forEach { category ->
                        val marks = buildList {
                            if (category.isPersonal) add(personalWord)
                            if (!category.isActive) add(hiddenWord)
                        }
                        DropdownMenuItem(
                            text = { Text(category.name + if (marks.isEmpty()) "" else marks.joinToString(prefix = " (", postfix = ")")) },
                            onClick = {
                                viewModel.onCategoryChange(category.id)
                                close()
                            }
                        )
                    }
                }
                errorsFor(ExpenseField.CATEGORY, ui.errors).firstOrNull()?.let {
                    Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error)
                }

                // ---- Amount ----
                val amountError = errorsFor(ExpenseField.AMOUNT, ui.errors).firstOrNull()
                AppTextField(
                    value = form.amountText,
                    onValueChange = { viewModel.onAmountChange(sanitizeNumberInput(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.expense_amount_label)) },
                    prefix = { Text(stringResource(R.string.unit_taka)) },
                    enabled = !ui.busy,
                    singleLine = true,
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(stringResource(it.messageRes())) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    visualTransformation = BengaliDigitsTransformation
                )

                // ---- Date (today by default; no future day) ----
                DayField(
                    date = form.date,
                    today = viewModel.today,
                    enabled = !ui.busy,
                    errorText = errorsFor(ExpenseField.DATE, ui.errors).firstOrNull()?.let { stringResource(it.messageRes()) },
                    onDateChange = viewModel::onDateChange
                )

                // ---- Reason (optional) with a counter ----
                val reasonError = errorsFor(ExpenseField.REASON, ui.errors).firstOrNull()
                AppTextField(
                    value = form.reason,
                    onValueChange = viewModel::onReasonChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.expense_reason_label)) },
                    enabled = !ui.busy,
                    minLines = 2,
                    maxLines = 4,
                    isError = reasonError != null,
                    supportingText = {
                        Column {
                            reasonError?.let { Text(stringResource(it.messageRes())) }
                            Text(
                                stringResource(
                                    R.string.ledger_note_counter,
                                    toBengaliDigits(reasonLength(form.reason).toString()),
                                    toBengaliDigits(MAX_EXPENSE_REASON_LENGTH.toString())
                                )
                            )
                        }
                    }
                )

                }

                // ---- Problems that are not about one box ----
                ui.problem?.let {
                    Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                }

                // ---- Delete (edit mode only) ----
                if (showsDeleteButton(ui.isEdit)) {
                    DestructiveTextButton(
                        text = stringResource(R.string.expense_btn_delete),
                        onClick = viewModel::onDeleteClick,
                        enabled = !ui.busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ---- The save button: disabled while saving, so a double tap cannot save twice ----
            SaveBar(
                label = stringResource(if (ui.busy) R.string.ledger_saving else R.string.btn_save),
                enabled = !ui.busy && !ui.saved && ui.deletedId == null && ui.problem != ExpenseProblem.NOT_FOUND,
                onClick = { if (needsMoneyConfirm(amountPaisa, EXPENSE_CONFIRM_PAISA)) confirmOpen = true else viewModel.onSave() }
            )
        }
    }

    if (confirmOpen) {
        ConfirmAmountDialog(
            amountText = stringResource(R.string.price_format, formatTaka(amountPaisa ?: 0L)),
            onFix = { confirmOpen = false },
            onConfirm = {
                confirmOpen = false
                viewModel.onSave()
            }
        )
    }

    // ---- "খরচটি মুছবেন?" ----
    if (ui.confirmDeleteOpen) {
        AppAlertDialog(
            onDismissRequest = viewModel::onDeleteDismiss,
            title = { Text(stringResource(R.string.expense_delete_title)) },
            text = { Text(stringResource(R.string.expense_delete_text)) },
            confirmButton = {
                DestructiveTextButton(stringResource(R.string.expense_delete_yes), viewModel::onDeleteConfirm)
            },
            dismissButton = {
                DialogTextButton(stringResource(R.string.cancel_no), viewModel::onDeleteDismiss)
            }
        )
    }
}

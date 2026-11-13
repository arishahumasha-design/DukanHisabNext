package com.example.dukanhisabnext.ui.expense

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.dukanhisabnext.ui.components.LoadingIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.dukanhisabnext.ui.components.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import com.example.dukanhisabnext.ui.components.AppTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.entity.ExpenseCategory
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.components.EmptyState
import com.example.dukanhisabnext.ui.components.SaveBar
import com.example.dukanhisabnext.ui.components.DialogTextButton
import com.example.dukanhisabnext.ui.components.PillVariant
import com.example.dukanhisabnext.ui.components.StatusPill
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

/**
 * "খরচের ক্যাটাগরি": every category (the hidden ones are marked), with add, rename, the "দোকানের খরচ
 * নয় (ব্যক্তিগত)" checkbox, and "লুকান" / "দেখান". There is no delete (old expenses must never lose
 * their category). The last shown category cannot be hidden: a message says so.
 */
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = appViewModel { app, _ -> CategoriesViewModel(app.expenseRepository) }
) {
    EnsureDefaultCategories()

    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    // A refused hide (or an error) message goes away by itself.
    LaunchedEffect(ui.screenError, ui.unexpected) {
        if (ui.screenError != null || ui.unexpected) {
            delay(4_000)
            viewModel.onMessageShown()
        }
    }

    SubScreenScaffold(title = stringResource(R.string.title_expense_categories), onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // ---- The message of a refused hide ----
            ui.screenError?.let {
                Text(
                    text = stringResource(it.messageRes()),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (ui.unexpected) {
                Text(
                    text = stringResource(R.string.err_ledger_unexpected),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // ---- The list ----
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val list = categories
                when {
                    list == null -> LoadingIndicator(Modifier.fillMaxSize())
                    list.isEmpty() -> EmptyState(icon = R.drawable.ic_expense, text = stringResource(R.string.cat_empty))
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(list, key = { it.id }) { category ->
                            CategoryRow(
                                category = category,
                                onEdit = { viewModel.onEditClick(category) },
                                onToggle = { viewModel.onToggleVisible(category) }
                            )
                        }
                    }
                }
            }

            // ---- Add ----
            SaveBar(label = stringResource(R.string.cat_btn_add), enabled = true, onClick = viewModel::onAddClick)
        }
    }

    ui.dialog?.let { form ->
        CategoryDialog(form = form, ui = ui, viewModel = viewModel)
    }
}

/** One category: its name, the marks (ব্যক্তিগত, লুকানো) and the "লুকান" / "দেখান" button. Tap = rename. */
@Composable
private fun CategoryRow(category: ExpenseCategory, onEdit: () -> Unit, onToggle: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onEdit),
        headlineContent = {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (category.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        supportingContent = {
            val marks = categoryMarks(category)
            if (marks.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    marks.forEach { mark ->
                        StatusPill(stringResource(mark.labelRes()), PillVariant.Neutral)
                    }
                }
            }
        },
        trailingContent = {
            TextButton(onClick = onToggle) {
                Text(stringResource(if (showsHideButton(category)) R.string.cat_hide else R.string.cat_show))
            }
        }
    )
    HorizontalDivider()
}

/** The add / rename dialog: the name, the "দোকানের খরচ নয় (ব্যক্তিগত)" checkbox, and the problems. */
@Composable
private fun CategoryDialog(form: CategoryForm, ui: CategoriesUi, viewModel: CategoriesViewModel) {
    val nameError = errorsFor(CategoryField.NAME, ui.errors).firstOrNull()
    AppAlertDialog(
        onDismissRequest = viewModel::onDismiss,
        title = { Text(stringResource(if (form.isNew) R.string.cat_title_add else R.string.cat_title_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = form.name,
                    onValueChange = viewModel::onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.cat_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    enabled = !ui.busy,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(stringResource(it.messageRes())) } }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = form.isPersonal,
                            enabled = !ui.busy,
                            role = Role.Checkbox,
                            onValueChange = viewModel::onPersonalChange
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = form.isPersonal, onCheckedChange = null)
                    Text(
                        text = stringResource(R.string.cat_personal_checkbox),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (ui.unexpected) {
                    Text(stringResource(R.string.err_ledger_unexpected), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            DialogTextButton(stringResource(if (ui.busy) R.string.ledger_saving else R.string.btn_save), viewModel::onSave, enabled = !ui.busy)
        },
        dismissButton = {
            DialogTextButton(stringResource(R.string.btn_cancel), viewModel::onDismiss, enabled = !ui.busy)
        }
    )
}

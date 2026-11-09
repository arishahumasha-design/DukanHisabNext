package com.example.dukanhisabnext.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.DropdownField
import com.example.dukanhisabnext.ui.components.NumberField
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.components.messageRes

/** Add a new product, or edit an existing one (when the route carries a product id). */
@Composable
fun ProductEditScreen(
    onBack: () -> Unit,
    viewModel: ProductEditViewModel = appViewModel { app, args -> ProductEditViewModel(app.productRepository, args) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    // rememberSaveable keeps this answer when the phone is rotated.
    var showArchiveConfirm by rememberSaveable { mutableStateOf(false) }

    // After a successful save (or archive) the ViewModel sets finished = true: close the screen.
    LaunchedEffect(state.finished) {
        if (state.finished) onBack()
    }

    SubScreenScaffold(
        title = stringResource(if (state.isNew) R.string.title_product_new else R.string.title_product_edit),
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding() // lift the form above the keyboard
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Name
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                isError = state.errors.name != null,
                supportingText = {
                    val error = state.errors.name
                    when {
                        error != null -> Text(stringResource(error.messageRes()))
                        state.duplicateNameWarning -> Text(stringResource(R.string.name_duplicate_warning))
                    }
                }
            )

            // Category (can be created right here)
            DropdownField(
                label = stringResource(R.string.field_category),
                selectedText = categories.firstOrNull { it.id == state.categoryId }?.name
                    ?: stringResource(R.string.category_none)
            ) { close ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.category_none)) },
                    onClick = { viewModel.onCategorySelected(null); close() }
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = { viewModel.onCategorySelected(category.id); close() }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.category_new)) },
                    onClick = { close(); viewModel.openCategoryDialog() }
                )
            }

            // Unit (locked after the product is created)
            DropdownField(
                label = stringResource(R.string.field_unit),
                selectedText = stringResource(state.unit.labelRes()),
                enabled = state.isNew
            ) { close ->
                ProductUnit.entries.forEach { unit ->
                    DropdownMenuItem(
                        text = { Text(stringResource(unit.labelRes())) },
                        onClick = { viewModel.onUnitSelected(unit); close() }
                    )
                }
            }
            if (!state.isNew) {
                Text(
                    text = stringResource(R.string.unit_locked_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            NumberField(
                label = stringResource(R.string.field_sell_price),
                value = state.sellPrice,
                onValueChange = viewModel::onSellPriceChange,
                error = state.errors.sellPrice
            )
            NumberField(
                label = stringResource(R.string.field_cost_price),
                value = state.costPrice,
                onValueChange = viewModel::onCostPriceChange,
                error = state.errors.costPrice
            )
            // Opening stock only exists when adding. Later changes go through the stock screen.
            if (state.isNew) {
                NumberField(
                    label = stringResource(R.string.field_opening_stock),
                    value = state.openingStock,
                    onValueChange = viewModel::onOpeningStockChange,
                    error = state.errors.openingStock
                )
            }
            NumberField(
                label = stringResource(R.string.field_low_stock),
                value = state.lowStockLevel,
                onValueChange = viewModel::onLowStockLevelChange,
                error = state.errors.lowStockLevel
            )

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving && !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_save))
            }

            if (!state.isNew) {
                OutlinedButton(
                    onClick = { showArchiveConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_archive))
                }
            }
        }
    }

    // ---- Pop-up: new category ----
    if (state.categoryDialogOpen) {
        AlertDialog(
            onDismissRequest = viewModel::closeCategoryDialog,
            title = { Text(stringResource(R.string.dialog_category_title)) },
            text = {
                OutlinedTextField(
                    value = state.categoryName,
                    onValueChange = viewModel::onCategoryNameChange,
                    label = { Text(stringResource(R.string.field_category_name)) },
                    singleLine = true,
                    isError = state.categoryError != null,
                    supportingText = state.categoryError?.let { { Text(stringResource(it.messageRes())) } }
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmNewCategory) { Text(stringResource(R.string.btn_add)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeCategoryDialog) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    // ---- Pop-up: confirm archive ----
    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text(stringResource(R.string.archive_confirm_title)) },
            text = { Text(stringResource(R.string.archive_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { showArchiveConfirm = false; viewModel.archive() }) {
                    Text(stringResource(R.string.btn_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

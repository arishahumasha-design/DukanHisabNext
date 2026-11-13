package com.example.dukanhisabnext.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.dukanhisabnext.ui.components.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.example.dukanhisabnext.ui.components.AppTextField
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.dukanhisabnext.ui.components.ConfirmAmountDialog
import com.example.dukanhisabnext.ui.components.FormWithSaveBar
import com.example.dukanhisabnext.ui.components.SaveBar
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.components.FormCard
import com.example.dukanhisabnext.ui.components.DialogTextButton
import com.example.dukanhisabnext.ui.components.DestructiveTextButton
import com.example.dukanhisabnext.ui.components.needsStockConfirm
import com.example.dukanhisabnext.util.formatQuantity
import com.example.dukanhisabnext.util.parseQuantity
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
    var confirmStockOpen by rememberSaveable { mutableStateOf(false) }

    // After a successful save (or archive) the ViewModel sets finished = true: close the screen.
    LaunchedEffect(state.finished) {
        if (state.finished) onBack()
    }

    SubScreenScaffold(
        title = stringResource(if (state.isNew) R.string.title_product_new else R.string.title_product_edit),
        onBack = onBack
    ) { padding ->
        // The Save button is pinned above the keyboard (FormWithSaveBar); the fields scroll.
        // A big opening stock (1,000 units or more) asks "ঠিক আছে?" first.
        FormWithSaveBar(
            padding = padding,
            saveBar = {
                SaveBar(stringResource(R.string.btn_save), !state.isSaving && !state.isLoading) {
                    if (state.isNew && needsStockConfirm(parseQuantity(state.openingStock), null)) confirmStockOpen = true
                    else viewModel.save()
                }
            }
        ) {
            FormCard {
            // Name
            AppTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
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

            }

            FormCard {
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
            }

            FormCard {
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
            }

            if (!state.isNew) {
                DestructiveTextButton(
                    text = stringResource(R.string.btn_archive),
                    onClick = { showArchiveConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // ---- Pop-up: "ঠিক আছে?" for a big opening stock ----
    if (confirmStockOpen) {
        val quantity = parseQuantity(state.openingStock) ?: 0L
        ConfirmAmountDialog(
            amountText = formatQuantity(quantity) + " " + stringResource(state.unit.labelRes()),
            onFix = { confirmStockOpen = false },
            onConfirm = {
                confirmStockOpen = false
                viewModel.save()
            }
        )
    }

    // ---- Pop-up: new category ----
    if (state.categoryDialogOpen) {
        AppAlertDialog(
            onDismissRequest = viewModel::closeCategoryDialog,
            title = { Text(stringResource(R.string.dialog_category_title)) },
            text = {
                AppTextField(
                    value = state.categoryName,
                    onValueChange = viewModel::onCategoryNameChange,
                    label = { Text(stringResource(R.string.field_category_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    isError = state.categoryError != null,
                    supportingText = state.categoryError?.let { { Text(stringResource(it.messageRes())) } }
                )
            },
            confirmButton = {
                DialogTextButton(stringResource(R.string.btn_add), viewModel::confirmNewCategory)
            },
            dismissButton = {
                DialogTextButton(stringResource(R.string.btn_cancel), viewModel::closeCategoryDialog)
            }
        )
    }

    // ---- Pop-up: confirm archive ----
    if (showArchiveConfirm) {
        AppAlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text(stringResource(R.string.archive_confirm_title)) },
            text = { Text(stringResource(R.string.archive_confirm_text)) },
            confirmButton = {
                DestructiveTextButton(stringResource(R.string.btn_archive), { showArchiveConfirm = false; viewModel.archive() })
            },
            dismissButton = {
                DialogTextButton(stringResource(R.string.btn_cancel), { showArchiveConfirm = false })
            }
        )
    }
}

package com.example.dukanhisabnext.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.NumberField
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.components.messageRes
import com.example.dukanhisabnext.util.StockMath
import com.example.dukanhisabnext.util.formatDate
import com.example.dukanhisabnext.util.formatQuantity
import com.example.dukanhisabnext.util.formatSignedQuantity
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.parseQuantity

/** Add stock (when you buy) or correct stock (after counting). Shows the movement history. */
@Composable
fun StockAdjustScreen(
    onBack: () -> Unit,
    viewModel: StockAdjustViewModel = appViewModel { app, args -> StockAdjustViewModel(app.productRepository, args) }
) {
    val product by viewModel.product.collectAsStateWithLifecycle()
    val stock by viewModel.stock.collectAsStateWithLifecycle()
    val movements by viewModel.movements.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()

    val unitText = product?.let { stringResource(it.unit.labelRes()) }.orEmpty()

    SubScreenScaffold(title = stringResource(R.string.title_stock), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Product name and current stock (the SUM of the movements below)
            item {
                Column {
                    Text(product?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
                    val current = stock
                    if (current != null) {
                        val status = stockStatus(current, product?.lowStockLevel)
                        Text(
                            text = stringResource(R.string.stock_current, formatQuantity(current), unitText),
                            style = MaterialTheme.typography.titleMedium,
                            color = status.color()
                        )
                    }
                }
            }

            // Which action: buying stock, or fixing the count
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = form.mode == StockMode.PURCHASE,
                        onClick = { viewModel.onModeChange(StockMode.PURCHASE) },
                        label = { Text(stringResource(R.string.mode_purchase)) }
                    )
                    FilterChip(
                        selected = form.mode == StockMode.CORRECTION,
                        onClick = { viewModel.onModeChange(StockMode.CORRECTION) },
                        label = { Text(stringResource(R.string.mode_correction)) }
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (form.mode == StockMode.PURCHASE) {
                        NumberField(
                            label = stringResource(R.string.field_quantity),
                            value = form.quantity,
                            onValueChange = viewModel::onQuantityChange,
                            error = form.errors.quantity
                        )
                        NumberField(
                            label = stringResource(R.string.field_unit_cost),
                            value = form.unitCost,
                            onValueChange = viewModel::onUnitCostChange,
                            error = form.errors.unitCost
                        )
                        // The checkbox appears only when a buying price has been typed.
                        if (form.unitCost.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = form.updateCostPrice,
                                    onCheckedChange = viewModel::onUpdateCostPriceChange
                                )
                                Text(stringResource(R.string.checkbox_update_cost))
                            }
                        }
                    } else {
                        NumberField(
                            label = stringResource(R.string.field_counted_stock),
                            value = form.counted,
                            onValueChange = viewModel::onCountedChange,
                            error = form.errors.counted
                        )
                        // Preview: "now 12 -> new 10 (difference -2)". Nothing is saved yet.
                        val counted = parseQuantity(form.counted)
                        val current = stock
                        if (counted != null && current != null) {
                            val difference = StockMath.correctionDifference(counted, current)
                            if (difference != 0L) {
                                Text(
                                    text = stringResource(
                                        R.string.correction_preview,
                                        formatQuantity(current),
                                        formatQuantity(counted),
                                        formatSignedQuantity(difference)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = form.note,
                        onValueChange = viewModel::onNoteChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_note)) }
                    )
                    Button(
                        onClick = viewModel::save,
                        enabled = !form.isSaving && stock != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.btn_save))
                    }
                }
            }

            // History: every stock change, newest first. Rows are never edited or deleted.
            item {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            if (movements.isEmpty()) {
                item { Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(movements, key = { it.id }) { movement ->
                    MovementRow(movement, unitText)
                }
            }
        }
    }
}

@Composable
private fun MovementRow(movement: StockMovement, unitText: String) {
    ListItem(
        headlineContent = {
            Text(
                text = "${formatSignedQuantity(movement.quantity)} $unitText",
                color = if (movement.quantity < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Column {
                Text(
                    stringResource(
                        R.string.movement_line,
                        stringResource(movement.type.labelRes()),
                        formatDate(movement.createdAt)
                    )
                )
                movement.unitCost?.let {
                    Text(stringResource(R.string.price_format, formatTaka(it)))
                }
                movement.note?.let { Text(it) }
            }
        }
    )
}

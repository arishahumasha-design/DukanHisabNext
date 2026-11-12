package com.example.dukanhisabnext.ui.sell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.NumberField
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.products.StockStatus
import com.example.dukanhisabnext.ui.products.color
import com.example.dukanhisabnext.ui.products.labelRes
import com.example.dukanhisabnext.util.formatQuantity
import com.example.dukanhisabnext.util.formatTaka

/**
 * Screen B of the বিক্রি tab: the cart. Lines as cards, then the subtotal (মোট), the discount and
 * delivery switches, and a fixed bottom bar with the grand total (সর্বমোট) and "এগিয়ে যান".
 * Back returns to the picker; the cart lives in the shared CartStore, so nothing is lost.
 */
@Composable
fun CartScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit = {},
    viewModel: CartViewModel = appViewModel { app, _ -> CartViewModel(app.productRepository, app.cartStore) }
) {
    val cart by viewModel.cartState.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val stocks by viewModel.stocks.collectAsStateWithLifecycle()
    val unavailable by viewModel.unavailable.collectAsStateWithLifecycle()
    val costPrices by viewModel.costPrices.collectAsStateWithLifecycle()
    val canContinue by viewModel.canContinue.collectAsStateWithLifecycle()

    SubScreenScaffold(title = stringResource(R.string.cart_title), onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
            CartRestoreNote()
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ---- Lines ----
                if (summary.lines.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.cart_empty_screen),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(summary.lines, key = { it.input.productId }) { line ->
                        CartLineCard(
                            line = line,
                            stock = stocks[line.input.productId] ?: 0L, // no movements yet = stock 0
                            unavailable = line.input.productId in unavailable,
                            costPrice = costPrices[line.input.productId],
                            viewModel = viewModel
                        )
                    }
                }

                // ---- মোট (subtotal) ----
                item(key = "subtotal") {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                        AmountRow(
                            label = stringResource(R.string.label_cart_subtotal),
                            amount = summary.subtotal,
                            bold = true
                        )
                    }
                }

                // ---- Discount (switch, amount, ৳ or %) ----
                item(key = "discount") {
                    Column {
                        SwitchRow(
                            label = stringResource(R.string.field_discount),
                            checked = cart.discountOn,
                            onCheckedChange = viewModel::onDiscountOnChange
                        )
                        if (cart.discountOn) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                val percent = cart.discountUnit == DiscountUnit.PERCENT
                                NumberField(
                                    label = stringResource(R.string.field_discount_amount),
                                    value = cart.discountText,
                                    onValueChange = viewModel::onDiscountChange,
                                    modifier = Modifier.weight(1f),
                                    error = summary.discountError,
                                    prefix = if (percent) null else stringResource(R.string.unit_taka),
                                    suffix = if (percent) stringResource(R.string.unit_percent) else null
                                )
                                DiscountUnitToggle(
                                    selected = cart.discountUnit,
                                    onSelect = viewModel::onDiscountUnitChange
                                )
                            }
                            // For a percentage, show the taka amount it comes to (rounded to the paisa).
                            if (cart.discountUnit == DiscountUnit.PERCENT &&
                                cart.discountText.isNotBlank() && summary.discountError == null
                            ) {
                                Text(
                                    text = stringResource(R.string.discount_amount_line, formatTaka(summary.discount)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ---- Delivery charge (switch, amount) ----
                item(key = "delivery") {
                    Column {
                        SwitchRow(
                            label = stringResource(R.string.field_delivery),
                            checked = cart.deliveryOn,
                            onCheckedChange = viewModel::onDeliveryOnChange
                        )
                        if (cart.deliveryOn) {
                            NumberField(
                                label = stringResource(R.string.field_delivery_amount),
                                value = cart.deliveryText,
                                onValueChange = viewModel::onDeliveryChange,
                                error = summary.deliveryError,
                                prefix = stringResource(R.string.unit_taka)
                            )
                        }
                    }
                }
            }

            // ---- Fixed bottom bar: সর্বমোট and the big continue button ----
            GrandTotalBar(total = summary.total, canContinue = canContinue, onContinue = onContinue)
        }
    }
}

// =========================================================================================

/** A label on the left and an amount on the right. */
@Composable
private fun AmountRow(label: String, amount: Long, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.price_format, formatTaka(amount)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** A label and an on/off switch. At least 56dp high, so it is easy to hit. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Two big buttons: ৳ (a fixed amount) or % (a percentage). The chosen one is filled. */
@Composable
private fun DiscountUnitToggle(selected: DiscountUnit, onSelect: (DiscountUnit) -> Unit) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DiscountUnit.entries.forEach { unit ->
            val label = stringResource(if (unit == DiscountUnit.TAKA) R.string.unit_taka else R.string.unit_percent)
            val modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
            val padding = PaddingValues(horizontal = 12.dp)
            if (unit == selected) {
                Button(onClick = { onSelect(unit) }, modifier = modifier, contentPadding = padding) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                OutlinedButton(onClick = { onSelect(unit) }, modifier = modifier, contentPadding = padding) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** One cart line: name, remove, quantity, price, line total (computed, read-only), stock warning. */
@Composable
private fun CartLineCard(line: CartLineView, stock: Long, unavailable: Boolean, costPrice: Long?, viewModel: CartViewModel) {
    val input = line.input
    val unitText = stringResource(input.unit.labelRes())

    Card(
        modifier = Modifier.fillMaxWidth(),
        // A line the sale could not accept (archived product) is drawn in red until it is removed.
        colors = if (unavailable) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            if (unavailable) {
                Text(
                    text = stringResource(R.string.line_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = 8.dp, end = 8.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = input.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                // The default size of an IconButton is 48dp: easy to hit.
                IconButton(onClick = { viewModel.onRemoveLine(input.productId) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.btn_remove_line))
                }
            }

            Row(
                modifier = Modifier.padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NumberField(
                    label = stringResource(R.string.field_line_quantity),
                    value = input.quantityText,
                    onValueChange = { viewModel.onQuantityChange(input.productId, it) },
                    modifier = Modifier.weight(1f),
                    error = line.quantityError,
                    suffix = unitText
                )
                NumberField(
                    label = stringResource(R.string.field_line_price),
                    value = input.unitPriceText,
                    onValueChange = { viewModel.onUnitPriceChange(input.productId, it) },
                    modifier = Modifier.weight(1f),
                    error = line.priceError,
                    prefix = stringResource(R.string.unit_taka)
                )
                // The line total is quantity x price, worked out here. It is plain text, not a box.
                Column(
                    modifier = Modifier.weight(0.9f).align(Alignment.CenterVertically),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = stringResource(R.string.label_line_total),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = line.lineTotal?.let { stringResource(R.string.price_format, formatTaka(it)) } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // More than the shelf holds: a warning, but the sale is still allowed.
            if (exceedsStock(line.quantity, stock)) {
                Row(
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = StockStatus.LOW.color(),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.stock_warning_line, formatQuantity(stock), unitText),
                        style = MaterialTheme.typography.bodySmall,
                        color = StockStatus.LOW.color()
                    )
                }
            }

            // The price typed here is below what the product cost: a warning, the sale is still allowed.
            if (costPrice != null && isBelowCost(line.unitPrice, costPrice)) {
                Row(
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = StockStatus.LOW.color(),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.below_cost_line, formatTaka(costPrice)),
                        style = MaterialTheme.typography.bodySmall,
                        color = StockStatus.LOW.color()
                    )
                }
            }
        }
    }
}

/** সর্বমোট on the left, and the big "এগিয়ে যান" button on the right (off while anything is invalid). */
@Composable
private fun GrandTotalBar(total: Long, canContinue: Boolean, onContinue: () -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.label_grand_total),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.price_format, formatTaka(total)),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = onContinue,
                enabled = canContinue,
                modifier = Modifier.heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.btn_continue), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

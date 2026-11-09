package com.example.dukanhisabnext.ui.sell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.dao.ProductWithStock
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.NumberField
import com.example.dukanhisabnext.ui.components.rememberImeVisible
import com.example.dukanhisabnext.ui.products.StockStatus
import com.example.dukanhisabnext.ui.products.color
import com.example.dukanhisabnext.ui.products.labelRes
import com.example.dukanhisabnext.ui.products.stockStatus
import com.example.dukanhisabnext.util.formatQuantity
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.toBengaliDigits

/**
 * The বিক্রি tab. Top: search. Middle: products (tap = +1 in the cart). Below: the cart with
 * quantity and price boxes, discount and delivery. Bottom: live subtotal, total and the
 * "এগিয়ে যান" button. While the keyboard is open for a cart box, the product list steps
 * aside so the cart has room.
 */
@Composable
fun SellScreen(
    onContinue: () -> Unit = {},
    viewModel: SellViewModel = appViewModel { app, _ -> SellViewModel(app.productRepository, app.cartStore) }
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val stocks by viewModel.stocks.collectAsStateWithLifecycle()
    val cart by viewModel.cartState.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()

    val imeVisible = rememberImeVisible()
    var searchFocused by remember { mutableStateOf(false) }
    // Show the product list unless the keyboard is open for something other than the search box.
    val showProducts = !imeVisible || searchFocused

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // ---- Search ----
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .onFocusChanged { searchFocused = it.isFocused },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_products)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
            }
        )

        // ---- Products ----
        if (showProducts) {
            ProductPicker(
                products = products,
                searching = query.isNotBlank(),
                quantityInCart = cart.lines.associate { it.productId to it.quantityText },
                onTap = { viewModel.onProductTapped(it) },
                modifier = Modifier.weight(1f)
            )
            HorizontalDivider()
        }

        // ---- Cart ----
        CartPanel(
            cart = cart,
            summary = summary,
            stocks = stocks,
            viewModel = viewModel,
            modifier = Modifier.weight(if (showProducts) 1.2f else 1f)
        )

        // ---- Totals and continue ----
        BottomBar(summary = summary, onContinue = onContinue)
    }
}

// =========================================================================================
// Products
// =========================================================================================

@Composable
private fun ProductPicker(
    products: List<ProductWithStock>?,
    searching: Boolean,
    quantityInCart: Map<Long, String>,
    onTap: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        products == null -> Box(modifier) // still loading
        products.isEmpty() -> Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(if (searching) R.string.empty_search else R.string.empty_products),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        else -> LazyColumn(modifier = modifier) {
            items(products, key = { it.product.id }) { item ->
                ProductPickRow(item, quantityInCart[item.product.id], onClick = { onTap(item.product) })
            }
        }
    }
}

/** One product to tap. A big row (at least 64dp high) so it is easy to hit. */
@Composable
private fun ProductPickRow(item: ProductWithStock, inCartText: String?, onClick: () -> Unit) {
    val product = item.product
    val status = stockStatus(item.stock, product.lowStockLevel)
    val unitText = stringResource(product.unit.labelRes())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.price_format, formatTaka(product.sellPrice)),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(text = "  ·  ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.stock_label, formatQuantity(item.stock), unitText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = status.color()
                )
            }
        }
        // Feedback for the tap: how much of this product is in the cart now.
        if (inCartText != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.in_cart, toBengaliDigits(inCartText)),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
    HorizontalDivider()
}

// =========================================================================================
// Cart
// =========================================================================================

@Composable
private fun CartPanel(
    cart: CartState,
    summary: CartSummary,
    stocks: Map<Long, Long>,
    viewModel: SellViewModel,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // When a NEW line is added, scroll down to it. (Index 0 is the header, line i is index i + 1.)
    var previousLineCount by remember { mutableIntStateOf(cart.lines.size) }
    LaunchedEffect(cart.lines.size) {
        if (cart.lines.size > previousLineCount) listState.animateScrollToItem(cart.lines.size)
        previousLineCount = cart.lines.size
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header") {
            Text(
                text = stringResource(R.string.cart_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (summary.lines.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.cart_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(summary.lines, key = { it.input.productId }) { line ->
                CartLineCard(
                    line = line,
                    stock = stocks[line.input.productId] ?: 0L, // no movements yet = stock 0
                    viewModel = viewModel
                )
            }
        }

        item(key = "extras") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    label = stringResource(R.string.field_discount),
                    value = cart.discountText,
                    onValueChange = viewModel::onDiscountChange,
                    modifier = Modifier.weight(1f),
                    error = summary.discountError,
                    prefix = "৳"
                )
                NumberField(
                    label = stringResource(R.string.field_delivery),
                    value = cart.deliveryText,
                    onValueChange = viewModel::onDeliveryChange,
                    modifier = Modifier.weight(1f),
                    error = summary.deliveryError,
                    prefix = "৳"
                )
            }
        }
    }
}

/** One cart line: name, remove, quantity, price, line total, and the stock warning. */
@Composable
private fun CartLineCard(line: CartLineView, stock: Long, viewModel: SellViewModel) {
    val input = line.input
    val unitText = stringResource(input.unit.labelRes())

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
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
                    prefix = "৳"
                )
                // The rounded line total, exactly what will be saved.
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
        }
    }
}

// =========================================================================================
// Bottom bar
// =========================================================================================

@Composable
private fun BottomBar(summary: CartSummary, onContinue: () -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.label_subtotal) + ": " +
                        stringResource(R.string.price_format, formatTaka(summary.subtotal)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.label_total),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.price_format, formatTaka(summary.total)),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            // Disabled while the cart is empty or a number is wrong. 56dp high: a big target.
            Button(
                onClick = onContinue,
                enabled = summary.canContinue,
                modifier = Modifier.heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.btn_continue), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

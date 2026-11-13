package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.ui.components.LoadingIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.example.dukanhisabnext.ui.components.AppTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import com.example.dukanhisabnext.ui.components.EmptyState
import com.example.dukanhisabnext.ui.components.PillVariant
import com.example.dukanhisabnext.ui.components.ProductTile
import com.example.dukanhisabnext.ui.components.StatusPill
import com.example.dukanhisabnext.ui.theme.AppTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.dao.ProductWithStock
import com.example.dukanhisabnext.data.entity.Category
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.products.color
import com.example.dukanhisabnext.ui.products.labelRes
import com.example.dukanhisabnext.ui.products.stockStatus
import com.example.dukanhisabnext.util.formatQuantity
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.toBengaliDigits

/**
 * Screen A of the বিক্রি tab: the product picker. Search and category chips on top, the full
 * product list below (tap = +1 in the cart), and a fixed bottom bar with the grand total and a
 * button that opens the cart screen.
 */
@Composable
fun SellScreen(
    onOpenCart: () -> Unit,
    viewModel: SellViewModel = appViewModel { app, _ -> SellViewModel(app.productRepository, app.cartStore) }
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val cart by viewModel.cartState.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // The cart came back after a restart, but some products can no longer be sold.
        CartRestoreNote()
        // ---- Search ----
        AppTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_products)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.clear_search))
                    }
                }
            }
        )

        // ---- Category filter ----
        CategoryChips(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            onSelected = viewModel::onCategorySelected
        )

        // ---- Products: the whole middle of the screen ----
        ProductPicker(
            products = products,
            filtering = query.isNotBlank() || selectedCategoryId != null,
            quantityInCart = cart.lines.associate { it.productId to it.quantityText },
            onTap = { viewModel.onProductTapped(it) },
            modifier = Modifier.weight(1f)
        )

        // ---- Fixed bottom bar ----
        PickerBottomBar(
            total = summary.total,
            lineCount = summary.lines.size,
            onOpenCart = onOpenCart
        )
    }
}

// =========================================================================================

@Composable
private fun CategoryChips(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onSelected: (Long?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedCategoryId == null,
                onClick = { onSelected(null) },
                label = { Text(stringResource(R.string.chip_all)) }
            )
        }
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedCategoryId == category.id,
                onClick = { onSelected(category.id) },
                label = { Text(category.name) }
            )
        }
    }
}

@Composable
private fun ProductPicker(
    products: List<ProductWithStock>?,
    filtering: Boolean,
    quantityInCart: Map<Long, String>,
    onTap: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    HorizontalDivider()
    when {
        products == null -> LoadingIndicator(modifier) // still loading
        products.isEmpty() -> EmptyState(
            icon = if (filtering) R.drawable.ic_search else R.drawable.ic_product,
            text = stringResource(if (filtering) R.string.empty_search else R.string.empty_products),
            modifier = modifier
        )
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
            .heightIn(min = 68.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProductTile(name = product.name)
        Column(modifier = Modifier.weight(1f)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.price_format, formatTaka(product.sellPrice)),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(text = "  ·  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = stringResource(R.string.stock_label, formatQuantity(item.stock), unitText),
                    style = MaterialTheme.typography.bodySmall,
                    color = status.color()
                )
            }
        }
        // Feedback for the tap: how much of this product is in the cart now.
        if (inCartText != null) {
            StatusPill(
                text = stringResource(R.string.in_cart, toBengaliDigits(inCartText)),
                variant = PillVariant.Paid
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = AppTheme.colors.border)
}

/**
 * Left: "সর্বমোট ৳ X". Right: a button with the number of cart lines and an arrow, which opens
 * the cart. It is disabled while the cart is empty.
 */
@Composable
private fun PickerBottomBar(total: Long, lineCount: Int, onOpenCart: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = AppTheme.colors.border)
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.label_grand_total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.price_format, formatTaka(total)),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Button(
                    onClick = onOpenCart,
                    enabled = lineCount > 0,
                    shape = com.example.dukanhisabnext.ui.theme.ButtonShape,
                    modifier = Modifier.heightIn(min = 52.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_sell), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = toBengaliDigits(lineCount.toString()),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = stringResource(R.string.btn_open_cart),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

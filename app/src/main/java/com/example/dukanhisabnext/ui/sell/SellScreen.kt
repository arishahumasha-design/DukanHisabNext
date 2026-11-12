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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
        products.isEmpty() -> Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(if (filtering) R.string.empty_search else R.string.empty_products),
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

/**
 * Left: "সর্বমোট ৳ X". Right: a button with the number of cart lines and an arrow, which opens
 * the cart. It is disabled while the cart is empty.
 */
@Composable
private fun PickerBottomBar(total: Long, lineCount: Int, onOpenCart: () -> Unit) {
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
                onClick = onOpenCart,
                enabled = lineCount > 0,
                modifier = Modifier.heightIn(min = 56.dp)
            ) {
                Icon(Icons.Filled.ShoppingCart, contentDescription = null)
                Text(
                    text = toBengaliDigits(lineCount.toString()),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.btn_open_cart)
                )
            }
        }
    }
}

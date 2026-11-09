package com.example.dukanhisabnext.ui.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.dao.ProductWithStock
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.util.formatQuantity
import com.example.dukanhisabnext.util.formatTaka

@Composable
fun ProductListScreen(
    onBack: () -> Unit,
    onAddProduct: () -> Unit,
    onEditProduct: (Long) -> Unit,
    onAdjustStock: (Long) -> Unit,
    viewModel: ProductListViewModel = appViewModel { app, _ -> ProductListViewModel(app.productRepository) }
) {
    // collectAsStateWithLifecycle: turns the ViewModel's live value into screen state,
    // and stops listening while the screen is not visible.
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()

    SubScreenScaffold(
        title = stringResource(R.string.title_products),
        onBack = onBack,
        floatingActionButton = {
            if (!showArchived) {
                FloatingActionButton(onClick = onAddProduct) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_product))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Search box
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
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

            // Category chips: "সব" (all) first, then each category
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { viewModel.onCategorySelected(null) },
                        label = { Text(stringResource(R.string.chip_all)) }
                    )
                }
                items(categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { viewModel.onCategorySelected(category.id) },
                        label = { Text(category.name) }
                    )
                }
            }

            // Switch: show archived products instead of normal ones
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.show_archived),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(checked = showArchived, onCheckedChange = viewModel::onShowArchivedChange)
            }
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            // The list itself. null = still loading (show nothing for that split second).
            val list = products
            when {
                list == null -> Unit
                list.isEmpty() -> {
                    val message = when {
                        query.isNotBlank() || selectedCategoryId != null -> R.string.empty_search
                        showArchived -> R.string.empty_archived
                        else -> R.string.empty_products
                    }
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Extra space at the bottom so the round + button never hides the last row.
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(list, key = { it.product.id }) { item ->
                        ProductRow(
                            item = item,
                            archived = showArchived,
                            onClick = { onEditProduct(item.product.id) },
                            onStock = { onAdjustStock(item.product.id) },
                            onRestore = { viewModel.restore(item.product.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(
    item: ProductWithStock,
    archived: Boolean,
    onClick: () -> Unit,
    onStock: () -> Unit,
    onRestore: () -> Unit
) {
    val product = item.product
    // Stock is never stored: item.stock came from adding up the stock movements.
    val status = stockStatus(item.stock, product.lowStockLevel)
    val unitText = stringResource(product.unit.labelRes())

    ListItem(
        modifier = Modifier.clickable(enabled = !archived, onClick = onClick),
        headlineContent = { Text(product.name) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.price_format, formatTaka(product.sellPrice)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status != StockStatus.NORMAL) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = status.color(),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = stringResource(R.string.stock_label, formatQuantity(item.stock), unitText),
                        color = status.color()
                    )
                    if (status != StockStatus.NORMAL) {
                        val tag = if (status == StockStatus.NEGATIVE) R.string.tag_negative_stock else R.string.tag_low_stock
                        Text(text = " · " + stringResource(tag), color = status.color())
                    }
                }
            }
        },
        trailingContent = {
            if (archived) {
                TextButton(onClick = onRestore) { Text(stringResource(R.string.btn_restore)) }
            } else {
                TextButton(onClick = onStock) { Text(stringResource(R.string.btn_stock)) }
            }
        }
    )
    HorizontalDivider()
}

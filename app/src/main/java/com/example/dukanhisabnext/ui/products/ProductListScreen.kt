package com.example.dukanhisabnext.ui.products

import com.example.dukanhisabnext.ui.components.PdfTopBarButton
import com.example.dukanhisabnext.ui.components.PdfExportDialogs
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.dukanhisabnext.DokanHisabApp
import com.example.dukanhisabnext.ui.components.LoadingIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.example.dukanhisabnext.ui.components.AppTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
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
import com.example.dukanhisabnext.ui.components.EmptyState
import com.example.dukanhisabnext.ui.components.PillVariant
import com.example.dukanhisabnext.ui.components.ProductTile
import com.example.dukanhisabnext.ui.components.SmallOutlinedButton
import com.example.dukanhisabnext.ui.components.PillOutlinedButton
import com.example.dukanhisabnext.ui.components.StatusPill
import com.example.dukanhisabnext.ui.theme.Spacing
import androidx.compose.foundation.layout.heightIn
import com.example.dukanhisabnext.util.formatQuantity
import com.example.dukanhisabnext.util.formatTaka

@Composable
fun ProductListScreen(
    onBack: () -> Unit,
    onAddProduct: () -> Unit,
    onEditProduct: (Long) -> Unit,
    onAdjustStock: (Long) -> Unit,
    viewModel: ProductListViewModel = appViewModel { app, args -> ProductListViewModel(app.productRepository, args) }
) {
    // collectAsStateWithLifecycle: turns the ViewModel's live value into screen state,
    // and stops listening while the screen is not visible.
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val lowStockOnly by viewModel.lowStockOnly.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()

    var pdfOpen by rememberSaveable { mutableStateOf(false) }
    val pdfService = (LocalContext.current.applicationContext as DokanHisabApp).pdfService
    PdfExportDialogs(
        open = pdfOpen, onClose = { pdfOpen = false }, make = { showCost -> pdfService.productList(showCost) },
        textRes = R.string.pdf_products_text, withCostSwitch = true
    )

    SubScreenScaffold(
        title = stringResource(R.string.title_products),
        onBack = onBack,
        actions = { PdfTopBarButton(onClick = { pdfOpen = true }) },
        floatingActionButton = {
            if (!showArchived) {
                FloatingActionButton(onClick = onAddProduct) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add_product))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Search box
            AppTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
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

            // Category chips: "সব" (all) first, then each category
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    // "সব" = no filter at all: it is selected only then, and tapping it clears the category AND "কম স্টক".
                    FilterChip(
                        selected = isAllChipSelected(selectedCategoryId, lowStockOnly),
                        onClick = {
                            viewModel.onCategorySelected(null)
                            viewModel.onLowStockOnlyChange(false)
                        },
                        label = { Text(stringResource(R.string.chip_all)) }
                    )
                }
                // "কম স্টক": only the products that need attention (works with the search and the category)
                item {
                    FilterChip(
                        selected = lowStockOnly,
                        onClick = { viewModel.onLowStockOnlyChange(!lowStockOnly) },
                        label = { Text(stringResource(R.string.chip_low_stock)) }
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

            // The list itself. null = still loading (a quiet ring appears if it takes more than a moment).
            val list = products
            when {
                list == null -> LoadingIndicator(Modifier.fillMaxSize())
                list.isEmpty() -> {
                    val message = when (productEmptyKind(query.isNotBlank(), selectedCategoryId != null, lowStockOnly, showArchived)) {
                        ProductEmptyKind.NO_MATCH -> R.string.empty_search
                        ProductEmptyKind.LOW_STOCK_NONE -> R.string.empty_low_stock
                        ProductEmptyKind.NO_ARCHIVED -> R.string.empty_archived
                        ProductEmptyKind.NO_PRODUCTS -> R.string.empty_products
                    }
                    EmptyState(icon = R.drawable.ic_product, text = stringResource(message))
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !archived, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m)
    ) {
        ProductTile(name = product.name)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.price_format, formatTaka(product.sellPrice)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.stock_label, formatQuantity(item.stock), unitText),
                style = MaterialTheme.typography.bodySmall,
                color = status.color()
            )
            // One pill says what is wrong (the stock text above already shows the amount).
            if (archived) {
                StatusPill(stringResource(R.string.tag_archived), PillVariant.Neutral)
            } else if (status == StockStatus.LOW) {
                StatusPill(stringResource(R.string.tag_low_stock), PillVariant.LowStock)
            } else if (status == StockStatus.NEGATIVE) {
                StatusPill(stringResource(R.string.tag_negative_stock), PillVariant.Cancelled)
            }
        }
        if (archived) {
            SmallOutlinedButton(stringResource(R.string.btn_restore), onRestore)
        } else {
            PillOutlinedButton(stringResource(R.string.btn_stock), onStock)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
}

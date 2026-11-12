package com.example.dukanhisabnext.ui.products

import com.example.dukanhisabnext.data.dao.ProductWithStock

// The "কম স্টক" filter of the product list, as plain Kotlin (no Android) so it can be unit-tested
// on the PC. It uses stockStatus() (ProductLabels.kt): the same rule as the tags on the rows and as
// the Home card's LOW_STOCK_SQL (a phone test checks that the count and the tags agree).

/** A product needs attention when its stock tag is not normal: negative stock, or stock at or below its alert level. */
fun isLowStock(product: ProductWithStock): Boolean =
    stockStatus(product.stock, product.product.lowStockLevel) != StockStatus.NORMAL

/**
 * With the "কম স্টক" chip on, only the products that need attention are shown. It works on top of
 * the search and the category filter, which the database already applied to [list]. The order is kept.
 */
fun filterLowStock(list: List<ProductWithStock>, onlyLowStock: Boolean): List<ProductWithStock> =
    if (onlyLowStock) list.filter(::isLowStock) else list

/** Which message to show for an empty list. */
enum class ProductEmptyKind { NO_MATCH, LOW_STOCK_NONE, NO_ARCHIVED, NO_PRODUCTS }

/**
 * A search or a category is the most specific reason; then the "কম স্টক" chip ("nothing is low" is good
 * news); then the archived switch; then simply no product yet.
 */
fun productEmptyKind(searching: Boolean, categoryChosen: Boolean, onlyLowStock: Boolean, showArchived: Boolean): ProductEmptyKind = when {
    searching || categoryChosen -> ProductEmptyKind.NO_MATCH
    onlyLowStock -> ProductEmptyKind.LOW_STOCK_NONE
    showArchived -> ProductEmptyKind.NO_ARCHIVED
    else -> ProductEmptyKind.NO_PRODUCTS
}

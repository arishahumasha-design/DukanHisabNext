package com.example.dukanhisabnext.ui.sell

// Warnings on a cart line that do NOT stop the sale (M9). Plain Kotlin, PC-tested.

/**
 * The unit price typed in the cart is BELOW the product's buying price: this line loses money.
 * Both in paisa. false while the price box is empty or unreadable (null) or the product has no
 * buying price. A price equal to the cost is not below it.
 */
fun isBelowCost(unitPrice: Long?, costPrice: Long?): Boolean =
    unitPrice != null && costPrice != null && unitPrice < costPrice

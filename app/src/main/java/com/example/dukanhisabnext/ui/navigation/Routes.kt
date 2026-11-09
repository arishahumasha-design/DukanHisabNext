package com.example.dukanhisabnext.ui.navigation

/**
 * Route names for the screens inside the More tab.
 * A "route" is the internal address of a screen. The part in {braces} is an argument
 * (here the product id) that is filled in when we navigate.
 */
object Routes {
    const val MORE = "more"
    const val PRODUCTS = "products"
    const val PRODUCT_EDIT = "product_edit?productId={productId}"
    const val STOCK_ADJUST = "stock_adjust/{productId}"

    /** [productId] = null opens the form empty, to add a new product. */
    fun productEdit(productId: Long?): String =
        if (productId == null) "product_edit" else "product_edit?productId=$productId"

    fun stockAdjust(productId: Long): String = "stock_adjust/$productId"

    /** The two form screens hide the bottom bar, to leave room for the keyboard. */
    fun hidesBottomBar(route: String?): Boolean = route == PRODUCT_EDIT || route == STOCK_ADJUST
}

package com.example.dukanhisabnext.ui.navigation

/**
 * Route names for the screens inside the More and Khata tabs.
 * A "route" is the internal address of a screen. The part in {braces} is an argument
 * (here an id) that is filled in when we navigate.
 */
object Routes {
    // ---- More tab ----
    const val MORE = "more"
    const val PRODUCTS = "products"
    const val PRODUCT_EDIT = "product_edit?productId={productId}"
    const val STOCK_ADJUST = "stock_adjust/{productId}"

    // ---- Sell tab: screen A (picker) and screen B (cart) ----
    const val SELL = "sell"
    const val CART = "cart"

    // ---- Khata tab ----
    const val KHATA = "khata"
    const val CUSTOMER_EDIT = "customer_edit?customerId={customerId}"
    const val CUSTOMER_DETAIL = "customer_detail/{customerId}"

    /** [productId] = null opens the form empty, to add a new product. */
    fun productEdit(productId: Long?): String =
        if (productId == null) "product_edit" else "product_edit?productId=$productId"

    fun stockAdjust(productId: Long): String = "stock_adjust/$productId"

    /** [customerId] = null opens the form empty, to add a new customer. */
    fun customerEdit(customerId: Long?): String =
        if (customerId == null) "customer_edit" else "customer_edit?customerId=$customerId"

    fun customerDetail(customerId: Long): String = "customer_detail/$customerId"

    /**
     * The form screens hide the bottom bar, to leave room for the keyboard. The cart screen
     * hides it too: it has its own bottom bar with the total and the continue button.
     */
    fun hidesBottomBar(route: String?): Boolean =
        route == PRODUCT_EDIT || route == STOCK_ADJUST || route == CUSTOMER_EDIT || route == CART
}

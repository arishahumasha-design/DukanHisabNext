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
    const val SALES_LIST = "sales_list"
    /** A receipt opened from the sales list: only the sale id, so there is no change line. */
    const val SALE_RECEIPT = "sale_receipt/{saleId}"
    const val SETTINGS = "settings"
    const val OWNER = "owner_info"
    const val SHOP = "shop_profile"

    // ---- Sell tab: screen A (picker) and screen B (cart) ----
    const val SELL = "sell"
    const val CART = "cart"
    const val PAYMENT = "payment"
    const val DUE = "due"
    /** The normal add-customer form, opened from a sale. The new customer comes back selected. */
    const val SALE_ADD_CUSTOMER = "sale_add_customer"
    const val RECEIPT = "receipt/{saleId}?change={change}"

    // ---- Khata tab ----
    const val KHATA = "khata"
    const val CUSTOMER_EDIT = "customer_edit?customerId={customerId}"
    const val CUSTOMER_DETAIL = "customer_detail/{customerId}"
    /** The ONE khata entry form. {action} is a LedgerAction name (PAYMENT, MANUAL_DUE, ...). */
    const val LEDGER_ENTRY = "ledger_entry/{customerId}/{action}"
    /** A receipt opened from a customer's history: only the sale id (no change line). */
    const val KHATA_RECEIPT = "khata_receipt/{saleId}"

    /** [productId] = null opens the form empty, to add a new product. */
    fun productEdit(productId: Long?): String =
        if (productId == null) "product_edit" else "product_edit?productId=$productId"

    fun stockAdjust(productId: Long): String = "stock_adjust/$productId"

    /** [customerId] = null opens the form empty, to add a new customer. */
    fun customerEdit(customerId: Long?): String =
        if (customerId == null) "customer_edit" else "customer_edit?customerId=$customerId"

    fun customerDetail(customerId: Long): String = "customer_detail/$customerId"

    fun ledgerEntry(customerId: Long, action: String): String = "ledger_entry/$customerId/$action"

    fun khataReceipt(saleId: Long): String = "khata_receipt/$saleId"

    fun saleReceipt(saleId: Long): String = "sale_receipt/$saleId"

    /** [change] is the ফেরত given back (paisa). It is not stored in the database, so it travels here. */
    fun receipt(saleId: Long, change: Long = 0L): String = "receipt/$saleId?change=$change"

    /**
     * The form screens hide the bottom bar, to leave room for the keyboard. The cart screen
     * hides it too: it has its own bottom bar with the total and the continue button.
     */
    fun hidesBottomBar(route: String?): Boolean =
        route == PRODUCT_EDIT || route == STOCK_ADJUST || route == CUSTOMER_EDIT ||
            route == CART || route == PAYMENT || route == DUE || route == SALE_ADD_CUSTOMER ||
            route == SHOP || route == OWNER || route == LEDGER_ENTRY
}

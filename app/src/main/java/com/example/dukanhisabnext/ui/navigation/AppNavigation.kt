package com.example.dukanhisabnext.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import com.example.dukanhisabnext.ui.components.BottomBar
import com.example.dukanhisabnext.ui.components.BottomBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.dukanhisabnext.ui.components.rememberImeVisible
import com.example.dukanhisabnext.ui.customers.CustomerDetailScreen
import com.example.dukanhisabnext.ui.customers.CustomerEditScreen
import com.example.dukanhisabnext.ui.expense.CategoriesScreen
import com.example.dukanhisabnext.ui.expense.ExpenseFormScreen
import com.example.dukanhisabnext.ui.expense.ExpenseListScreen
import com.example.dukanhisabnext.ui.expense.NO_DELETED_EXPENSE
import com.example.dukanhisabnext.ui.khata.ContactsScreen
import com.example.dukanhisabnext.ui.report.DashboardTarget
import com.example.dukanhisabnext.ui.report.ReportScreen
import com.example.dukanhisabnext.ui.khata.KhataScreen
import com.example.dukanhisabnext.ledger.LedgerAction
import com.example.dukanhisabnext.ui.customers.LedgerFormScreen
import com.example.dukanhisabnext.ui.products.ProductEditScreen
import com.example.dukanhisabnext.ui.products.ProductListScreen
import com.example.dukanhisabnext.ui.products.StockAdjustScreen
import com.example.dukanhisabnext.ui.screens.HomeScreen
import com.example.dukanhisabnext.ui.screens.MoreScreen
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.example.dukanhisabnext.DokanHisabApp
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.customers.CustomerEditScreen
import com.example.dukanhisabnext.ui.sell.CartScreen
import com.example.dukanhisabnext.ui.sell.CheckoutViewModel
import com.example.dukanhisabnext.ui.sell.DueScreen
import com.example.dukanhisabnext.ui.sell.PaymentScreen
import com.example.dukanhisabnext.ui.receipt.ReceiptScreen
import com.example.dukanhisabnext.ui.sales.SalesListScreen
import com.example.dukanhisabnext.ui.backup.BackupOverlays
import com.example.dukanhisabnext.ui.backup.BackupScreen
import com.example.dukanhisabnext.ui.settings.OwnerScreen
import com.example.dukanhisabnext.ui.settings.SettingsScreen
import com.example.dukanhisabnext.ui.shop.ShopScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.ui.sell.SellScreen

/** A note passed back from "দোকানের তথ্য" to Settings: the shop was just saved. */
private const val SHOP_SAVED_KEY = "shop_saved"
private const val LEDGER_SAVED_KEY = "ledger_saved"

/** A note passed back from the expense form to the expense list: this expense was just deleted (offer the undo). */
private const val EXPENSE_DELETED_KEY = "expense_deleted_id"

/** A note passed back from the add-customer form to the Khata tab: this new customer needs a বাকি. */
private const val NEW_DUE_CUSTOMER_KEY = "new_due_customer"
private const val NO_CUSTOMER = -1L

// The whole app frame: bottom bar (the buttons) + NavHost (the picture frame
// that shows whichever screen is selected).
@Composable
fun AppNavigation() {
    // The whole app, with two full-screen layers above it for restore (see BackupOverlays).
    Box(modifier = Modifier.fillMaxSize()) {
        AppFrame()
        BackupOverlays()
    }
}

@Composable
private fun AppFrame() {
    // The NavController remembers which screen we are on (and survives rotation).
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val imeVisible = rememberImeVisible()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // Form screens hide the bar, and so does an open keyboard (the bar would only sit
            // behind it and leave a gap).
            if (!Routes.hidesBottomBar(currentDestination?.route) && !imeVisible) {
                BottomBar(
                    items = Screen.entries.map { screen ->
                        BottomBarItem(
                            label = stringResource(screen.labelRes),
                            icon = screen.icon,
                            // hierarchy = this screen and its parents. A screen inside the
                            // More group still "belongs to" the More tab, so it stays highlighted.
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    // Going "back" from any tab returns to Home, not through every tab.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true // do not stack the same tab twice
                                    restoreState = true
                                }
                            }
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            // With the keyboard open, the screens lift themselves by the keyboard's height
            // (imePadding), and that height already includes the system navigation bar. So the
            // frame's own bottom padding (the nav bar) is dropped then; otherwise it is counted
            // twice and leaves a gap between a pinned Save bar and the keyboard.
            modifier = Modifier.padding(
                top = innerPadding.calculateTopPadding(),
                bottom = if (imeVisible) 0.dp else innerPadding.calculateBottomPadding()
            )
        ) {
            composable(Screen.Home.route) {
                // The four Home cards open the matching screen (the sales and expense lists open on আজ,
                // the product list with the "কম স্টক" chip on, the Khata tab as a tab).
                HomeScreen(
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    // The four quick actions only open screens that already exist.
                    onNewSale = { navController.openTab(Screen.Sell) },
                    onReceiveMoney = { navController.openTab(Screen.Khata) },
                    onNewExpense = { navController.navigate(Routes.expenseForm(null)) },
                    onNewProduct = { navController.navigate(Routes.productEdit(null)) },
                    onOpen = { target ->
                        when (target) {
                            DashboardTarget.SALES_LIST -> navController.navigate(Routes.SALES_LIST)
                            DashboardTarget.EXPENSE_LIST -> navController.navigate(Routes.EXPENSES)
                            DashboardTarget.KHATA -> navController.openTab(Screen.Khata)
                            DashboardTarget.LOW_STOCK_PRODUCTS -> navController.navigate(Routes.products(lowStock = true))
                        }
                    }
                )
            }

            // The বিক্রি tab is a group of two screens: the product picker, then the cart.
            // The cart itself lives in the app-wide CartStore, so going back loses nothing.
            navigation(route = Screen.Sell.route, startDestination = Routes.SELL) {
                composable(Routes.SELL) {
                    SellScreen(onOpenCart = { navController.navigate(Routes.CART) })
                }
                composable(Routes.CART) {
                    CartScreen(
                        onBack = { navController.popBackStack() },
                        onContinue = { navController.navigate(Routes.PAYMENT) }
                    )
                }
                composable(Routes.PAYMENT) { entry ->
                    PaymentScreen(
                        onBack = { navController.popBackStack() },
                        onOpenDue = { navController.navigate(Routes.DUE) },
                        onAddCustomer = { navController.navigate(Routes.SALE_ADD_CUSTOMER) },
                        onSaved = { saleId, change -> navController.openReceiptAfterSale(saleId, change) },
                        viewModel = appViewModel(owner = entry) { app, _ ->
                            CheckoutViewModel(app.saleRepository, app.customerRepository, app.cartStore)
                        }
                    )
                }
                composable(Routes.DUE) { entry ->
                    // The নতুন বাকি screen borrows the payment screen's ViewModel, so the customer
                    // boxes, the note and the save state are shared. remember() looks the payment
                    // screen up once, so this keeps working while the screens change.
                    val paymentEntry = remember(entry) { navController.getBackStackEntry(Routes.PAYMENT) }
                    DueScreen(
                        onBack = { navController.popBackStack() },
                        onAddCustomer = { navController.navigate(Routes.SALE_ADD_CUSTOMER) },
                        onSaved = { saleId, change -> navController.openReceiptAfterSale(saleId, change) },
                        viewModel = appViewModel(owner = paymentEntry) { app, _ ->
                            CheckoutViewModel(app.saleRepository, app.customerRepository, app.cartStore)
                        }
                    )
                }
                composable(Routes.SALE_ADD_CUSTOMER) {
                    // The normal add-customer form. When it saves, the new customer is selected for
                    // the sale in progress, and the payment (or বাকি) screen below it is unchanged:
                    // the amounts, note and switches typed there are kept.
                    val app = LocalContext.current.applicationContext as DokanHisabApp
                    CustomerEditScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { customerId -> app.cartStore.setCustomer(customerId) }
                    )
                }
                composable(
                    route = Routes.RECEIPT,
                    arguments = listOf(
                        navArgument("saleId") { type = NavType.LongType },
                        navArgument("change") {
                            type = NavType.LongType
                            defaultValue = 0L
                        }
                    )
                ) {
                    // The back arrow and the system Back button do the same: leave the receipt. Right
                    // after a sale that lands on the product picker (the cart is empty), never on
                    // the payment screen, because the payment screens were removed from the back
                    // stack when the sale was saved. "নতুন বিক্রয়" goes to the Sell tab.
                    ReceiptScreen(
                        onBack = { navController.popBackStack() },
                        onNewSale = { navController.openNewSale() }
                    )
                }
            }

            // The Khata tab: the বাকির খাতা screen, and the customer screens opened from it.
            navigation(route = Screen.Khata.route, startDestination = Routes.KHATA) {
                composable(Routes.KHATA) { entry ->
                    // The add-customer form (opened from the "নতুন বাকি" picker) leaves the new
                    // customer's id here; then the বাকি দিলাম form opens for that customer.
                    val newDueCustomer by entry.savedStateHandle
                        .getStateFlow(NEW_DUE_CUSTOMER_KEY, NO_CUSTOMER)
                        .collectAsStateWithLifecycle()
                    LaunchedEffect(newDueCustomer) {
                        if (newDueCustomer != NO_CUSTOMER) {
                            entry.savedStateHandle[NEW_DUE_CUSTOMER_KEY] = NO_CUSTOMER
                            navController.navigate(KHATA_CUSTOMER_ROUTES.entryOf(newDueCustomer, LedgerAction.MANUAL_DUE.name))
                        }
                    }
                    KhataScreen(
                        onOpenCustomer = { id -> navController.navigate(KHATA_CUSTOMER_ROUTES.detailOf(id)) },
                        onOpenReceipt = { saleId -> navController.navigate(KHATA_CUSTOMER_ROUTES.receiptOf(saleId)) },
                        // "নতুন বাকি": the picked customer goes straight to the বাকি দিলাম form.
                        onNewDue = { id ->
                            navController.navigate(KHATA_CUSTOMER_ROUTES.entryOf(id, LedgerAction.MANUAL_DUE.name))
                        },
                        onAddCustomer = { navController.navigate(Routes.KHATA_ADD_CUSTOMER) }
                    )
                }
                composable(Routes.KHATA_ADD_CUSTOMER) {
                    CustomerEditScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { id ->
                            navController.previousBackStackEntry?.savedStateHandle?.set(NEW_DUE_CUSTOMER_KEY, id)
                        }
                    )
                }
                customerScreens(navController, KHATA_CUSTOMER_ROUTES)
            }

            // The More tab is a small group ("graph") of screens that start at the More list.
            navigation(route = Screen.More.route, startDestination = Routes.MORE) {
                composable(Routes.MORE) {
                    MoreScreen(
                        onOpenProducts = { navController.navigate(Routes.products(lowStock = false)) },
                        onOpenContacts = { navController.navigate(Routes.CONTACTS) },
                        onOpenSales = { navController.navigate(Routes.SALES_LIST) },
                        onOpenExpenses = { navController.navigate(Routes.EXPENSES) },
                        onOpenReports = { navController.navigate(Routes.REPORTS) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                    )
                }
                composable(Routes.SALES_LIST) {
                    // The list keeps its search and filter in its ViewModel, so Back from a
                    // receipt returns to exactly the same list.
                    SalesListScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSale = { saleId -> navController.navigate(Routes.saleReceipt(saleId)) }
                    )
                }
                composable(
                    route = Routes.SALE_RECEIPT,
                    arguments = listOf(navArgument("saleId") { type = NavType.LongType })
                ) {
                    // The same receipt screen as after a sale, but with only the sale id: there is no
                    // "change" in the route, so no change (ফেরত) line is shown. It lives in the More
                    // group so the More tab stays highlighted. Back returns to the list.
                    ReceiptScreen(
                        onBack = { navController.popBackStack() },
                        onNewSale = { navController.openNewSale() }
                    )
                }
                composable(Routes.SETTINGS) { entry ->
                    // "দোকানের তথ্য" leaves a "saved" note here when it closes after saving.
                    val shopSaved by entry.savedStateHandle
                        .getStateFlow(SHOP_SAVED_KEY, false)
                        .collectAsStateWithLifecycle()
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onEditOwner = {
                            entry.savedStateHandle[SHOP_SAVED_KEY] = false
                            navController.navigate(Routes.OWNER)
                        },
                        onOpenShop = {
                            entry.savedStateHandle[SHOP_SAVED_KEY] = false
                            navController.navigate(Routes.SHOP)
                        },
                        onOpenBackup = { navController.navigate(Routes.BACKUP) },
                        shopSavedMessage = shopSaved
                    )
                }
                composable(Routes.BACKUP) {
                    BackupScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.OWNER) {
                    OwnerScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SHOP) {
                    ShopScreen(
                        onBack = { navController.popBackStack() },
                        // After saving, go back to Settings (it shows "saved" and the new shop label).
                        onSaved = {
                            navController.previousBackStackEntry?.savedStateHandle?.set(SHOP_SAVED_KEY, true)
                            navController.popBackStack()
                        }
                    )
                }
                composable(Routes.EXPENSES) { entry ->
                    // The expense form leaves the id of a just-deleted expense here, so the list can offer the undo.
                    val deletedId by entry.savedStateHandle
                        .getStateFlow(EXPENSE_DELETED_KEY, NO_DELETED_EXPENSE)
                        .collectAsStateWithLifecycle()
                    ExpenseListScreen(
                        onBack = { navController.popBackStack() },
                        onNewExpense = { navController.navigate(Routes.expenseForm(null)) },
                        onEditExpense = { id -> navController.navigate(Routes.expenseForm(id)) },
                        onOpenCategories = { navController.navigate(Routes.EXPENSE_CATEGORIES) },
                        deletedId = deletedId,
                        onDeletedShown = { entry.savedStateHandle[EXPENSE_DELETED_KEY] = NO_DELETED_EXPENSE }
                    )
                }
                composable(
                    route = Routes.EXPENSE_FORM,
                    // -1 means "no expense given" = add a new one.
                    arguments = listOf(navArgument("expenseId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    })
                ) {
                    ExpenseFormScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                        // After a delete, go back to the list; it shows the "ফিরিয়ে আনুন" undo bar.
                        onDeleted = { id ->
                            navController.previousBackStackEntry?.savedStateHandle?.set(EXPENSE_DELETED_KEY, id)
                            navController.popBackStack()
                        }
                    )
                }
                composable(Routes.REPORTS) {
                    // A sale opens its receipt and a customer opens the customer detail, both inside the More group.
                    ReportScreen(
                        onBack = { navController.popBackStack() },
                        onOpenReceipt = { saleId -> navController.navigate(Routes.saleReceipt(saleId)) },
                        onOpenCustomer = { id -> navController.navigate(CONTACT_CUSTOMER_ROUTES.detailOf(id)) }
                    )
                }
                composable(Routes.EXPENSE_CATEGORIES) {
                    CategoriesScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.CONTACTS) {
                    // The address book. Its customer screens are registered next, inside this More group.
                    ContactsScreen(
                        onBack = { navController.popBackStack() },
                        onAddCustomer = { navController.navigate(CONTACT_CUSTOMER_ROUTES.editOf(null)) },
                        onOpenCustomer = { id -> navController.navigate(CONTACT_CUSTOMER_ROUTES.detailOf(id)) }
                    )
                }
                customerScreens(navController, CONTACT_CUSTOMER_ROUTES)
                composable(
                    route = Routes.PRODUCTS,
                    // false unless the Home card "কম স্টক" opens the list with the chip already on.
                    arguments = listOf(navArgument("lowStock") {
                        type = NavType.BoolType
                        defaultValue = false
                    })
                ) {
                    ProductListScreen(
                        onBack = { navController.popBackStack() },
                        onAddProduct = { navController.navigate(Routes.productEdit(null)) },
                        onEditProduct = { id -> navController.navigate(Routes.productEdit(id)) },
                        onAdjustStock = { id -> navController.navigate(Routes.stockAdjust(id)) }
                    )
                }
                composable(
                    route = Routes.PRODUCT_EDIT,
                    // -1 means "no product given" = add a new one.
                    arguments = listOf(navArgument("productId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    })
                ) {
                    ProductEditScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.STOCK_ADJUST,
                    arguments = listOf(navArgument("productId") { type = NavType.LongType })
                ) {
                    StockAdjustScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

/**
 * The customer screens of one tab: detail (info, balance, buttons, history), edit, the entry form and
 * the receipt of a sale. Called once for the Khata tab and once for যোগাযোগ, with different route
 * names, so each tab keeps its own back stack and stays highlighted.
 */
private fun NavGraphBuilder.customerScreens(navController: NavController, r: CustomerRoutes) {
    composable(
        route = r.detail,
        arguments = listOf(navArgument("customerId") { type = NavType.LongType })
    ) { entry ->
        // The entry form leaves a "saved" note here when it closes after saving.
        val ledgerSaved by entry.savedStateHandle
            .getStateFlow(LEDGER_SAVED_KEY, false)
            .collectAsStateWithLifecycle()
        CustomerDetailScreen(
            onBack = { navController.popBackStack() },
            onEdit = { id -> navController.navigate(r.editOf(id)) },
            onAction = { id, action ->
                entry.savedStateHandle[LEDGER_SAVED_KEY] = false
                navController.navigate(r.entryOf(id, action.name))
            },
            onOpenReceipt = { saleId -> navController.navigate(r.receiptOf(saleId)) },
            justSaved = ledgerSaved,
            onSavedShown = { entry.savedStateHandle[LEDGER_SAVED_KEY] = false }
        )
    }
    composable(
        route = r.edit,
        // -1 means "no customer given" = add a new one.
        arguments = listOf(navArgument("customerId") {
            type = NavType.LongType
            defaultValue = -1L
        })
    ) {
        CustomerEditScreen(onBack = { navController.popBackStack() })
    }
    composable(
        route = r.entry,
        arguments = listOf(
            navArgument("customerId") { type = NavType.LongType },
            navArgument("action") { type = NavType.StringType }
        )
    ) {
        LedgerFormScreen(
            onBack = { navController.popBackStack() },
            // After saving, go back to the customer (it shows a short saved message).
            onSaved = {
                navController.previousBackStackEntry?.savedStateHandle?.set(LEDGER_SAVED_KEY, true)
                navController.popBackStack()
            }
        )
    }
    composable(
        route = r.receipt,
        arguments = listOf(navArgument("saleId") { type = NavType.LongType })
    ) {
        // The same receipt screen, with only the sale id (no change line). Back returns to the customer.
        ReceiptScreen(
            onBack = { navController.popBackStack() },
            onNewSale = { navController.openNewSale() }
        )
    }
}

/**
 * After a successful sale: open the receipt and remove the cart, payment and নতুন বাকি screens
 * from the back stack (everything above the product picker), so Back can never return to a
 * finished sale.
 */
private fun NavController.openReceiptAfterSale(saleId: Long, change: Long) {
    navigate(Routes.receipt(saleId, change)) {
        popUpTo(Routes.SELL) { inclusive = false }
    }
}

/**
 * Opens a bottom-bar tab the same way a tap on it does (the tab's own screens are kept and restored,
 * and Back from a tab goes to Home).
 */
private fun NavController.openTab(screen: Screen) {
    navigate(screen.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Goes to the Sell tab's product picker. If the picker is already on the back stack (a receipt
 * opened right after a sale), everything above it is removed. Otherwise (a receipt opened from
 * another tab, for example a sales list) the Sell tab is opened fresh.
 */
private fun NavController.openNewSale() {
    if (popBackStack(Routes.SELL, inclusive = false)) return
    navigate(Routes.SELL) {
        popUpTo(graph.findStartDestination().id) // leave the current tab's screens
        launchSingleTop = true
    }
}

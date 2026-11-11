package com.example.dukanhisabnext.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.dukanhisabnext.ui.customers.CustomerListScreen
import com.example.dukanhisabnext.ui.products.ProductEditScreen
import com.example.dukanhisabnext.ui.products.ProductListScreen
import com.example.dukanhisabnext.ui.products.StockAdjustScreen
import com.example.dukanhisabnext.ui.screens.HomeScreen
import com.example.dukanhisabnext.ui.screens.MoreScreen
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.example.dukanhisabnext.DokanHisabApp
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.customers.CustomerEditScreen
import com.example.dukanhisabnext.ui.sell.CartScreen
import com.example.dukanhisabnext.ui.sell.CheckoutViewModel
import com.example.dukanhisabnext.ui.sell.DueScreen
import com.example.dukanhisabnext.ui.sell.PaymentScreen
import com.example.dukanhisabnext.ui.receipt.ReceiptScreen
import com.example.dukanhisabnext.ui.shop.ShopScreen
import com.example.dukanhisabnext.ui.sell.SellScreen

// The whole app frame: bottom bar (the buttons) + NavHost (the picture frame
// that shows whichever screen is selected).
@Composable
fun AppNavigation() {
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
                NavigationBar {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
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
                            },
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(stringResource(screen.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { HomeScreen() }

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

            // The Khata tab is a small group of screens that start at the customer list.
            navigation(route = Screen.Khata.route, startDestination = Routes.KHATA) {
                composable(Routes.KHATA) {
                    CustomerListScreen(
                        onAddCustomer = { navController.navigate(Routes.customerEdit(null)) },
                        onOpenCustomer = { id -> navController.navigate(Routes.customerDetail(id)) }
                    )
                }
                composable(
                    route = Routes.CUSTOMER_DETAIL,
                    arguments = listOf(navArgument("customerId") { type = NavType.LongType })
                ) {
                    CustomerDetailScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate(Routes.customerEdit(id)) }
                    )
                }
                composable(
                    route = Routes.CUSTOMER_EDIT,
                    // -1 means "no customer given" = add a new one.
                    arguments = listOf(navArgument("customerId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    })
                ) {
                    CustomerEditScreen(onBack = { navController.popBackStack() })
                }
            }

            // The More tab is a small group ("graph") of screens that start at the More list.
            navigation(route = Screen.More.route, startDestination = Routes.MORE) {
                composable(Routes.MORE) {
                    MoreScreen(
                        onOpenProducts = { navController.navigate(Routes.PRODUCTS) },
                        onOpenShop = { navController.navigate(Routes.SHOP) }
                    )
                }
                composable(Routes.SHOP) {
                    ShopScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.PRODUCTS) {
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

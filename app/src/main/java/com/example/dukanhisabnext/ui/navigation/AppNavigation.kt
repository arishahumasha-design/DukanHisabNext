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
            composable(Screen.Sell.route) { SellScreen() }

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
                    MoreScreen(onOpenProducts = { navController.navigate(Routes.PRODUCTS) })
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

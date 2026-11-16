package com.example.dukanhisabnext.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.ui.navigation.Routes
import com.example.dukanhisabnext.ui.navigation.Screen
import com.example.dukanhisabnext.ui.navigation.openTab
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bottom-bar tab rule on a real NavController with the same graph shape as the app
 * (Home, and the Sell, Khata and More groups), using the app's own [openTab].
 */
@RunWith(AndroidJUnit4::class)
class TabNavigationPhoneTest {
    // No Espresso here: the one in this project cannot talk to the newest emulator images. The activity is driven
    // directly and the test waits until the screen has settled.
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun close() { scenario?.close() }

    private lateinit var nav: NavHostController

    private fun start() {
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario!!.onActivity { activity -> activity.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Screen.Home.route) {
                composable(Screen.Home.route) {}
                composable(Routes.BACKUP) {}
                navigation(route = Screen.Sell.route, startDestination = Routes.SELL) {
                    composable(Routes.SELL) {}
                    composable(Routes.CART) {}
                    composable(Routes.PAYMENT) {}
                    composable(Routes.RECEIPT) {}
                }
                navigation(route = Screen.Khata.route, startDestination = Routes.KHATA) {
                    composable(Routes.KHATA) {}
                    composable(Routes.CUSTOMER_DETAIL) {}
                }
                navigation(route = Screen.More.route, startDestination = Routes.MORE) {
                    composable(Routes.MORE) {}
                    composable(Routes.REPORTS) {}
                }
            }
        } }
        settle()
    }

    private fun settle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(3) { instrumentation.waitForIdleSync(); Thread.sleep(400) }
    }

    private fun go(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
        settle()
    }

    private val route get() = nav.currentDestination?.route

    @Test
    fun leavingSellOnThePaymentScreenAndTappingSellAgainOpensTheStartScreen() {
        start()
        go { nav.openTab(Screen.Sell) }
        go { nav.navigate(Routes.CART) }
        go { nav.navigate(Routes.PAYMENT) }
        assertEquals(Routes.PAYMENT, route)
        go { nav.openTab(Screen.Home) }
        assertEquals(Screen.Home.route, route)
        go { nav.openTab(Screen.Sell) }
        assertEquals(Routes.SELL, route)
    }

    @Test
    fun aReceiptLeftOpenInSellIsNotReopenedByTheSellTab() {
        start()
        go { nav.openTab(Screen.Sell) }
        go { nav.navigate(Routes.receipt(7L)) }
        assertEquals(Routes.RECEIPT, route)
        go { nav.openTab(Screen.Khata) }
        go { nav.openTab(Screen.Sell) }
        assertEquals(Routes.SELL, route)
    }

    @Test
    fun tappingTheOpenSellTabGoesBackToItsStartScreen() {
        start()
        go { nav.openTab(Screen.Sell) }
        go { nav.navigate(Routes.CART) }
        assertEquals(Routes.CART, route)
        go { nav.openTab(Screen.Sell) }
        assertEquals(Routes.SELL, route)
    }

    @Test
    fun tappingTheOpenMoreTabGoesBackToTheMoreList() {
        start()
        go { nav.openTab(Screen.More) }
        go { nav.navigate(Routes.REPORTS) }
        go { nav.openTab(Screen.More) }
        assertEquals(Routes.MORE, route)
    }

    @Test
    fun theOtherTabsStillKeepTheirPlace() {
        start()
        go { nav.openTab(Screen.Khata) }
        go { nav.navigate(Routes.customerDetail(3L)) }
        go { nav.openTab(Screen.Home) }
        go { nav.openTab(Screen.Khata) }
        assertEquals(Routes.CUSTOMER_DETAIL, route)
    }

    @Test
    fun sellOpenedFromAScreenInNoTabStartsFresh() {
        start()
        go { nav.openTab(Screen.Sell) }
        go { nav.navigate(Routes.CART) }
        go { nav.openTab(Screen.Home) }
        go { nav.navigate(Routes.BACKUP) }
        go { nav.openTab(Screen.Sell) }
        assertEquals(Routes.SELL, route)
    }
}

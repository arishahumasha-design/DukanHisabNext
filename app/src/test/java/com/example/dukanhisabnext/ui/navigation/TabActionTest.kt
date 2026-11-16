package com.example.dukanhisabnext.ui.navigation

import com.example.dukanhisabnext.backup.nudgeAllowedNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabActionTest {

    @Test
    fun theSellTabAlwaysOpensOnItsFirstScreenFromAnotherTabOrAScreenInNoTab() {
        for (from in listOf(Screen.Home, Screen.Khata, Screen.More, null)) {
            assertEquals("from $from", TabAction.OPEN_FRESH, tabAction(Screen.Sell, from))
        }
    }

    @Test
    fun aTapOnTheOpenTabGoesBackToItsFirstScreen() {
        for (tab in Screen.entries) assertEquals(TabAction.POP_TO_START, tabAction(tab, tab))
    }

    @Test
    fun theOtherTabsKeepTheirPlace() {
        for (target in listOf(Screen.Home, Screen.Khata, Screen.More)) {
            assertEquals(TabAction.OPEN_RESTORING, tabAction(target, Screen.Sell))
        }
        assertEquals(TabAction.OPEN_RESTORING, tabAction(Screen.More, Screen.Khata))
    }

    @Test
    fun everyTabHasAFirstScreen() {
        assertEquals("sell", Screen.Sell.startRoute)
        assertEquals("khata", Screen.Khata.startRoute)
        assertEquals("more", Screen.More.startRoute)
        assertEquals("home", Screen.Home.startRoute)
    }

    @Test
    fun theBackupDialogOpensOnlyWhileHomeIsInFrontAndNoRestoreIsShowing() {
        assertTrue(nudgeAllowedNow(homeIsShown = true, restoreRunning = false, restoreNotePending = false))
        // behind the cart, payment, receipt, forms, backup, restore and shop screens Home is not in front
        assertFalse(nudgeAllowedNow(homeIsShown = false, restoreRunning = false, restoreNotePending = false))
        assertFalse(nudgeAllowedNow(homeIsShown = true, restoreRunning = true, restoreNotePending = false))
        assertFalse(nudgeAllowedNow(homeIsShown = true, restoreRunning = false, restoreNotePending = true))
    }
}

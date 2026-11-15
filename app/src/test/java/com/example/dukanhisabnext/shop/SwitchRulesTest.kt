package com.example.dukanhisabnext.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Switching to another shop is refused while something of the open shop is still running. */
class SwitchRulesTest {
    private val two = ShopRegistry.legacy(1, opened = true).renamed(1, "A", null).withNewShop("B", null, 2).first

    @Test
    fun aSwitchWaitsForABackupOrRestore() {
        assertEquals(SwitchBlock.BACKUP_RUNNING, switchBlock(two, 2, backupBusy = true, saving = false))
    }

    @Test
    fun aSwitchWaitsForASaleOrPaymentBeingSaved() {
        assertEquals(SwitchBlock.SAVING, switchBlock(two, 2, backupBusy = false, saving = true))
    }

    @Test
    fun theOpenShopAndUnknownShopsAreNotSwitchedTo() {
        assertEquals(SwitchBlock.ALREADY_OPEN, switchBlock(two, 1, backupBusy = false, saving = false))
        assertEquals(SwitchBlock.NOT_FOUND, switchBlock(two, 7, backupBusy = false, saving = false))
        assertEquals(SwitchBlock.NOT_FOUND, switchBlock(two.markedDeleting(2), 2, backupBusy = false, saving = false))
    }

    @Test
    fun otherwiseTheSwitchMayHappen() {
        assertNull(switchBlock(two, 2, backupBusy = false, saving = false))
    }
}

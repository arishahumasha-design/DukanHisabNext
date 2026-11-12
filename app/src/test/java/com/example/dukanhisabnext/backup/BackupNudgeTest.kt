package com.example.dukanhisabnext.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one-time "ব্যাকআপ নিন" dialog: after 5 sales, no backup ever, at most twice, never after it was used. */
class BackupNudgeTest {

    @Test
    fun theNumbersAreFiveSalesAndTwoShows() {
        assertEquals(5L, NUDGE_AFTER_SALES)
        assertEquals(2, MAX_NUDGE_SHOWS)
    }

    @Test
    fun itAppearsFromTheFifthSaleWhenThereWasNeverABackup() {
        assertFalse(shouldShowBackupNudge(salesSaved = 4, lastBackupAt = null, nudgeUsed = false, timesShown = 0))
        assertTrue(shouldShowBackupNudge(5, null, false, 0))
        assertTrue(shouldShowBackupNudge(120, null, false, 0))
    }

    @Test
    fun itNeverAppearsWhenABackupExists() {
        assertFalse(shouldShowBackupNudge(50, lastBackupAt = 1_762_927_740_000, nudgeUsed = false, timesShown = 0))
    }

    @Test
    fun itStopsAfterBackupNowWasUsed() {
        assertFalse(shouldShowBackupNudge(50, null, nudgeUsed = true, timesShown = 0))
        assertFalse(shouldShowBackupNudge(50, null, nudgeUsed = true, timesShown = 1))
    }

    @Test
    fun itAppearsAtMostTwice() {
        assertTrue(shouldShowBackupNudge(50, null, false, timesShown = 0))
        assertTrue("shown once, the user said later", shouldShowBackupNudge(50, null, false, timesShown = 1))
        assertFalse("shown twice: enough", shouldShowBackupNudge(50, null, false, timesShown = 2))
        assertFalse(shouldShowBackupNudge(50, null, false, timesShown = 7))
    }

    @Test
    fun theSevenDayHomeLineIsASeparateRuleAndIsUnchanged() {
        val day = 24L * 60 * 60 * 1000
        val now = 10 * day
        assertFalse(shouldRemindBackup(now, now - 7 * day, hasData = true))
        assertTrue(shouldRemindBackup(now, now - 7 * day - 1, hasData = true))
        assertTrue(shouldRemindBackup(now, null, hasData = true))
    }
}

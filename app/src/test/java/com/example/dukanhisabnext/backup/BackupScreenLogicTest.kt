package com.example.dukanhisabnext.backup

import com.example.dukanhisabnext.util.DayPeriod
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The decisions of the backup screens: texts, the Home reminder, what each job state shows, the notes after a restart. */
class BackupScreenLogicTest {

    private fun at(utc: String) = Instant.parse(utc).toEpochMilli()

    private val words: (DayPeriod) -> String = {
        when (it) {
            DayPeriod.MORNING -> "সকাল"
            DayPeriod.NOON -> "দুপুর"
            DayPeriod.AFTERNOON -> "বিকাল"
            DayPeriod.EVENING -> "সন্ধ্যা"
            DayPeriod.NIGHT -> "রাত"
        }
    }

    // ---- the last backup text ----

    @Test
    fun noBackupYetShowsTheNeverText() {
        assertEquals("এখনও ব্যাকআপ নেওয়া হয়নি", lastBackupText(null, "এখনও ব্যাকআপ নেওয়া হয়নি", { "শেষ ব্যাকআপ: $it" }, words))
    }

    @Test
    fun theLastBackupIsShownInDhakaTimeWithBengaliDigits() {
        // 06:09 UTC = 12:09 in Dhaka.
        assertEquals(
            "শেষ ব্যাকআপ: ১২/১১/২০২৬ দুপুর ১২:০৯",
            lastBackupText(at("2026-11-12T06:09:00Z"), "-", { "শেষ ব্যাকআপ: $it" }, words)
        )
    }

    @Test
    fun justAfterMidnightInDhakaIsTheNextDay() {
        // 18:30 UTC on the 12th = 00:30 on the 13th in Dhaka (shown as 12:30 at night).
        assertEquals("১৩/১১/২০২৬ রাত ১২:৩০", backupDateTimeText(at("2026-11-12T18:30:00Z"), words))
        // 17:59 UTC = 23:59 on the 12th.
        assertEquals("১২/১১/২০২৬ রাত ১১:৫৯", backupDateTimeText(at("2026-11-12T17:59:00Z"), words))
    }

    // ---- the file name ----

    @Test
    fun theSavePickerNameHasLatinDigitsAndDhakaTime() {
        assertEquals("DokanHisab-backup-2026-11-12-1209.zip", saveFileName(at("2026-11-12T06:09:00Z")))
        assertEquals("DokanHisab-backup-2026-11-13-0030.zip", saveFileName(at("2026-11-12T18:30:00Z")))
        assertTrue(saveFileName(0).all { it.code < 128 })
    }

    // ---- the Home reminder ----

    private val day = 24L * 60 * 60 * 1000
    private val now = at("2026-11-12T06:09:00Z")

    @Test
    fun noDataYetMeansNoReminderEvenWithoutABackup() {
        assertFalse(shouldRemindBackup(now, null, hasData = false))
        assertFalse(shouldRemindBackup(now, now - 30 * day, hasData = false))
    }

    @Test
    fun dataAndNeverABackupShowsTheReminder() {
        assertTrue(shouldRemindBackup(now, null, hasData = true))
    }

    @Test
    fun theReminderStartsOnlyAfterMoreThanSevenDays() {
        assertFalse("just now", shouldRemindBackup(now, now, true))
        assertFalse("6 days", shouldRemindBackup(now, now - 6 * day, true))
        assertFalse("exactly 7 days", shouldRemindBackup(now, now - 7 * day, true))
        assertTrue("7 days and 1 ms", shouldRemindBackup(now, now - 7 * day - 1, true))
        assertTrue("8 days", shouldRemindBackup(now, now - 8 * day, true))
        assertEquals(7 * day, REMINDER_AFTER_MILLIS)
    }

    @Test
    fun aBackupTimeInTheFutureCountsAsRecent() {
        // The phone's clock was set back: never nag because of that.
        assertFalse(shouldRemindBackup(now, now + 3 * day, true))
    }

    @Test
    fun aSaleACustomerOrAProductIsData() {
        assertFalse(hasShopData(0, 0, 0))
        assertTrue(hasShopData(1, 0, 0))
        assertTrue(hasShopData(0, 1, 0))
        assertTrue(hasShopData(0, 0, 1))
    }

    // ---- what each job state shows ----

    private val summary = RestoreSummary(1, mapOf("sales" to 5), mapOf("sales" to 2))
    private val everyState: List<BackupJobState> =
        listOf(BackupJobState.Idle) + JobStep.entries.map { BackupJobState.Working(it) } + listOf(
            BackupJobState.AwaitingConfirm(summary),
            BackupJobState.ShareReady(File("x.zip")),
            BackupJobState.Done,
            BackupJobState.Failed(BackupProblem.NOT_A_ZIP),
            BackupJobState.Restarting
        )

    @Test
    fun buttonsWorkOnlyWhenNothingRunsAndNothingWaits() {
        for (state in everyState) {
            val expected = state is BackupJobState.Idle || state is BackupJobState.Done || state is BackupJobState.Failed
            assertEquals("$state", expected, backupScreenUi(state, false).buttonsEnabled)
            assertEquals("$state", expected, canStartJob(state))
        }
    }

    @Test
    fun onlyTheSwapAndTheRestartCoverTheWholeApp() {
        for (state in everyState) {
            val expected = state == BackupJobState.Working(JobStep.RESTORING) ||
                state == BackupJobState.Working(JobStep.UNDOING) || state == BackupJobState.Restarting
            assertEquals("$state", expected, blocksApp(state))
            assertEquals("$state", expected, backupScreenUi(state, false).blocksApp)
        }
        // The long checks do NOT block: Back may leave the screen and the work goes on.
        assertFalse(blocksApp(BackupJobState.Working(JobStep.CHECKING)))
        assertFalse(blocksApp(BackupJobState.Working(JobStep.MAKING_BACKUP)))
    }

    @Test
    fun progressShowsOnlyWhileWorking() {
        for (state in everyState) assertEquals("$state", state is BackupJobState.Working, backupScreenUi(state, false).showProgress)
    }

    @Test
    fun theUndoButtonShowsOnlyWhileASafetyCopyExists() {
        for (state in everyState) {
            assertTrue("$state", backupScreenUi(state, hasSafetyCopy = true).showUndo)
            assertFalse("$state", backupScreenUi(state, hasSafetyCopy = false).showUndo)
        }
    }

    @Test
    fun doneFailureAndSummaryEachShowOnlyInTheirOwnState() {
        assertTrue(backupScreenUi(BackupJobState.Done, false).showDone)
        assertEquals(BackupProblem.NOT_A_ZIP, backupScreenUi(BackupJobState.Failed(BackupProblem.NOT_A_ZIP), false).failure)
        assertTrue(backupScreenUi(BackupJobState.AwaitingConfirm(summary), false).showSummary)
        for (state in everyState) {
            val ui = backupScreenUi(state, false)
            assertEquals("$state", state is BackupJobState.Done, ui.showDone)
            assertEquals("$state", state is BackupJobState.Failed, ui.failure != null)
            assertEquals("$state", state is BackupJobState.AwaitingConfirm, ui.showSummary)
        }
    }

    // ---- the restore summary ----

    @Test
    fun theSummaryHasTheFiveRowsInOrderAndMissingTablesAreZero() {
        val rows = summaryRows(RestoreSummary(null, mapOf("sales" to 12, "ledger_entries" to 3), mapOf("customers" to 9, "sales" to 4)))
        assertEquals(listOf("sales", "customers", "products", "expenses", "ledger_entries"), rows.map { it.table })
        assertEquals(SummaryRow("sales", 12, 4), rows[0])
        assertEquals(SummaryRow("customers", 0, 9), rows[1])
        assertEquals(SummaryRow("products", 0, 0), rows[2])
        assertEquals(SummaryRow("ledger_entries", 3, 0), rows[4])
    }

    // ---- the note after a restart ----

    @Test
    fun aFinishedRestoreShowsItsResultOnce() {
        val result = mapOf(
            "fromSchemaVersion" to "2", "backupCreatedAt" to "1762927740000", "undo" to "false",
            "count.sales" to "7", "count.customers" to "3", "count.bad" to "x"
        )
        val note = startupNote(result, RecoveryOutcome.NOTHING, restoreFailed = false)
        assertEquals(StartupNote.Restored(1762927740000, false, mapOf("sales" to 7L, "customers" to 3L)), note)
    }

    @Test
    fun anUndoResultHasNoBackupDate() {
        val note = startupNote(mapOf("undo" to "true", "count.sales" to "1"), RecoveryOutcome.NOTHING, false)
        assertEquals(StartupNote.Restored(null, true, mapOf("sales" to 1L)), note)
    }

    @Test
    fun theRecoveryNoteComesFromTheStartupCheck() {
        assertEquals(
            StartupNote.Recovery(RecoveryOutcome.NOT_FINISHED_NOTHING_CHANGED),
            startupNote(null, RecoveryOutcome.NOT_FINISHED_NOTHING_CHANGED, false)
        )
        assertEquals(StartupNote.Recovery(RecoveryOutcome.FINISHED), startupNote(null, RecoveryOutcome.FINISHED, false))
    }

    @Test
    fun aFailedRestoreIsReportedAfterTheRestart() {
        assertEquals(StartupNote.Failed, startupNote(null, RecoveryOutcome.NOTHING, restoreFailed = true))
    }

    @Test
    fun nothingToSayAtANormalStart() {
        assertNull(startupNote(null, RecoveryOutcome.NOTHING, false))
    }

    @Test
    fun aResultWinsOverTheOtherNotes() {
        val note = startupNote(mapOf("undo" to "false"), RecoveryOutcome.FINISHED, true)
        assertTrue(note is StartupNote.Restored)
    }
}

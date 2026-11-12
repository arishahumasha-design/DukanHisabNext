package com.example.dukanhisabnext.backup

import com.example.dukanhisabnext.util.DayPeriod
import com.example.dukanhisabnext.util.dayPeriod
import com.example.dukanhisabnext.util.formatDate
import com.example.dukanhisabnext.util.formatTime12

// The decisions of the backup screens, as plain functions (PC-tested). The Bengali words come
// from strings.xml; they are passed in.

// ---- the last backup and the Home reminder ----

/** More than this long without a backup: the Home line shows. */
const val REMINDER_AFTER_MILLIS: Long = 7L * 24 * 60 * 60 * 1000

/**
 * The Home reminder: there is data worth saving (a sale, a customer or a product) and the last
 * backup is older than 7 days, or there never was one. EXACTLY 7 days is not "more than 7 days",
 * so no reminder yet. A last backup in the future (the phone's clock was changed) counts as recent.
 */
fun shouldRemindBackup(now: Long, lastBackupAt: Long?, hasData: Boolean): Boolean =
    hasData && (lastBackupAt == null || now - lastBackupAt > REMINDER_AFTER_MILLIS)

// ---- the one-time backup nudge (M9) ----

/** The nudge can appear once this many sales have been saved. */
const val NUDGE_AFTER_SALES: Long = 5

/** It is shown at most this many times in all (a "পরে" counts as one). */
const val MAX_NUDGE_SHOWS: Int = 2

/**
 * A one-time dialog on Home after the first 5 sales when no backup was EVER made: "ব্যাকআপ নিন" or
 * "পরে". Never again once "ব্যাকআপ নিন" was used ([nudgeUsed]), after it was shown twice
 * ([timesShown]), or when a backup exists. (The small Home line for a backup older than 7 days
 * is separate and stays.)
 */
fun shouldShowBackupNudge(salesSaved: Long, lastBackupAt: Long?, nudgeUsed: Boolean, timesShown: Int): Boolean =
    salesSaved >= NUDGE_AFTER_SALES && lastBackupAt == null && !nudgeUsed && timesShown < MAX_NUDGE_SHOWS

/** At least one sale, customer or product exists (counts of those tables). */
fun hasShopData(sales: Long, customers: Long, products: Long): Boolean = sales > 0 || customers > 0 || products > 0

/** "১২/১১/২০২৬ দুপুর ১২:০৯": the Dhaka date, the part of the day and the 12-hour time, Bengali digits. */
fun backupDateTimeText(epochMillis: Long, periodWord: (DayPeriod) -> String): String =
    "${formatDate(epochMillis)} ${periodWord(dayPeriod(epochMillis))} ${formatTime12(epochMillis)}"

/** The Settings row's second line: the last backup time, or "no backup yet". [labelFormat] puts the time into "শেষ ব্যাকআপ: %s". */
fun lastBackupText(
    lastBackupAt: Long?,
    neverText: String,
    labelFormat: (String) -> String,
    periodWord: (DayPeriod) -> String
): String = if (lastBackupAt == null) neverText else labelFormat(backupDateTimeText(lastBackupAt, periodWord))

/** The name offered in the "save as" picker (Latin digits, Dhaka time). */
fun saveFileName(now: Long): String = backupFileName(now)

// ---- what the Backup screen shows for each job state ----

data class BackupScreenUi(
    /** The three action buttons can be tapped (a second start while a job runs is refused too). */
    val buttonsEnabled: Boolean,
    /** A progress line with the current step. */
    val showProgress: Boolean,
    /** The swap runs: a full-screen "রিস্টোর হচ্ছে…" covers the whole app and Back does nothing. */
    val blocksApp: Boolean,
    /** The "আগের ডেটা ফিরিয়ে আনুন" button is shown (only while a safety copy exists). */
    val showUndo: Boolean,
    /** A green "saved" message. */
    val showDone: Boolean,
    /** The refusal message to show, or null. */
    val failure: BackupProblem?,
    /** The restore summary dialog is open. */
    val showSummary: Boolean
)

private fun isSwap(step: JobStep) = step == JobStep.RESTORING || step == JobStep.UNDOING

/** The full-screen block: during the swap and the restart after it. */
fun blocksApp(state: BackupJobState): Boolean = when (state) {
    is BackupJobState.Working -> isSwap(state.step)
    BackupJobState.Restarting -> true
    else -> false
}

/** A new job may start only when nothing runs and nothing waits for an answer. */
fun canStartJob(state: BackupJobState): Boolean =
    state is BackupJobState.Idle || state is BackupJobState.Done || state is BackupJobState.Failed

fun backupScreenUi(state: BackupJobState, hasSafetyCopy: Boolean): BackupScreenUi = BackupScreenUi(
    buttonsEnabled = canStartJob(state),
    showProgress = state is BackupJobState.Working,
    blocksApp = blocksApp(state),
    showUndo = hasSafetyCopy,
    showDone = state is BackupJobState.Done,
    failure = (state as? BackupJobState.Failed)?.problem,
    showSummary = state is BackupJobState.AwaitingConfirm
)

// ---- the restore summary ----

data class SummaryRow(val table: String, val inBackup: Long, val now: Long)

/** One row per [SUMMARY_TABLES] table; a table the backup does not have counts 0. */
fun summaryRows(summary: RestoreSummary): List<SummaryRow> = SUMMARY_TABLES.map { table ->
    SummaryRow(table, summary.backupCounts[table] ?: 0, summary.currentCounts[table] ?: 0)
}

// ---- the note shown once after a restart ----

sealed interface StartupNote {
    /** A restore (or an undo) finished; [counts] are the restored rows. [backupCreatedAt] is null for an undo. */
    data class Restored(val backupCreatedAt: Long?, val isUndo: Boolean, val counts: Map<String, Long>) : StartupNote

    /** The last restore did not finish (a crash, the battery): the engine put things right at start-up. */
    data class Recovery(val outcome: RecoveryOutcome) : StartupNote

    /** A restore failed in its last step and the app was restarted: the old data is back. */
    data object Failed : StartupNote
}

/**
 * What to tell the user at start-up: the result file the engine wrote (key names of
 * BackupEngine's result file), or its start-up check's finding, or that a restore failed.
 */
fun startupNote(result: Map<String, String>?, recovery: RecoveryOutcome, restoreFailed: Boolean): StartupNote? = when {
    result != null -> StartupNote.Restored(
        backupCreatedAt = result["backupCreatedAt"]?.toLongOrNull(),
        isUndo = result["undo"] == "true",
        counts = result.filterKeys { it.startsWith("count.") }
            .mapKeys { it.key.removePrefix("count.") }
            .mapNotNull { (k, v) -> v.toLongOrNull()?.let { k to it } }.toMap()
    )
    recovery != RecoveryOutcome.NOTHING -> StartupNote.Recovery(recovery)
    restoreFailed -> StartupNote.Failed
    else -> null
}

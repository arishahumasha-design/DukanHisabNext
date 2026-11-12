package com.example.dukanhisabnext.backup

import java.io.File

// The state of the one backup or restore job, as the screens see it. Plain Kotlin (no Android),
// so the decisions made from it can be unit-tested on the PC. BackupManager owns the state.

/** What the job is doing right now (each has its own Bengali line in strings.xml). */
enum class JobStep {
    MAKING_BACKUP,   // making the zip
    WRITING_FILE,    // copying it to the place the user chose
    PREPARING_SHARE, // putting it in the share folder
    COPYING_FILE,    // copying the picked file into the app's work folder
    CHECKING,        // the restore checks
    RESTORING,       // the swap (the app cannot be used)
    UNDO_CHECKING,   // the checks of the safety copy
    UNDOING          // the swap back (the app cannot be used)
}

/** The tables the restore summary shows, in this order (the table names of the database). */
val SUMMARY_TABLES: List<String> = listOf("sales", "customers", "products", "expenses", "ledger_entries")

/** The numbers shown before the user agrees to a restore. [backupCreatedAt] is null for an undo. */
data class RestoreSummary(
    val backupCreatedAt: Long?,
    val backupCounts: Map<String, Long>,
    val currentCounts: Map<String, Long>
)

sealed interface BackupJobState {
    /** Nothing is running. */
    data object Idle : BackupJobState

    data class Working(val step: JobStep) : BackupJobState

    /** A backup passed every check; it waits for the user's "yes" (nothing has changed yet). */
    data class AwaitingConfirm(val summary: RestoreSummary) : BackupJobState

    /** The zip is in the share folder; the screen opens the share sheet once. */
    data class ShareReady(val file: File) : BackupJobState

    /** The backup file was really written to the place the user chose. */
    data object Done : BackupJobState

    /** The job stopped. Nothing of the live data changed. */
    data class Failed(val problem: BackupProblem) : BackupJobState

    /** The swap is done; the app is restarting. */
    data object Restarting : BackupJobState
}

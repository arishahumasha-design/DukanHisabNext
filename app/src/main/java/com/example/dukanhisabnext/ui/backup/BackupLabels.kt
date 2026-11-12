package com.example.dukanhisabnext.ui.backup

import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.backup.BackupProblem
import com.example.dukanhisabnext.backup.RecoveryOutcome

// The Bengali words of backup and restore (all from strings.xml).
// Every refusal ends with "আপনার ডেটা বদলায়নি": a refused restore never touches the live data.

fun BackupProblem.messageRes(): Int = when (this) {
    BackupProblem.NO_SPACE -> R.string.err_backup_no_space
    BackupProblem.FILE_TOO_LARGE -> R.string.err_backup_file_too_large
    BackupProblem.CANNOT_READ -> R.string.err_backup_cannot_read
    BackupProblem.NOT_A_ZIP -> R.string.err_backup_not_a_zip
    BackupProblem.UNSAFE_ZIP -> R.string.err_backup_unsafe_zip
    BackupProblem.MANIFEST_MISSING -> R.string.err_backup_manifest_missing
    BackupProblem.MANIFEST_BAD -> R.string.err_backup_manifest_bad
    BackupProblem.NOT_DOKAN_BACKUP -> R.string.err_backup_not_dokan
    BackupProblem.FORMAT_TOO_NEW -> R.string.err_backup_format_too_new
    BackupProblem.SCHEMA_TOO_NEW -> R.string.err_backup_schema_too_new
    BackupProblem.HASH_MISMATCH -> R.string.err_backup_hash_mismatch
    BackupProblem.DB_DAMAGED -> R.string.err_backup_db_damaged
    BackupProblem.NOT_THIS_APP -> R.string.err_backup_not_this_app
    BackupProblem.MIGRATION_FAILED -> R.string.err_backup_migration_failed
    BackupProblem.COUNTS_MISMATCH -> R.string.err_backup_counts_mismatch
    BackupProblem.UNEXPECTED -> R.string.err_backup_unexpected
}

/** The one-time note after the start-up check, or null when there is nothing to say. */
fun RecoveryOutcome.messageRes(): Int? = when (this) {
    RecoveryOutcome.NOTHING -> null
    RecoveryOutcome.NOT_FINISHED_NOTHING_CHANGED -> R.string.backup_recovered_not_finished
    RecoveryOutcome.FINISHED -> R.string.backup_recovered_finished
}

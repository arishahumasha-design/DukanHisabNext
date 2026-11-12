package com.example.dukanhisabnext.backup

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The small settings file (backup_prefs.xml, outside the database so a restore keeps it):
 * when the last backup was really written, and a one-time "the restore failed" note.
 * Android's automatic backup includes this file (see backup_rules.xml).
 */
class BackupPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE)

    private val last = MutableStateFlow(readLast())

    /** UTC millis of the last backup that was written to the place the user chose, or null. Watched by Settings and Home. */
    val lastBackupAt: StateFlow<Long?> = last

    private fun readLast(): Long? = if (prefs.contains(KEY_LAST)) prefs.getLong(KEY_LAST, 0) else null

    /** Call only AFTER the file was written and checked. */
    fun markBackedUp(at: Long) {
        prefs.edit().putLong(KEY_LAST, at).commit() // commit: written before we say "done"
        last.value = at
    }

    /** How many times the one-time "ব্যাকআপ নিন" dialog was shown (it stops after 2). */
    var nudgeShownCount: Int
        get() = prefs.getInt(KEY_NUDGE_SHOWN, 0)
        set(value) {
            prefs.edit().putInt(KEY_NUDGE_SHOWN, value).commit()
        }

    /** True once the user tapped "ব্যাকআপ নিন" in that dialog: it never comes back. */
    var nudgeUsed: Boolean
        get() = prefs.getBoolean(KEY_NUDGE_USED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_NUDGE_USED, value).commit()
        }

    var restoreFailedNote: Boolean
        get() = prefs.getBoolean(KEY_FAILED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FAILED, value).commit()
        }

    private companion object {
        const val KEY_LAST = "lastBackupAt"
        const val KEY_FAILED = "restoreFailedNote"
        const val KEY_NUDGE_SHOWN = "nudgeShownCount"
        const val KEY_NUDGE_USED = "nudgeUsed"
    }
}

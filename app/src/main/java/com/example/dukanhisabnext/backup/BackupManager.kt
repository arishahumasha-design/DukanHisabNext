package com.example.dukanhisabnext.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the backup and restore jobs for the screens, one at a time, in a scope of its own (the app's),
 * so rotating the phone or pressing Back never cancels a job. The screens only watch [state].
 * The checks and the swap are the BackupEngine's; this class only moves files between the user's
 * chosen place and the engine, and keeps the state.
 *
 * Who does what:
 *  - save:    engine.createBackup -> copy to the chosen file -> read it back -> remember the time
 *  - share:   engine.createBackup -> copy into the cache share folder -> ShareReady
 *  - restore: copy the picked file in -> engine.prepareRestore -> AwaitingConfirm -> engine.restore -> restart
 *  - undo:    engine.prepareUndo -> engine.restore -> restart
 */
class BackupManager(
    private val context: Context,
    private val engine: BackupEngine,
    private val prefs: BackupPrefs,
    private val countRows: suspend () -> Map<String, Long>,
    private val isLiveOpen: () -> Boolean,
    private val restartApp: () -> Unit,
    startup: StartupNote?,
    /** Called after a restore or an undo replaced the data, just before the restart (the cart is emptied). */
    private val onDataReplaced: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _state = MutableStateFlow<BackupJobState>(BackupJobState.Idle)
    val state: StateFlow<BackupJobState> = _state

    private val _hasSafetyCopy = MutableStateFlow(engine.hasSafetyCopy())
    /** True while the data from before the last restore is kept (the undo button). */
    val hasSafetyCopy: StateFlow<Boolean> = _hasSafetyCopy

    private val _startupNote = MutableStateFlow(startup)
    /** The result of the last restore, shown once after the restart. */
    val startupNote: StateFlow<StartupNote?> = _startupNote

    val lastBackupAt: StateFlow<Long?> get() = prefs.lastBackupAt

    private val shareDir = File(context.cacheDir, SHARE_FOLDER)

    /** The restore that passed every check and waits for the user's yes. */
    private var pending: PreparedRestore? = null

    init {
        shareDir.deleteRecursively() // files shared by an earlier run
    }

    // ---- state helpers ----

    /** Moves from a startable state to Working, or refuses (a second tap, two screens). */
    private fun tryBegin(step: JobStep): Boolean {
        while (true) {
            val current = _state.value
            if (!canStartJob(current)) return false
            if (_state.compareAndSet(current, BackupJobState.Working(step))) return true
        }
    }

    private fun finish(next: BackupJobState) {
        _hasSafetyCopy.value = engine.hasSafetyCopy()
        _state.value = next
    }

    /** The user read the "saved" or the refusal message. */
    fun dismiss() {
        val current = _state.value
        if (current is BackupJobState.Done || current is BackupJobState.Failed) _state.value = BackupJobState.Idle
    }

    private fun cleanEngineOutput() {
        File(engine.paths.workDir, "out").deleteRecursively()
    }

    // ---- save ----

    /** Makes a backup and writes it to [target] (the file the "save as" picker made). */
    fun startSave(target: Uri): Boolean {
        if (!tryBegin(JobStep.MAKING_BACKUP)) return false
        scope.launch {
            try {
                when (val made = engine.createBackup()) {
                    is CreateResult.Failed -> finish(BackupJobState.Failed(made.problem))
                    is CreateResult.Created -> {
                        _state.value = BackupJobState.Working(JobStep.WRITING_FILE)
                        val written = withContext(Dispatchers.IO) { writeAndVerify(made.zip, target) }
                        if (written) {
                            prefs.markBackedUp(clock()) // only now: the file is really there
                            finish(BackupJobState.Done)
                        } else {
                            finish(BackupJobState.Failed(BackupProblem.UNEXPECTED))
                        }
                    }
                }
            } catch (e: Exception) {
                finish(BackupJobState.Failed(BackupProblem.UNEXPECTED))
            } finally {
                cleanEngineOutput()
            }
        }
        return true
    }

    /** Copies [zip] into the chosen file, then reads that file back and compares it with the original. */
    private fun writeAndVerify(zip: File, target: Uri): Boolean {
        return try {
            val resolver = context.contentResolver
            val out = resolver.openOutputStream(target, "wt") ?: return false
            out.use { stream ->
                FileInputStream(zip).use { it.copyTo(stream) }
                stream.flush()
            }
            val expected = FileInputStream(zip).use(::sha256Hex)
            val actual = resolver.openInputStream(target)?.use(::sha256Hex)
            expected == actual
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    // ---- share ----

    /** Makes a backup and puts it in the cache share folder; the screen then opens the share sheet. */
    fun startShare(): Boolean {
        if (!tryBegin(JobStep.MAKING_BACKUP)) return false
        scope.launch {
            try {
                when (val made = engine.createBackup()) {
                    is CreateResult.Failed -> finish(BackupJobState.Failed(made.problem))
                    is CreateResult.Created -> {
                        _state.value = BackupJobState.Working(JobStep.PREPARING_SHARE)
                        val copy = withContext(Dispatchers.IO) {
                            shareDir.deleteRecursively()
                            shareDir.mkdirs()
                            made.zip.copyTo(File(shareDir, made.zip.name), overwrite = true)
                        }
                        finish(BackupJobState.ShareReady(copy))
                    }
                }
            } catch (e: Exception) {
                finish(BackupJobState.Failed(BackupProblem.UNEXPECTED))
            } finally {
                cleanEngineOutput()
            }
        }
        return true
    }

    /** The share sheet was opened. (Sharing does not set the "last backup" time: we cannot know it arrived.) */
    fun shareHandled() {
        val current = _state.value
        if (current is BackupJobState.ShareReady) _state.compareAndSet(current, BackupJobState.Idle)
    }

    // ---- restore ----

    /** Copies the picked file in and runs every check. On success it waits for [confirmRestore]. */
    fun startRestore(source: Uri): Boolean {
        if (!tryBegin(JobStep.COPYING_FILE)) return false
        scope.launch {
            try {
                val resolver = context.contentResolver
                val declared = declaredSize(source)
                val input = try {
                    resolver.openInputStream(source)
                } catch (e: Exception) {
                    null
                }
                if (input == null) {
                    finish(BackupJobState.Failed(BackupProblem.CANNOT_READ))
                    return@launch
                }
                val (file, problem) = engine.copyIncoming(input, declared)
                if (file == null) {
                    finish(BackupJobState.Failed(problem ?: BackupProblem.CANNOT_READ))
                    return@launch
                }
                _state.value = BackupJobState.Working(JobStep.CHECKING)
                when (val prepared = engine.prepareRestore(file)) {
                    is PrepareResult.Refused -> finish(BackupJobState.Failed(prepared.problem))
                    is PrepareResult.Ready -> {
                        pending = prepared.restore
                        val summary = RestoreSummary(
                            backupCreatedAt = prepared.restore.manifest?.createdAt,
                            backupCounts = prepared.restore.counts,
                            currentCounts = countRows()
                        )
                        finish(BackupJobState.AwaitingConfirm(summary))
                    }
                }
            } catch (e: Exception) {
                finish(BackupJobState.Failed(BackupProblem.UNEXPECTED))
            } finally {
                File(engine.paths.workDir, "incoming").deleteRecursively()
            }
        }
        return true
    }

    private fun declaredSize(uri: Uri): Long? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    } catch (e: Exception) {
        null
    }

    /** The user said "no" (or left): the prepared copy is thrown away, nothing changed. */
    fun cancelRestore() {
        val current = _state.value as? BackupJobState.AwaitingConfirm ?: return
        if (!_state.compareAndSet(current, BackupJobState.Idle)) return
        pending = null
        discardPrepared()
    }

    private fun discardPrepared() {
        val prepared = engine.paths.prepared
        for (suffix in listOf("", "-wal", "-shm", "-journal")) File(prepared.path + suffix).delete()
    }

    /** The user said "yes": the swap. On success the app restarts by itself. */
    fun confirmRestore() {
        val current = _state.value as? BackupJobState.AwaitingConfirm ?: return
        val prepared = pending ?: return
        if (!_state.compareAndSet(current, BackupJobState.Working(JobStep.RESTORING))) return
        pending = null
        scope.launch { swap(prepared) }
    }

    // ---- undo ----

    /** "আগের ডেটা ফিরিয়ে আনুন": the safety copy goes through the same checks and the same swap. */
    fun startUndo(): Boolean {
        if (!tryBegin(JobStep.UNDO_CHECKING)) return false
        scope.launch {
            try {
                when (val prepared = engine.prepareUndo()) {
                    is PrepareResult.Refused -> finish(BackupJobState.Failed(prepared.problem))
                    is PrepareResult.Ready -> {
                        _state.value = BackupJobState.Working(JobStep.UNDOING)
                        swap(prepared.restore)
                    }
                }
            } catch (e: Exception) {
                finish(BackupJobState.Failed(BackupProblem.UNEXPECTED))
            }
        }
        return true
    }

    /** The swap itself, then the restart. If it fails but the database was already closed, the app restarts too. */
    private suspend fun swap(prepared: PreparedRestore) {
        when (val result = engine.restore(prepared)) {
            is RestoreResult.Done -> {
                _state.value = BackupJobState.Restarting
                onDataReplaced()
                delay(RESTART_DELAY_MS) // let the "রিস্টোর হচ্ছে…" screen show
                restartApp()
            }
            is RestoreResult.Failed -> {
                if (isLiveOpen()) {
                    finish(BackupJobState.Failed(result.problem))
                } else {
                    // The engine put the old files back, but this process still holds the closed database.
                    prefs.restoreFailedNote = true
                    _state.value = BackupJobState.Restarting
                    delay(RESTART_DELAY_MS)
                    restartApp()
                }
            }
        }
    }

    // ---- the note after a restart ----

    /** The user read the result: it is not shown again. */
    fun dismissStartupNote() {
        _startupNote.value = null
        engine.clearRestoreResult()
        prefs.restoreFailedNote = false
    }

    // ---- small questions ----

    /** The rows of the tables the restore summary shows, for "now". */
    suspend fun currentCounts(): Map<String, Long> = countRows()

    private companion object {
        const val SHARE_FOLDER = "shared-backups"
        const val RESTART_DELAY_MS = 600L
    }
}

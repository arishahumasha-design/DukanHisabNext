package com.example.dukanhisabnext.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.withTransaction
import com.example.dukanhisabnext.data.ALL_MIGRATIONS
import com.example.dukanhisabnext.data.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The files the engine works with. Everything except the live database lives under [workRoot]. */
class BackupPaths(val liveDb: File, val workRoot: File) {
    val liveWal = File(liveDb.path + "-wal")
    val liveShm = File(liveDb.path + "-shm")
    val liveJournal = File(liveDb.path + "-journal")
    /** The live database file and its helper files, in the order they are moved away (helpers first). */
    val liveFiles: List<File> get() = listOf(liveWal, liveShm, liveJournal, liveDb)

    /** Temporary work (excluded from Android's automatic backup). Emptied after each job. */
    val workDir = File(workRoot, "backup-work")
    /** The one safety copy of the data from before the last restore (excluded from Android's backup). */
    val safetyDir = File(workRoot, "restore-safety")
    val marker = File(workRoot, "restore.state")
    val resultFile = File(workRoot, "restore.result")
    /** The checked, up-to-date database waiting to be swapped in. Next to the live one, so the swap is a rename. */
    val prepared = File(liveDb.parentFile, liveDb.name + ".restore-prepared")

    fun safetyFileFor(live: File) = File(safetyDir, live.name)
}

/** The steps of the engine, for crash tests (a test hook can stop the work right after any of them). */
enum class BackupStep {
    // creating a backup
    SNAPSHOT_COPIED, ZIP_WRITTEN,
    // the swap of a restore
    OLD_SAFETY_CLEARED, MARKER_PREPARED, LIVE_CLOSED, LIVE_WAL_MOVED, LIVE_DB_MOVED, MARKER_SAFETY,
    PREPARED_MOVED_IN, MARKER_SWAPPED, CLEANED_UP
}

sealed interface CreateResult {
    data class Created(val zip: File, val manifest: BackupManifest) : CreateResult
    data class Failed(val problem: BackupProblem) : CreateResult
}

/** A restore that passed every check and waits for the user's "yes". */
data class PreparedRestore(
    val manifest: BackupManifest?,      // null when it comes from the safety copy (undo)
    val fromSchemaVersion: Int,
    /** Rows of every table after bringing it up to date (what the app will show). */
    val counts: Map<String, Long>
)

sealed interface PrepareResult {
    data class Ready(val restore: PreparedRestore) : PrepareResult
    data class Refused(val problem: BackupProblem) : PrepareResult
}

sealed interface RestoreResult {
    data class Done(val counts: Map<String, Long>) : RestoreResult
    data class Failed(val problem: BackupProblem) : RestoreResult
}

/** What the start-up check found. */
enum class RecoveryOutcome { NOTHING, NOT_FINISHED_NOTHING_CHANGED, FINISHED }

/**
 * Makes backups and restores them. The rules are in BackupFormat.kt; this class does the file work.
 *
 *  - A backup copies the live database while holding Room's write lock (so no sale can be half in
 *    it), then turns the copy into one clean file, checks it, and zips it with a manifest.
 *  - A restore runs every check on private copies, never on the live database. Only after the
 *    user agrees, [restore] swaps the files using renames and a marker file, so a crash at any
 *    moment is repaired at the next start by [recover]: either the old data or the new data, never
 *    a mix.
 *
 * One job at a time ([lock]). [fault] is only for crash tests.
 */
class BackupEngine(
    private val context: Context,
    private val getLive: () -> AppDatabase,
    private val closeLive: () -> Unit,
    private val appVersionCode: Long,
    private val appVersionName: String,
    liveDbName: String = AppDatabase.FILE_NAME,
    workRoot: File = context.filesDir,
    private val clock: () -> Long = System::currentTimeMillis,
    private val limits: BackupLimits = BackupLimits(),
    private val usableSpace: (File) -> Long = { it.usableSpace },
    private val fault: (BackupStep) -> Unit = {}
) {
    val paths = BackupPaths(context.getDatabasePath(liveDbName), workRoot)
    private val lock = Mutex()

    /** True while a job runs (the screen disables its buttons; a second start is refused). */
    val isBusy: Boolean get() = lock.isLocked

    fun hasSafetyCopy(): Boolean = paths.safetyFileFor(paths.liveDb).exists()

    // =====================================================================================
    // Create
    // =====================================================================================

    /** Makes a backup zip in the work folder. The caller copies it to the place the user picked. */
    suspend fun createBackup(): CreateResult {
        if (!lock.tryLock()) return CreateResult.Failed(BackupProblem.UNEXPECTED)
        try {
            val dir = File(paths.workDir, "create").also { it.deleteRecursively(); it.mkdirs() }
            val outDir = File(paths.workDir, "out").also { it.deleteRecursively(); it.mkdirs() }
            val liveSize = paths.liveDb.length() + paths.liveWal.length()
            if (usableSpace(paths.workRoot) < liveSize * 3 + MARGIN) return CreateResult.Failed(BackupProblem.NO_SPACE)

            // 1. Copy the database AND its -wal while holding the write lock: no one can write now,
            //    so the two files match exactly. A sale being saved either finished before (it is in
            //    the copy) or waits a moment and is saved right after (it is not in the copy).
            val snapshot = File(dir, "snapshot.db")
            val live = getLive()
            live.withTransaction {
                live.openHelper.writableDatabase.query("SELECT COUNT(*) FROM room_master_table").use { it.moveToFirst() }
                withContext(Dispatchers.IO) {
                    paths.liveDb.copyTo(snapshot, overwrite = true)
                    if (paths.liveWal.exists()) paths.liveWal.copyTo(File(snapshot.path + "-wal"), overwrite = true)
                }
            }
            fault(BackupStep.SNAPSHOT_COPIED)

            return withContext(Dispatchers.IO) {
                // 2. One clean file: fold the -wal in, then check it like a restore would.
                makeSingleFile(snapshot)
                val facts = inspectDatabase(snapshot) ?: return@withContext CreateResult.Failed(BackupProblem.DB_DAMAGED)
                if (facts.problem != null) return@withContext CreateResult.Failed(facts.problem)

                // 3. The manifest and the zip.
                val now = clock()
                val manifest = BackupManifest(
                    dbSchemaVersion = facts.userVersion,
                    appVersionCode = appVersionCode,
                    appVersionName = appVersionName,
                    createdAt = now,
                    dbSize = snapshot.length(),
                    dbSha256 = FileInputStream(snapshot).use(::sha256Hex),
                    counts = facts.counts
                )
                val zip = File(outDir, backupFileName(now))
                ZipOutputStream(FileOutputStream(zip).buffered()).use { out ->
                    out.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                    out.write(writeManifest(manifest).toByteArray(Charsets.UTF_8))
                    out.closeEntry()
                    out.putNextEntry(ZipEntry(DATABASE_ENTRY))
                    FileInputStream(snapshot).use { it.copyTo(out) }
                    out.closeEntry()
                }
                fault(BackupStep.ZIP_WRITTEN)

                // 4. Read it back before handing it out: the entries, the manifest and the hash.
                val check = readAndVerifyZip(zip, File(dir, "verify.db"))
                if (check is ZipCheck.Bad) return@withContext CreateResult.Failed(BackupProblem.UNEXPECTED)
                CreateResult.Created(zip, manifest)
            }
        } catch (e: IOException) {
            return CreateResult.Failed(BackupProblem.UNEXPECTED)
        } catch (e: SQLiteException) {
            return CreateResult.Failed(BackupProblem.DB_DAMAGED)
        } catch (e: Exception) {
            return CreateResult.Failed(BackupProblem.UNEXPECTED)
        } finally {
            File(paths.workDir, "create").deleteRecursively()
            lock.unlock()
        }
    }

    // =====================================================================================
    // Check and prepare (never touches the live database)
    // =====================================================================================

    /**
     * Copies the picked file into the work folder, stopping at the size limit. [declaredLength] is
     * what the system says the file size is (null when unknown).
     */
    suspend fun copyIncoming(source: InputStream, declaredLength: Long?): Pair<File?, BackupProblem?> = withContext(Dispatchers.IO) {
        try {
            if (declaredLength != null && declaredLength > limits.maxBackupBytes) return@withContext null to BackupProblem.FILE_TOO_LARGE
            val needed = (declaredLength ?: limits.maxBackupBytes) + MARGIN
            if (usableSpace(paths.workRoot) < needed) return@withContext null to BackupProblem.NO_SPACE
            val dir = File(paths.workDir, "incoming").also { it.deleteRecursively(); it.mkdirs() }
            val file = File(dir, "backup.zip")
            val copied = source.use { input -> FileOutputStream(file).use { copyCapped(input, it, limits.maxBackupBytes) } }
            if (copied < 0) {
                file.delete()
                null to BackupProblem.FILE_TOO_LARGE
            } else {
                file to null
            }
        } catch (e: IOException) {
            null to BackupProblem.CANNOT_READ
        }
    }

    /**
     * Every check, in order, on private copies. On success the up-to-date database waits at
     * [BackupPaths.prepared] for [restore]. On refusal nothing is left behind and nothing changed.
     */
    suspend fun prepareRestore(zip: File): PrepareResult {
        if (!lock.tryLock()) return PrepareResult.Refused(BackupProblem.UNEXPECTED)
        try {
            return withContext(Dispatchers.IO) {
                paths.prepared.delete()
                if (zip.length() > limits.maxBackupBytes) return@withContext refuse(BackupProblem.FILE_TOO_LARGE)
                val work = File(paths.workDir, "check").also { it.deleteRecursively(); it.mkdirs() }
                when (val read = readAndVerifyZip(zip, File(work, "incoming.db"))) {
                    is ZipCheck.Bad -> refuse(read.problem)
                    is ZipCheck.Good -> prepareDatabase(read.db, read.manifest)
                }
            }
        } catch (e: SQLiteException) {
            return refuse(BackupProblem.DB_DAMAGED)
        } catch (e: Exception) {
            // Anything else (a full disk half-way, a strange file): refused, the live data is untouched.
            return refuse(BackupProblem.UNEXPECTED)
        } finally {
            File(paths.workDir, "check").deleteRecursively()
            lock.unlock()
        }
    }

    /** "আগের ডেটা ফিরিয়ে আনুন": the safety copy goes through the same checks, then the same swap. */
    suspend fun prepareUndo(): PrepareResult {
        if (!lock.tryLock()) return PrepareResult.Refused(BackupProblem.UNEXPECTED)
        try {
            return withContext(Dispatchers.IO) {
                paths.prepared.delete()
                val safetyDb = paths.safetyFileFor(paths.liveDb)
                if (!safetyDb.exists()) return@withContext refuse(BackupProblem.CANNOT_READ)
                val work = File(paths.workDir, "check").also { it.deleteRecursively(); it.mkdirs() }
                val copy = File(work, "incoming.db")
                safetyDb.copyTo(copy, overwrite = true)
                val wal = paths.safetyFileFor(paths.liveWal)
                if (wal.exists()) wal.copyTo(File(copy.path + "-wal"), overwrite = true)
                makeSingleFile(copy)
                prepareDatabase(copy, manifest = null)
            }
        } catch (e: SQLiteException) {
            return refuse(BackupProblem.DB_DAMAGED)
        } catch (e: Exception) {
            return refuse(BackupProblem.UNEXPECTED)
        } finally {
            File(paths.workDir, "check").deleteRecursively()
            lock.unlock()
        }
    }

    private fun refuse(problem: BackupProblem): PrepareResult {
        for (suffix in listOf("", "-wal", "-shm", "-journal")) File(paths.prepared.path + suffix).delete()
        return PrepareResult.Refused(problem)
    }

    private sealed interface ZipCheck {
        data class Good(val manifest: BackupManifest, val db: File) : ZipCheck
        data class Bad(val problem: BackupProblem) : ZipCheck
    }

    /** Checks 3 to 8: the zip, its entries, the manifest, the versions, and the database's size and hash. */
    private fun readAndVerifyZip(zip: File, dbOut: File): ZipCheck {
        val zipFile = try {
            ZipFile(zip)
        } catch (e: ZipException) {
            // Android itself refuses entry names that escape the folder ("zip path traversal").
            return ZipCheck.Bad(if (e.message.orEmpty().contains("path", ignoreCase = true)) BackupProblem.UNSAFE_ZIP else BackupProblem.NOT_A_ZIP)
        } catch (e: IOException) {
            return ZipCheck.Bad(BackupProblem.NOT_A_ZIP)
        }
        zipFile.use { zf ->
            val entries = try {
                zf.entries().toList()
            } catch (e: Exception) {
                return ZipCheck.Bad(BackupProblem.UNSAFE_ZIP)
            }
            checkEntries(entries.map { ZipEntryInfo(it.name, it.size, it.compressedSize) }, limits)?.let { return ZipCheck.Bad(it) }

            // The manifest (small, read with a cap).
            val manifestEntry = zf.getEntry(MANIFEST_ENTRY)
            val manifestBytes = try {
                zf.getInputStream(manifestEntry).use { readCapped(it, limits.maxManifestBytes) }
            } catch (e: IOException) {
                return ZipCheck.Bad(BackupProblem.MANIFEST_BAD)
            } ?: return ZipCheck.Bad(BackupProblem.MANIFEST_BAD)
            val manifest = when (val parsed = parseManifest(String(manifestBytes, Charsets.UTF_8))) {
                is ManifestResult.Bad -> return ZipCheck.Bad(parsed.problem)
                is ManifestResult.Ok -> parsed.manifest
            }
            checkSchemaVersion(manifest.dbSchemaVersion)?.let { return ZipCheck.Bad(it) }
            checkDbEntry(manifest)?.let { return ZipCheck.Bad(it) }
            if (manifest.dbSize > limits.maxEntryBytes) return ZipCheck.Bad(BackupProblem.FILE_TOO_LARGE)
            if (usableSpace(paths.workRoot) < manifest.dbSize * 3 + MARGIN) return ZipCheck.Bad(BackupProblem.NO_SPACE)

            // The database: unpacked with a cap (zip bomb), hashed while unpacking.
            val dbEntry = zf.getEntry(DATABASE_ENTRY)
            val digest = MessageDigest.getInstance("SHA-256")
            val written = try {
                zf.getInputStream(dbEntry).use { input ->
                    DigestOutputStream(FileOutputStream(dbOut), digest).use { copyCapped(input, it, limits.maxEntryBytes) }
                }
            } catch (e: IOException) {
                return ZipCheck.Bad(BackupProblem.HASH_MISMATCH)
            }
            if (written < 0) return ZipCheck.Bad(BackupProblem.FILE_TOO_LARGE)
            if (looksLikeBomb(written, dbEntry.compressedSize, limits)) return ZipCheck.Bad(BackupProblem.UNSAFE_ZIP)
            if (written != manifest.dbSize || toHex(digest.digest()) != manifest.dbSha256) return ZipCheck.Bad(BackupProblem.HASH_MISMATCH)
            return ZipCheck.Good(manifest, dbOut)
        }
    }

    /**
     * Checks 9 to 12 on a private copy: a working SQLite file of this app (header, version, integrity,
     * foreign keys, Room's identity hash), the row counts, then brought up to date by the normal
     * migrations with Room's full schema check. On success the file waits at [BackupPaths.prepared].
     */
    private fun prepareDatabase(db: File, manifest: BackupManifest?): PrepareResult {
        val facts = try {
            inspectDatabase(db)
        } catch (e: SQLiteException) {
            null
        } ?: return refuse(BackupProblem.DB_DAMAGED)
        facts.problem?.let { return refuse(it) }
        if (manifest != null) {
            if (facts.userVersion != manifest.dbSchemaVersion) return refuse(BackupProblem.DB_DAMAGED)
            if (facts.counts != manifest.counts) return refuse(BackupProblem.COUNTS_MISMATCH)
        }
        checkSchemaVersion(facts.userVersion)?.let { return refuse(it) }

        // Bring it up to date next to the live database (a plain name, so Room can open it).
        paths.prepared.delete()
        if (!db.renameTo(paths.prepared)) {
            db.copyTo(paths.prepared, overwrite = true)
            db.delete()
        }
        val countsAfter = try {
            val room = Room.databaseBuilder(context, AppDatabase::class.java, paths.prepared.name)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
            try {
                room.openHelper.writableDatabase // runs the migrations and Room's schema check
                facts.counts.keys.associateWith { table -> countRows(room.openHelper.readableDatabase, table) }
            } finally {
                room.close()
            }
        } catch (e: Exception) {
            return refuse(BackupProblem.MIGRATION_FAILED)
        }
        if (countsAfter != facts.counts) return refuse(BackupProblem.COUNTS_MISMATCH)

        makeSingleFile(paths.prepared)
        val after = try {
            inspectDatabase(paths.prepared)
        } catch (e: SQLiteException) {
            null
        }
        if (after == null || after.problem != null || after.userVersion != CURRENT_SCHEMA_VERSION) {
            return refuse(BackupProblem.MIGRATION_FAILED)
        }
        return PrepareResult.Ready(PreparedRestore(manifest, facts.userVersion, after.counts))
    }

    // =====================================================================================
    // The swap
    // =====================================================================================

    /**
     * Puts the prepared database in place of the live one. The live database is closed first; the
     * caller must restart the app afterwards. Every step is a rename and the marker says how far it
     * got, so a crash at any moment is repaired by [recover] at the next start.
     */
    suspend fun restore(prepared: PreparedRestore): RestoreResult {
        if (!lock.tryLock()) return RestoreResult.Failed(BackupProblem.UNEXPECTED)
        try {
            return withContext(Dispatchers.IO) {
                if (!paths.prepared.exists()) return@withContext RestoreResult.Failed(BackupProblem.UNEXPECTED)
                try {
                    swap(prepared)
                    RestoreResult.Done(prepared.counts)
                } catch (e: IOException) {
                    // A normal error (not a crash): undo at once, the same way the next start would.
                    recover(paths)
                    RestoreResult.Failed(BackupProblem.UNEXPECTED)
                }
            }
        } finally {
            lock.unlock()
        }
    }

    private fun swap(prepared: PreparedRestore) {
        // 0. The one safety copy is replaced: the old one goes before the marker is written.
        paths.safetyDir.deleteRecursively()
        if (!paths.safetyDir.mkdirs()) throw IOException("cannot make the safety folder")
        fault(BackupStep.OLD_SAFETY_CLEARED)

        writeMarker(paths, RestoreStage.PREPARED)
        fault(BackupStep.MARKER_PREPARED)

        closeLive()
        fault(BackupStep.LIVE_CLOSED)

        // 1. The live files go into the safety folder: the helper files first, the database last.
        for (live in paths.liveFiles) {
            if (live.exists() && !live.renameTo(paths.safetyFileFor(live))) throw IOException("cannot move ${live.name}")
            if (live == paths.liveWal) fault(BackupStep.LIVE_WAL_MOVED)
        }
        fault(BackupStep.LIVE_DB_MOVED)

        writeMarker(paths, RestoreStage.SAFETY)
        fault(BackupStep.MARKER_SAFETY)

        // 2. The prepared database takes the live name.
        if (!paths.prepared.renameTo(paths.liveDb)) throw IOException("cannot move the prepared database in")
        fault(BackupStep.PREPARED_MOVED_IN)

        writeMarker(paths, RestoreStage.SWAPPED)
        fault(BackupStep.MARKER_SWAPPED)

        // 3. Clean up and leave the result for the screen after the restart.
        writeResult(paths, prepared)
        paths.workDir.deleteRecursively()
        paths.marker.delete()
        fault(BackupStep.CLEANED_UP)
    }

    /** What the last restore brought back (shown once after the restart), or null. */
    fun readRestoreResult(): Map<String, String>? = readProperties(paths.resultFile)

    fun clearRestoreResult() {
        paths.resultFile.delete()
    }

    companion object {
        private const val MARGIN = 2L * 1024 * 1024

        /**
         * Run at every app start, before anything opens the database. Repairs a restore that was
         * stopped (a crash, the battery, the user killing the app) using the marker file:
         * before the swap finished -> the old data is put back; after -> the new data stays.
         */
        fun recover(paths: BackupPaths): RecoveryOutcome {
            if (!paths.marker.exists()) {
                // No restore was in its swap. Left-overs of a job that was stopped (a prepared file,
                // the work folder) are not in use at start-up, so they go.
                deletePrepared(paths)
                paths.workDir.deleteRecursively()
                return RecoveryOutcome.NOTHING
            }
            // An unreadable marker is treated as the earliest stage (the safest: roll back).
            val stage = parseStage(readProperties(paths.marker)?.get("stage")) ?: RestoreStage.PREPARED
            val safetyHasFiles = paths.liveFiles.any { paths.safetyFileFor(it).exists() }
            val outcome = when (recoveryAction(stage, safetyHasFiles)) {
                RecoveryAction.NOTHING -> RecoveryOutcome.NOTHING
                RecoveryAction.DISCARD_PREPARED -> RecoveryOutcome.NOT_FINISHED_NOTHING_CHANGED
                RecoveryAction.ROLL_BACK -> {
                    // Each file that was moved to safety goes back, replacing what is there now
                    // (the new database, if it was already moved in). Files never moved stay.
                    for (live in paths.liveFiles) {
                        val saved = paths.safetyFileFor(live)
                        if (saved.exists()) {
                            if (live.exists() && !live.delete()) throw IOException("cannot remove ${live.name}")
                            if (!saved.renameTo(live)) throw IOException("cannot put back ${live.name}")
                        }
                    }
                    // The new database may have been moved in without a -wal of the old one: its
                    // helper files would not match the old database, so they go too.
                    if (!paths.safetyFileFor(paths.liveShm).exists()) paths.liveShm.delete()
                    RecoveryOutcome.NOT_FINISHED_NOTHING_CHANGED
                }
                RecoveryAction.FINISH -> RecoveryOutcome.FINISHED
            }
            deletePrepared(paths)
            paths.workDir.deleteRecursively()
            paths.marker.delete()
            return outcome
        }

        private fun deletePrepared(paths: BackupPaths) {
            paths.prepared.delete()
            File(paths.prepared.path + "-wal").delete()
            File(paths.prepared.path + "-shm").delete()
            File(paths.prepared.path + "-journal").delete()
        }

        private fun writeMarker(paths: BackupPaths, stage: RestoreStage) {
            writeAtomically(paths.marker, "stage=${stage.name}\n")
        }

        private fun writeResult(paths: BackupPaths, prepared: PreparedRestore) {
            val text = buildString {
                append("fromSchemaVersion=").append(prepared.fromSchemaVersion).append('\n')
                prepared.manifest?.let { append("backupCreatedAt=").append(it.createdAt).append('\n') }
                append("undo=").append(prepared.manifest == null).append('\n')
                for ((table, rows) in prepared.counts.toSortedMap()) append("count.").append(table).append('=').append(rows).append('\n')
            }
            writeAtomically(paths.resultFile, text)
        }

        /** Writes to a temporary file, flushes it to the disk, then renames it (all or nothing). */
        private fun writeAtomically(file: File, text: String) {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            FileOutputStream(tmp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) throw IOException("cannot write ${file.name}")
            }
        }

        private fun readProperties(file: File): Map<String, String>? {
            if (!file.exists()) return null
            return try {
                file.readLines(Charsets.UTF_8).mapNotNull { line ->
                    val i = line.indexOf('=')
                    if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
                }.toMap()
            } catch (e: IOException) {
                null
            }
        }
    }
}

// =========================================================================================
// SQLite helpers (on private copies only)
// =========================================================================================

/** What a database file is: its version, Room's identity hash, its row counts, or the first problem. */
private data class DbFacts(val userVersion: Int, val counts: Map<String, Long>, val problem: BackupProblem?)

private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

/** Opens a copy without Android's default "delete a corrupt file" behaviour. */
private fun openCopy(file: File, writable: Boolean): SQLiteDatabase =
    SQLiteDatabase.openDatabase(
        file.path, null,
        if (writable) SQLiteDatabase.OPEN_READWRITE else SQLiteDatabase.OPEN_READONLY
    ) { /* a corrupt copy is reported, never deleted */ }

/** Folds the -wal into the file and switches it to a single file (no -wal, no -shm). */
private fun makeSingleFile(file: File) {
    openCopy(file, writable = true).use { db ->
        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        db.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
    }
    File(file.path + "-wal").delete()
    File(file.path + "-shm").delete()
}

/** Checks 9 and 10: header, opening, integrity, foreign keys, Room's identity hash; then the row counts. */
private fun inspectDatabase(file: File): DbFacts? {
    val header = ByteArray(16)
    val read = FileInputStream(file).use { it.read(header) }
    if (read != 16 || !header.contentEquals(SQLITE_HEADER)) return DbFacts(0, emptyMap(), BackupProblem.DB_DAMAGED)

    return try {
        openCopy(file, writable = false).use { db ->
            val integrity = db.rawQuery("PRAGMA integrity_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            if (integrity != "ok") return DbFacts(0, emptyMap(), BackupProblem.DB_DAMAGED)
            val fkProblems = db.rawQuery("PRAGMA foreign_key_check", null).use { it.count }
            if (fkProblems > 0) return DbFacts(0, emptyMap(), BackupProblem.DB_DAMAGED)
            val version = db.version

            val identity = try {
                db.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            } catch (e: SQLiteException) {
                null
            }
            if (identity == null || KNOWN_SCHEMA_HASHES[version] != identity) {
                // A newer database of this app would have an unknown hash too: say "too new" for that.
                val problem = if (version > CURRENT_SCHEMA_VERSION) BackupProblem.SCHEMA_TOO_NEW else BackupProblem.NOT_THIS_APP
                return DbFacts(version, emptyMap(), problem)
            }

            val tables = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                    "AND name NOT IN ('room_master_table', 'android_metadata') ORDER BY name",
                null
            ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            val counts = tables.associateWith { table ->
                db.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { c -> c.moveToFirst(); c.getLong(0) }
            }
            DbFacts(version, counts, null)
        }
    } catch (e: SQLiteException) {
        DbFacts(0, emptyMap(), BackupProblem.DB_DAMAGED)
    }
}

private fun countRows(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Long =
    db.query("SELECT COUNT(*) FROM `$table`").use { c -> c.moveToFirst(); c.getLong(0) }

/** Copies at most [max] bytes. Returns the number of bytes, or -1 when the source had more. */
private fun copyCapped(input: InputStream, output: java.io.OutputStream, max: Long): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buffer)
        if (n < 0) return total
        total += n
        if (total > max) return -1
        output.write(buffer, 0, n)
    }
}

/** Reads at most [max] bytes; null when there is more. */
private fun readCapped(input: InputStream, max: Long): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    return if (copyCapped(input, out, max) < 0) null else out.toByteArray()
}

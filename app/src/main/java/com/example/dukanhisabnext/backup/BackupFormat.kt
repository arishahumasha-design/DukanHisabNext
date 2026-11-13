package com.example.dukanhisabnext.backup

import com.example.dukanhisabnext.util.DhakaTime
import java.io.InputStream
import java.io.StringReader
import java.security.MessageDigest
import java.time.Instant
import java.util.Properties

// The backup file format and its rules, as plain Kotlin/Java (no Android) so they can be
// unit-tested on the PC. The full description for other platforms is docs/DATA_FORMAT.md.
//
// A backup is ONE zip file:
//   manifest.properties        UTF-8 "key=value" lines (written first)
//   database/dokan_hisab.db    the SQLite database, one clean file (no -wal)
// Format version 1 allows exactly these two entries; later versions may add more (for example
// "files/..." for product photos) with a higher formatVersion.

const val BACKUP_FORMAT_NAME = "dokanhisab-backup"
const val BACKUP_FORMAT_VERSION = 1
const val BACKUP_PLATFORM = "android"
const val MANIFEST_ENTRY = "manifest.properties"
const val DATABASE_ENTRY = "database/dokan_hisab.db"
const val ENCRYPTION_NONE = "none"

/** The database schema version this app writes (AppDatabase.version). */
const val CURRENT_SCHEMA_VERSION = 5

/**
 * Room's identity hash of every schema version this app can read (from app/schemas/.../N.json;
 * a PC test checks they match the files). A database whose hash is not here is not ours.
 */
val KNOWN_SCHEMA_HASHES: Map<Int, String> = mapOf(
    1 to "4a150112da92ffebcb69bef2389dc153",
    2 to "a0199054f2b523abb52d42a994cdb47d",
    3 to "30f220f802126321712bf3fb6261abb3",
    4 to "6ab4148f4da5d9eeecf6eecd00107348",
    5 to "f335e70a02a4cdd6a344a20a70b58427"
)

/** The small settings file that remembers the last backup time (outside the database, so a restore keeps it). */
const val BACKUP_PREFS_NAME = "backup_prefs"

/** The size limits that protect the phone from a broken or hostile file. Tests use smaller ones. */
data class BackupLimits(
    val maxBackupBytes: Long = 256L * 1024 * 1024,   // the picked zip file
    val maxEntryBytes: Long = 512L * 1024 * 1024,    // one unpacked entry (the database)
    val maxEntries: Int = 32,
    val maxManifestBytes: Long = 64L * 1024,
    /** Unpacked / packed. Above this (for entries bigger than [ratioFloorBytes]) it looks like a zip bomb. */
    val maxRatio: Long = 1_000,
    val ratioFloorBytes: Long = 10L * 1024 * 1024
)

/** Everything a restore can refuse. Each has its own Bengali message (strings.xml). */
enum class BackupProblem {
    NO_SPACE,          // not enough free space on the phone
    FILE_TOO_LARGE,    // the file or the database inside is bigger than the limits
    CANNOT_READ,       // the file could not be read at all
    NOT_A_ZIP,         // not a zip file, or cut off (truncated)
    UNSAFE_ZIP,        // a zip trick: path escape, unknown or duplicate entries, too many entries, zip bomb
    MANIFEST_MISSING,  // no manifest.properties inside
    MANIFEST_BAD,      // the manifest cannot be read or misses a required value
    NOT_DOKAN_BACKUP,  // a zip, but not a DokanHisab backup
    FORMAT_TOO_NEW,    // made with a newer backup format (or a feature this app does not have)
    SCHEMA_TOO_NEW,    // the database is newer than this app
    HASH_MISMATCH,     // the database inside does not match the manifest (damaged or changed)
    DB_DAMAGED,        // not a working SQLite database, or its integrity check fails
    NOT_THIS_APP,      // a SQLite database, but not this app's
    MIGRATION_FAILED,  // an older backup could not be brought up to date
    COUNTS_MISMATCH,   // the number of rows is not what the manifest says
    UNEXPECTED         // anything else; nothing was changed
}

// =========================================================================================
// The manifest
// =========================================================================================

data class BackupManifest(
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val platform: String = BACKUP_PLATFORM,
    val dbSchemaVersion: Int,
    val appVersionCode: Long,
    val appVersionName: String,
    /** When the backup was made (UTC epoch millis). */
    val createdAt: Long,
    val dbEntry: String = DATABASE_ENTRY,
    val dbSize: Long,
    /** SHA-256 of the database entry, 64 lowercase hex characters. */
    val dbSha256: String,
    val encryption: String = ENCRYPTION_NONE,
    /** Row count of every table of the database (name -> rows). */
    val counts: Map<String, Long>
)

private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")

/** The manifest as text: one "key=value" line each, in a fixed order, then the counts by table name. */
fun writeManifest(m: BackupManifest): String = buildString {
    fun line(key: String, value: Any) = append(key).append('=').append(escape(value.toString())).append('\n')
    line("format", BACKUP_FORMAT_NAME)
    line("formatVersion", m.formatVersion)
    line("platform", m.platform)
    line("dbSchemaVersion", m.dbSchemaVersion)
    line("appVersionCode", m.appVersionCode)
    line("appVersionName", m.appVersionName)
    line("createdAt", m.createdAt)
    line("dbEntry", m.dbEntry)
    line("dbSize", m.dbSize)
    line("dbSha256", m.dbSha256)
    line("encryption", m.encryption)
    for ((table, rows) in m.counts.toSortedMap()) line("count.$table", rows)
}

sealed interface ManifestResult {
    data class Ok(val manifest: BackupManifest) : ManifestResult
    data class Bad(val problem: BackupProblem) : ManifestResult
}

/** Reads a manifest. The order of the checks decides the message: not ours, then too new, then broken. */
fun parseManifest(text: String): ManifestResult {
    val p = try {
        Properties().apply { load(StringReader(text)) }
    } catch (e: IllegalArgumentException) {
        return ManifestResult.Bad(BackupProblem.MANIFEST_BAD)
    }
    fun bad() = ManifestResult.Bad(BackupProblem.MANIFEST_BAD)

    if (p.getProperty("format") != BACKUP_FORMAT_NAME) return ManifestResult.Bad(BackupProblem.NOT_DOKAN_BACKUP)
    val formatVersion = p.getProperty("formatVersion")?.toIntOrNull() ?: return bad()
    if (formatVersion > BACKUP_FORMAT_VERSION) return ManifestResult.Bad(BackupProblem.FORMAT_TOO_NEW)
    if (formatVersion < 1) return bad()
    val encryption = p.getProperty("encryption") ?: ENCRYPTION_NONE
    if (encryption != ENCRYPTION_NONE) return ManifestResult.Bad(BackupProblem.FORMAT_TOO_NEW) // a feature this app has not got

    val schema = p.getProperty("dbSchemaVersion")?.toIntOrNull() ?: return bad()
    val createdAt = p.getProperty("createdAt")?.toLongOrNull() ?: return bad()
    val dbEntry = p.getProperty("dbEntry") ?: return bad()
    val dbSize = p.getProperty("dbSize")?.toLongOrNull()?.takeIf { it >= 0 } ?: return bad()
    val sha = p.getProperty("dbSha256")?.takeIf(::isSha256Hex) ?: return bad()
    val counts = mutableMapOf<String, Long>()
    for (key in p.stringPropertyNames().filter { it.startsWith("count.") }) {
        counts[key.removePrefix("count.")] = p.getProperty(key).toLongOrNull()?.takeIf { it >= 0 } ?: return bad()
    }
    return ManifestResult.Ok(
        BackupManifest(
            formatVersion = formatVersion,
            platform = p.getProperty("platform") ?: "",
            dbSchemaVersion = schema,
            appVersionCode = p.getProperty("appVersionCode")?.toLongOrNull() ?: 0,
            appVersionName = p.getProperty("appVersionName") ?: "",
            createdAt = createdAt,
            dbEntry = dbEntry,
            dbSize = dbSize,
            dbSha256 = sha,
            encryption = encryption,
            counts = counts
        )
    )
}

/** The database version must be one this app knows; a newer one needs an app update. null = fine. */
fun checkSchemaVersion(version: Int): BackupProblem? = when {
    version > CURRENT_SCHEMA_VERSION -> BackupProblem.SCHEMA_TOO_NEW
    version !in KNOWN_SCHEMA_HASHES -> BackupProblem.MANIFEST_BAD
    else -> null
}

/** Format version 1 keeps its database at exactly one place. null = fine. */
fun checkDbEntry(manifest: BackupManifest): BackupProblem? =
    if (manifest.dbEntry == DATABASE_ENTRY) null else BackupProblem.MANIFEST_BAD

fun isSha256Hex(text: String): Boolean = text.length == 64 && text.all { it in '0'..'9' || it in 'a'..'f' }

// =========================================================================================
// Zip safety
// =========================================================================================

/** One entry of the zip as the central directory describes it. [size] is -1 when not written. */
data class ZipEntryInfo(val name: String, val size: Long, val compressedSize: Long)

/** The entries a format-version-1 backup may contain. */
val ALLOWED_ENTRIES: Set<String> = setOf(MANIFEST_ENTRY, DATABASE_ENTRY)

/**
 * A name that cannot escape the folder it is unpacked into: relative, no "..", no ".", no empty
 * part, no backslash, no drive letter or colon, no control characters, not too long.
 */
fun isSafeEntryName(name: String): Boolean {
    if (name.isEmpty() || name.length > 200) return false
    if (name.startsWith("/") || name.contains('\\') || name.contains(':')) return false
    if (name.any { it < ' ' }) return false
    return name.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
}

/** Every check of the entry list, before anything is unpacked. null = fine. */
fun checkEntries(entries: List<ZipEntryInfo>, limits: BackupLimits): BackupProblem? {
    if (entries.size > limits.maxEntries) return BackupProblem.UNSAFE_ZIP
    if (entries.any { !isSafeEntryName(it.name) }) return BackupProblem.UNSAFE_ZIP
    if (entries.map { it.name }.toSet().size != entries.size) return BackupProblem.UNSAFE_ZIP // duplicates
    if (entries.any { it.name !in ALLOWED_ENTRIES }) return BackupProblem.UNSAFE_ZIP
    val manifest = entries.firstOrNull { it.name == MANIFEST_ENTRY } ?: return BackupProblem.MANIFEST_MISSING
    val db = entries.firstOrNull { it.name == DATABASE_ENTRY } ?: return BackupProblem.NOT_DOKAN_BACKUP
    if (manifest.size > limits.maxManifestBytes) return BackupProblem.MANIFEST_BAD
    if (db.size > limits.maxEntryBytes) return BackupProblem.FILE_TOO_LARGE
    if (looksLikeBomb(db.size, db.compressedSize, limits)) return BackupProblem.UNSAFE_ZIP
    return null
}

/** An entry that unpacks to far more than it packs (checked again while unpacking, with the real numbers). */
fun looksLikeBomb(unpacked: Long, packed: Long, limits: BackupLimits): Boolean =
    unpacked > limits.ratioFloorBytes && packed > 0 && unpacked / packed > limits.maxRatio

// =========================================================================================
// Name, hash
// =========================================================================================

/** "DokanHisab-backup-2026-11-12-1209.zip": the Dhaka date and time, Latin digits (they sort everywhere). */
fun backupFileName(createdAt: Long): String {
    val t = Instant.ofEpochMilli(createdAt).atZone(DhakaTime.ZONE)
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "DokanHisab-backup-${t.year}-${two(t.monthValue)}-${two(t.dayOfMonth)}-${two(t.hour)}${two(t.minute)}.zip"
}

fun toHex(bytes: ByteArray): String = bytes.joinToString("") { ((it.toInt() and 0xff) + 0x100).toString(16).substring(1) }

/** SHA-256 of everything [input] gives, as lowercase hex. The caller closes [input]. */
fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        digest.update(buffer, 0, n)
    }
    return toHex(digest.digest())
}

// =========================================================================================
// The swap and its recovery
// =========================================================================================

/**
 * The marker file (restore.state) says how far a restore got:
 *  - PREPARED: the new database is ready next to the live one; the live one is untouched;
 *  - SAFETY: the live files were moved into the safety folder; the new one may or may not be in place;
 *  - SWAPPED: the new database is in place; only the clean-up is left.
 */
enum class RestoreStage { PREPARED, SAFETY, SWAPPED }

enum class RecoveryAction {
    NOTHING,          // no restore was running
    DISCARD_PREPARED, // nothing was moved yet: delete the prepared file (the old data never left)
    ROLL_BACK,        // put the safety copy back (the old data returns)
    FINISH            // the new data is in place: just clean up
}

/**
 * What to do at the next start after a restore that did not finish. [safetyHasFiles] = any live
 * file (db, -wal, -shm) was already moved into the safety folder by THIS restore (the old safety
 * copy is deleted before the marker is written, so anything there now belongs to this restore).
 */
fun recoveryAction(stage: RestoreStage?, safetyHasFiles: Boolean): RecoveryAction = when (stage) {
    null -> RecoveryAction.NOTHING
    RestoreStage.SWAPPED -> RecoveryAction.FINISH
    RestoreStage.PREPARED, RestoreStage.SAFETY -> if (safetyHasFiles) RecoveryAction.ROLL_BACK else RecoveryAction.DISCARD_PREPARED
}

fun parseStage(text: String?): RestoreStage? = text?.trim()?.let { t -> RestoreStage.entries.firstOrNull { it.name == t } }

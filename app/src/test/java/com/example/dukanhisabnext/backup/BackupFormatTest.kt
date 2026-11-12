package com.example.dukanhisabnext.backup

import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure rules of the backup file: the manifest, the versions, the zip checks, the name and the recovery table. */
class BackupFormatTest {

    private val schemaDir = File("schemas/com.example.dukanhisabnext.data.AppDatabase")

    private val sample = BackupManifest(
        dbSchemaVersion = 3,
        appVersionCode = 7,
        appVersionName = "1.0",
        createdAt = 1_762_927_740_000,
        dbSize = 123_456,
        dbSha256 = "a".repeat(64),
        counts = mapOf("sales" to 12, "products" to 40, "customers" to 0)
    )

    private fun ok(text: String): BackupManifest {
        val r = parseManifest(text)
        assertTrue("expected Ok but was $r", r is ManifestResult.Ok)
        return (r as ManifestResult.Ok).manifest
    }

    private fun problem(text: String): BackupProblem? = (parseManifest(text) as? ManifestResult.Bad)?.problem

    private fun withLine(key: String, value: String?): String =
        writeManifest(sample).lines().filter { it.isNotEmpty() && !it.startsWith("$key=") }
            .let { if (value == null) it else it + "$key=$value" }.joinToString("\n")

    // ---- the manifest ----

    @Test
    fun theManifestRoundTrips() {
        assertEquals(sample, ok(writeManifest(sample)))
    }

    @Test
    fun theManifestHasEveryKeyInAFixedOrderIncludingThePlatform() {
        val keys = writeManifest(sample).lines().filter { it.isNotEmpty() }.map { it.substringBefore('=') }
        assertEquals(
            listOf(
                "format", "formatVersion", "platform", "dbSchemaVersion", "appVersionCode", "appVersionName",
                "createdAt", "dbEntry", "dbSize", "dbSha256", "encryption",
                "count.customers", "count.products", "count.sales"
            ),
            keys
        )
        assertTrue(writeManifest(sample).contains("platform=android\n"))
        assertTrue(writeManifest(sample).startsWith("format=dokanhisab-backup\nformatVersion=1\n"))
    }

    @Test
    fun aVersionNameWithStrangeCharactersSurvives() {
        val odd = sample.copy(appVersionName = "1.0 = beta\\new\nline")
        assertEquals(odd, ok(writeManifest(odd)))
    }

    @Test
    fun aManifestOfAnotherPlatformIsAccepted() {
        // A future web or iOS version writes the same format with its own platform name.
        assertEquals("ios", ok(withLine("platform", "ios")).platform)
        assertEquals("", ok(withLine("platform", null)).platform)
    }

    @Test
    fun notOurFormatIsNotADokanBackup() {
        assertEquals(BackupProblem.NOT_DOKAN_BACKUP, problem(withLine("format", "something-else")))
        assertEquals(BackupProblem.NOT_DOKAN_BACKUP, problem(withLine("format", null)))
        assertEquals(BackupProblem.NOT_DOKAN_BACKUP, problem(""))
        assertEquals(BackupProblem.NOT_DOKAN_BACKUP, problem("hello world"))
    }

    @Test
    fun aNewerFormatVersionOrEncryptionIsTooNew() {
        assertEquals(BackupProblem.FORMAT_TOO_NEW, problem(withLine("formatVersion", "2")))
        assertEquals(BackupProblem.FORMAT_TOO_NEW, problem(withLine("encryption", "aes-256-gcm")))
    }

    @Test
    fun missingOrWrongValuesAreABadManifest() {
        for (key in listOf("formatVersion", "dbSchemaVersion", "createdAt", "dbEntry", "dbSize", "dbSha256")) {
            assertEquals("missing $key", BackupProblem.MANIFEST_BAD, problem(withLine(key, null)))
        }
        assertEquals(BackupProblem.MANIFEST_BAD, problem(withLine("formatVersion", "0")))
        assertEquals(BackupProblem.MANIFEST_BAD, problem(withLine("formatVersion", "one")))
        assertEquals(BackupProblem.MANIFEST_BAD, problem(withLine("dbSize", "-1")))
        assertEquals(BackupProblem.MANIFEST_BAD, problem(withLine("dbSha256", "A".repeat(64)))) // upper case
        assertEquals(BackupProblem.MANIFEST_BAD, problem(withLine("dbSha256", "a".repeat(63))))
        assertEquals(BackupProblem.MANIFEST_BAD, problem(withLine("count.sales", "-3")))
        assertEquals(BackupProblem.MANIFEST_BAD, problem(withLine("count.sales", "many")))
    }

    @Test
    fun theOptionalKeysHaveDefaults() {
        val m = ok(withLine("encryption", null).let { t -> t.lines().filterNot { it.startsWith("appVersion") }.joinToString("\n") })
        assertEquals(ENCRYPTION_NONE, m.encryption)
        assertEquals(0L, m.appVersionCode)
        assertEquals("", m.appVersionName)
    }

    @Test
    fun theSchemaVersionMustBeKnown() {
        assertNull(checkSchemaVersion(1))
        assertNull(checkSchemaVersion(2))
        assertNull(checkSchemaVersion(3))
        assertEquals(BackupProblem.SCHEMA_TOO_NEW, checkSchemaVersion(4))
        assertEquals(BackupProblem.SCHEMA_TOO_NEW, checkSchemaVersion(99))
        assertEquals(BackupProblem.MANIFEST_BAD, checkSchemaVersion(0))
        assertEquals(BackupProblem.MANIFEST_BAD, checkSchemaVersion(-1))
    }

    @Test
    fun theDatabaseEntryIsFixedInFormatOne() {
        assertNull(checkDbEntry(sample))
        assertEquals(BackupProblem.MANIFEST_BAD, checkDbEntry(sample.copy(dbEntry = "database/other.db")))
        assertEquals(BackupProblem.MANIFEST_BAD, checkDbEntry(sample.copy(dbEntry = "../dokan_hisab.db")))
    }

    // ---- the schema files ----

    @Test
    fun theKnownHashesMatchTheExportedSchemaFiles() {
        val files = schemaDir.listFiles { f -> f.name.endsWith(".json") }!!.map { it.name.removeSuffix(".json").toInt() }.sorted()
        assertEquals(files, KNOWN_SCHEMA_HASHES.keys.sorted())
        assertEquals(CURRENT_SCHEMA_VERSION, files.max())
        for (v in files) {
            val hash = Regex("\"identityHash\": \"(\\w+)\"").find(File(schemaDir, "$v.json").readText())!!.groupValues[1]
            assertEquals("schema $v", hash, KNOWN_SCHEMA_HASHES[v])
        }
    }

    @Test
    fun theCurrentSchemaVersionIsTheDatabaseVersion() {
        val source = File("src/main/java/com/example/dukanhisabnext/data/AppDatabase.kt").readText()
        assertTrue(source.contains("version = $CURRENT_SCHEMA_VERSION,"))
    }

    // ---- zip safety ----

    @Test
    fun safeAndUnsafeEntryNames() {
        assertTrue(isSafeEntryName("manifest.properties"))
        assertTrue(isSafeEntryName("database/dokan_hisab.db"))
        for (bad in listOf(
            "", "/etc/passwd", "../x", "database/../../x", "a/./b", "a//b", "a/", "C:x", "c:\\x",
            "database\\dokan_hisab.db", "a\u0000b", "a\nb", "x".repeat(201)
        )) {
            assertFalse("should be unsafe: $bad", isSafeEntryName(bad))
        }
    }

    private val limits = BackupLimits()
    private val manifestE = ZipEntryInfo(MANIFEST_ENTRY, 500, 300)
    private val dbE = ZipEntryInfo(DATABASE_ENTRY, 100_000, 20_000)

    @Test
    fun aNormalBackupPassesTheEntryCheck() {
        assertNull(checkEntries(listOf(manifestE, dbE), limits))
        assertNull(checkEntries(listOf(dbE, manifestE), limits)) // the order inside does not matter
    }

    @Test
    fun theEntryChecksInOrder() {
        val many = (1..33).map { ZipEntryInfo("e$it", 1, 1) }
        assertEquals(BackupProblem.UNSAFE_ZIP, checkEntries(many, limits))
        assertEquals(BackupProblem.UNSAFE_ZIP, checkEntries(listOf(manifestE, dbE, ZipEntryInfo("../evil", 1, 1)), limits))
        assertEquals(BackupProblem.UNSAFE_ZIP, checkEntries(listOf(manifestE, dbE, dbE), limits))
        assertEquals(BackupProblem.UNSAFE_ZIP, checkEntries(listOf(manifestE, dbE, ZipEntryInfo("photos/a.jpg", 1, 1)), limits))
        assertEquals(BackupProblem.MANIFEST_MISSING, checkEntries(listOf(dbE), limits))
        assertEquals(BackupProblem.MANIFEST_MISSING, checkEntries(emptyList(), limits))
        assertEquals(BackupProblem.NOT_DOKAN_BACKUP, checkEntries(listOf(manifestE), limits))
        assertEquals(BackupProblem.MANIFEST_BAD, checkEntries(listOf(manifestE.copy(size = 65 * 1024L), dbE), limits))
        assertEquals(BackupProblem.FILE_TOO_LARGE, checkEntries(listOf(manifestE, dbE.copy(size = 513L * 1024 * 1024)), limits))
        assertEquals(BackupProblem.UNSAFE_ZIP, checkEntries(listOf(manifestE, dbE.copy(size = 400L * 1024 * 1024, compressedSize = 1000)), limits))
    }

    @Test
    fun aSizeNotWrittenInTheZipIsCheckedWhileUnpacking() {
        // -1 means "unknown" in the zip directory; the engine then counts the bytes while unpacking.
        assertNull(checkEntries(listOf(manifestE.copy(size = -1), dbE.copy(size = -1)), limits))
    }

    @Test
    fun theBombRuleIgnoresSmallDatabases() {
        // A nearly empty database packs extremely well; that is not a bomb.
        assertFalse(looksLikeBomb(5L * 1024 * 1024, 100, limits))
        assertTrue(looksLikeBomb(20L * 1024 * 1024, 100, limits))
        assertFalse(looksLikeBomb(20L * 1024 * 1024, 1_000_000, limits))
        assertFalse(looksLikeBomb(20L * 1024 * 1024, 0, limits))
    }

    // ---- the file name ----

    @Test
    fun theFileNameUsesDhakaTimeAndLatinDigits() {
        // 06:09 UTC = 12:09 in Dhaka.
        assertEquals("DokanHisab-backup-2026-11-12-1209.zip", backupFileName(Instant.parse("2026-11-12T06:09:00Z").toEpochMilli()))
    }

    @Test
    fun lateAtNightTheDhakaDateIsAlreadyTheNextDay() {
        // 18:30 UTC on the 12th = 00:30 on the 13th in Dhaka.
        assertEquals("DokanHisab-backup-2026-11-13-0030.zip", backupFileName(Instant.parse("2026-11-12T18:30:00Z").toEpochMilli()))
        assertEquals("DokanHisab-backup-2027-01-01-0005.zip", backupFileName(Instant.parse("2026-12-31T18:05:59Z").toEpochMilli()))
    }

    // ---- hash ----

    @Test
    fun sha256OfTheStandardTestVectors() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex(ByteArrayInputStream("abc".toByteArray()))
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArrayInputStream(ByteArray(0)))
        )
        assertTrue(isSha256Hex(sha256Hex(ByteArrayInputStream(ByteArray(200_000) { it.toByte() }))))
    }

    @Test
    fun hexKeepsLeadingZeros() {
        assertEquals("000fff10", toHex(byteArrayOf(0, 0x0f, -1, 0x10)))
    }

    // ---- the recovery table ----

    @Test
    fun theRecoveryTable() {
        assertEquals(RecoveryAction.NOTHING, recoveryAction(null, false))
        assertEquals(RecoveryAction.NOTHING, recoveryAction(null, true)) // an old safety copy, no restore running
        assertEquals(RecoveryAction.DISCARD_PREPARED, recoveryAction(RestoreStage.PREPARED, false))
        assertEquals(RecoveryAction.ROLL_BACK, recoveryAction(RestoreStage.PREPARED, true))
        assertEquals(RecoveryAction.DISCARD_PREPARED, recoveryAction(RestoreStage.SAFETY, false))
        assertEquals(RecoveryAction.ROLL_BACK, recoveryAction(RestoreStage.SAFETY, true))
        assertEquals(RecoveryAction.FINISH, recoveryAction(RestoreStage.SWAPPED, false))
        assertEquals(RecoveryAction.FINISH, recoveryAction(RestoreStage.SWAPPED, true))
    }

    @Test
    fun theMarkerText() {
        assertEquals(RestoreStage.SAFETY, parseStage("SAFETY"))
        assertEquals(RestoreStage.SWAPPED, parseStage(" SWAPPED\n"))
        assertNull(parseStage("swapped"))
        assertNull(parseStage(""))
        assertNull(parseStage(null))
    }
}

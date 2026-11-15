package com.example.dukanhisabnext.backup

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The demo backups in docs/demo (made by tools/demo-data/make-demo.mjs, for the Play Store pictures and for trying the
 * app) are valid backup files by the app's own format rules: right entries, manifest, hash, database schema version,
 * and nothing real in them (shop names are the fictional ones).
 */
class DemoBackupsTest {

    private val dir = File("../docs/demo")
    private val zips = dir.listFiles { f -> f.name.endsWith(".zip") }.orEmpty().sortedBy { it.name }

    @Test
    fun thereAreFourDemoBackupsTwoShopsInTwoLanguages() {
        assertEquals(
            listOf("demo-bn-green-valley-grocery.zip", "demo-bn-sunrise-electronics.zip", "demo-en-green-valley-grocery.zip", "demo-en-sunrise-electronics.zip"),
            zips.map { it.name }
        )
    }

    @Test
    fun everyDemoBackupPassesTheFormatChecks() {
        for (file in zips) {
            ZipFile(file).use { zf ->
                val entries = zf.entries().asSequence().map { ZipEntryInfo(it.name, it.size, it.compressedSize) }.toList()
                assertNull(file.name, checkEntries(entries, BackupLimits()))
                val manifest = (parseManifest(zf.getInputStream(zf.getEntry(MANIFEST_ENTRY)).readBytes().toString(Charsets.UTF_8)) as ManifestResult.Ok).manifest
                assertEquals(file.name, CURRENT_SCHEMA_VERSION, manifest.dbSchemaVersion)
                assertNull(file.name, checkSchemaVersion(manifest.dbSchemaVersion))
                assertNull(file.name, checkDbEntry(manifest))
                val db = zf.getInputStream(zf.getEntry(DATABASE_ENTRY)).readBytes()
                assertEquals(file.name, manifest.dbSize, db.size.toLong())
                assertEquals(file.name, manifest.dbSha256, sha256Hex(db.inputStream()))
                assertTrue("SQLite header", String(db.copyOf(15), Charsets.US_ASCII) == "SQLite format 3")
                assertTrue(file.name, (manifest.counts["sales"] ?: 0) > 20 && (manifest.counts["products"] ?: 0) >= 9 && (manifest.counts["customers"] ?: 0) >= 5)
                assertEquals("BDT", manifest.displayCurrency)
            }
        }
    }

    @Test
    fun theShopsAreTheFictionalOnesAndTheirFileNamesStayAscii() {
        val names = zips.map { f ->
            ZipFile(f).use { zf ->
                (parseManifest(zf.getInputStream(zf.getEntry(MANIFEST_ENTRY)).readBytes().toString(Charsets.UTF_8)) as ManifestResult.Ok).manifest.shopName
            }
        }
        assertEquals(setOf("Green Valley Grocery & Agro", "Sunrise Electronics", "গ্রিন ভ্যালি মুদি ও কৃষি ভাণ্ডার", "সানরাইজ ইলেকট্রনিক্স"), names.toSet())
        assertTrue(zips.all { f -> f.name.all { it.code < 128 } })
    }
}

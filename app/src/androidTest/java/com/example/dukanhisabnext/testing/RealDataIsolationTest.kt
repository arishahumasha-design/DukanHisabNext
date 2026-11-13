package com.example.dukanhisabnext.testing

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dukanhisabnext.backup.BackupEngineTest
import com.example.dukanhisabnext.data.BackDatedSaleTest
import com.example.dukanhisabnext.data.CartPersistenceTest
import com.example.dukanhisabnext.data.MigrationTest
import com.example.dukanhisabnext.data.ReceiptAndShopRepositoryTest
import com.example.dukanhisabnext.data.SaleCancelRepositoryTest
import com.example.dukanhisabnext.data.SaleRepositoryTest
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.JUnitCore
import org.junit.runner.RunWith

/**
 * PROOF that the phone tests cannot touch the app's real data.
 *
 * It records what the real app has on this device (the shop's database: the rows of every table and
 * the shop info; the settings file backup_prefs.xml; every file of the files folder, which holds the
 * cart file and the sale-attempts file; the list of database files), runs a sample of the other phone
 * tests (the ones that use files, the real database class, a shop profile or a sale), and records it again.
 * The two records must be the same.
 *
 * This is the ONE phone test allowed to read the real app data (read-only): PhoneTestIsolationGuardTest
 * (PC) forbids it everywhere else. A second test proves the recording itself is not blind: it changes a
 * fake copy of the app's data (in an isolated folder) and the records then differ.
 */
@RunWith(AndroidJUnit4::class)
class RealDataIsolationTest {

    /** The data of one "app", as plain text lines (compared as a whole). */
    private class Snapshot(val lines: List<String>) {
        override fun equals(other: Any?) = other is Snapshot && other.lines == lines
        override fun hashCode() = lines.hashCode()
        override fun toString() = lines.joinToString("\n")
    }

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }.take(12)

    /** Every table: its row count and a checksum of all its rows, plus the shop info itself. */
    private fun databaseLines(dbFile: File): List<String> {
        if (!dbFile.exists()) return listOf("database: absent")
        val lines = mutableListOf<String>()
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val tables = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT IN ('android_metadata', 'sqlite_sequence', 'room_master_table') ORDER BY name",
                null
            ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            for (table in tables) {
                val dump = StringBuilder()
                var rows = 0
                db.rawQuery("SELECT * FROM `$table` ORDER BY 1", null).use { c ->
                    while (c.moveToNext()) {
                        rows++
                        for (i in 0 until c.columnCount) dump.append(c.getString(i)).append('\u0001')
                        dump.append('\n')
                    }
                }
                lines += "table $table: $rows rows, checksum ${sha(dump.toString().toByteArray())}"
            }
            if ("shop_profile" in tables) {
                db.rawQuery("SELECT id, name, ownerName, phone, address FROM shop_profile ORDER BY id", null).use { c ->
                    while (c.moveToNext()) lines += "shop: " + (0 until c.columnCount).joinToString(" | ") { c.getString(it).orEmpty() }
                }
            }
            lines += "user_version: ${db.version}"
        }
        return lines
    }

    /** Every file of a folder: relative path, size and a checksum (the cart file and the journal are in files/). */
    private fun treeLines(label: String, dir: File): List<String> {
        if (!dir.exists()) return listOf("$label: absent")
        return dir.walkTopDown().filter { it.isFile }
            .filterNot { it.name.startsWith("profileinstaller") || it.name.startsWith("profileInstalled") } // the system's own
            .sortedBy { it.relativeTo(dir).path }
            .map { "$label/${it.relativeTo(dir).path}: ${it.length()} bytes, ${sha(it.readBytes())}" }
            .toList()
    }

    private fun snapshotOf(dataDir: File): Snapshot {
        val lines = mutableListOf<String>()
        val databases = File(dataDir, "databases")
        lines += "database files: " + ((databases.list()?.filter { !it.endsWith("-shm") && !it.endsWith("-wal") && !it.endsWith("-journal") }?.sorted()) ?: emptyList<String>())
        lines += databaseLines(File(databases, "dokan_hisab.db"))
        val prefs = File(dataDir, "shared_prefs/backup_prefs.xml")
        lines += if (prefs.exists()) "backup_prefs.xml: ${sha(prefs.readBytes())} ${prefs.readText().replace("\n", " ")}" else "backup_prefs.xml: absent"
        lines += treeLines("files", File(dataDir, "files"))
        return Snapshot(lines)
    }

    // =====================================================================================
    // The proof
    // =====================================================================================

    @Test
    fun runningOtherPhoneTestsLeavesTheAppsRealDataUntouched() {
        val real: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = snapshotOf(real.dataDir)

        // A sample of the tests that use files, the real database class, a shop profile or a saved sale.
        val result = JUnitCore().run(
            BackupEngineTest::class.java,
            MigrationTest::class.java,
            CartPersistenceTest::class.java,
            ReceiptAndShopRepositoryTest::class.java,
            SaleRepositoryTest::class.java,
            SaleCancelRepositoryTest::class.java,
            BackDatedSaleTest::class.java
        )
        assertTrue(
            "the sample tests must pass: " + result.failures.joinToString("\n") { it.testHeader + ": " + it.message },
            result.wasSuccessful()
        )
        assertTrue("the sample really ran (${result.runCount} tests)", result.runCount >= 60)

        val after = snapshotOf(real.dataDir)
        assertEquals("the app's real data changed while the sample tests ran:", before.toString(), after.toString())
    }

    @Test
    fun theRecordingNoticesAChangeToTheShopTheSettingsOrTheFiles() {
        // A fake "app data folder" made like the real one, inside an isolated folder (never the real data).
        val isolated = IsolatedContext.create(InstrumentationRegistry.getInstrumentation().targetContext, "isolation-proof")
        try {
            val fake = File(isolated.root, "fake-app").also { it.mkdirs() }
            val dbFile = File(fake, "databases/dokan_hisab.db").also { it.parentFile!!.mkdirs() }
            SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
                db.execSQL("CREATE TABLE shop_profile (id INTEGER PRIMARY KEY, name TEXT, ownerName TEXT, phone TEXT, address TEXT)")
                db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY, total INTEGER)")
                db.execSQL("INSERT INTO shop_profile VALUES (1, 'দোকান', 'মালিক', '017', 'ঠিকানা')")
                db.execSQL("INSERT INTO sales VALUES (1, 100)")
            }
            File(fake, "shared_prefs").mkdirs()
            File(fake, "shared_prefs/backup_prefs.xml").writeText("<map><long name=\"lastBackupAt\" value=\"1\" /></map>")
            File(fake, "files/cart").mkdirs()
            File(fake, "files/cart/cart.json").writeText("{\"lines\":[]}")
            File(fake, "files/cart/sale-attempts.txt").writeText("a=1\n")

            val baseline = snapshotOf(fake)
            assertEquals("the same data gives the same record", baseline, snapshotOf(fake))

            fun mutate(what: String, change: () -> Unit, undo: () -> Unit) {
                change()
                assertNotEquals("a change of $what must show", baseline.toString(), snapshotOf(fake).toString())
                undo()
                assertEquals("after undoing $what the record is the same again", baseline.toString(), snapshotOf(fake).toString())
            }

            mutate("the shop name",
                { SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("UPDATE shop_profile SET name = 'বদলানো'") } },
                { SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("UPDATE shop_profile SET name = 'দোকান'") } })
            mutate("a sale",
                { SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("INSERT INTO sales VALUES (2, 50)") } },
                { SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("DELETE FROM sales WHERE id = 2") } })
            val prefs = File(fake, "shared_prefs/backup_prefs.xml")
            val prefsText = prefs.readText()
            mutate("the backup time", { prefs.writeText(prefsText.replace("\"1\"", "\"2\"")) }, { prefs.writeText(prefsText) })
            val cart = File(fake, "files/cart/cart.json")
            val cartText = cart.readText()
            mutate("the cart file", { cart.writeText("{\"lines\":[1]}") }, { cart.writeText(cartText) })
            val journal = File(fake, "files/cart/sale-attempts.txt")
            mutate("the sale-attempts file", { journal.appendText("b=2\n") }, { journal.writeText("a=1\n") })
            val extra = File(fake, "files/extra.txt")
            mutate("a new file", { extra.writeText("x") }, { extra.delete() })
        } finally {
            isolated.cleanUp()
        }
    }

    @Test
    fun theIsolatedContextReallyLivesOutsideTheApp() {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val isolated = IsolatedContext.create(real, "isolation-location")
        try {
            // The three folders that hold the app's real data (and its no-backup copy) are never used.
            val realData = listOf(real.filesDir, File(real.dataDir, "databases"), File(real.dataDir, "shared_prefs"), real.noBackupFilesDir)
                .map { it.canonicalPath }
            val inIsolation = File(real.cacheDir, IsolatedContext.FOLDER).canonicalPath
            for (folder in listOf(isolated.filesDir, isolated.cacheDir, isolated.noBackupFilesDir, isolated.getDatabasePath("x.db"), isolated.getDir("d", 0))) {
                val path = folder.canonicalPath
                assertTrue("$folder must be inside $inIsolation", path.startsWith(inIsolation))
                for (data in realData) assertTrue("$folder must not be inside the real data folder $data", !path.startsWith(data))
            }
            assertTrue(isolated.applicationContext === isolated)
            var refused = false
            try {
                isolated.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
            } catch (e: UnsupportedOperationException) {
                refused = true
            }
            assertTrue("SharedPreferences must be refused", refused)
        } finally {
            isolated.cleanUp()
        }
    }
}

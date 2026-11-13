package com.example.dukanhisabnext.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dukanhisabnext.data.ALL_MIGRATIONS
import com.example.dukanhisabnext.data.AppDatabase
import com.example.dukanhisabnext.data.createDatabaseFromSchema
import com.example.dukanhisabnext.data.insertShopData
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phone test of the backup engine with REAL files. It uses its own database name
 * ("backup-test-live.db") and its own folders, never the shop's dokan_hisab.db.
 *
 * "A" is the data in the backup, "B" the data on the phone when the restore starts.
 */
@RunWith(AndroidJUnit4::class)
class BackupEngineTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val liveName = "backup-test-live.db"
    private val workRoot = File(context.filesDir, "backup-test-root")
    private val files = File(context.cacheDir, "backup-test-files")
    private val now = 1_762_927_740_000L

    /** A simulated crash (the battery dies): an Error, so no normal error handling catches it. */
    private class SimulatedCrash : Error()

    // ---- the "live" database of these tests ----

    private var live: AppDatabase? = null

    private fun openLive(): AppDatabase = live ?: Room.databaseBuilder(context, AppDatabase::class.java, liveName)
        .addMigrations(*ALL_MIGRATIONS).build().also { live = it }

    private fun closeLive() {
        live?.close()
        live = null
    }

    private fun engine(
        limits: BackupLimits = BackupLimits(),
        space: (File) -> Long = { it.usableSpace },
        fault: (BackupStep) -> Unit = {}
    ) = BackupEngine(
        context = context, getLive = ::openLive, closeLive = ::closeLive,
        appVersionCode = 5, appVersionName = "test", liveDbName = liveName, workRoot = workRoot,
        clock = { now }, limits = limits, usableSpace = space, fault = fault
    )

    private fun cleanAll() {
        closeLive()
        context.deleteDatabase(liveName)
        context.deleteDatabase("$liveName.restore-prepared")
        workRoot.deleteRecursively()
        files.deleteRecursively()
        files.mkdirs()
    }

    @Before
    fun setUp() = cleanAll()

    @After
    fun tearDown() {
        cleanAll()
        files.deleteRecursively()
    }

    // ---- data ----

    private fun sql(vararg statements: String) {
        val db = openLive().openHelper.writableDatabase
        for (s in statements) db.execSQL(s)
    }

    /** "A": a row in every table, Bengali text, nulls, a cancelled sale, a deleted expense. */
    private fun fillA() = sql(
        "INSERT INTO shop_profile (id, name, ownerName, phone, address) VALUES (1, 'রহিম স্টোর', 'রহিম', '01711111111', NULL)",
        "INSERT INTO categories (id, name) VALUES (1, 'চাল-ডাল')",
        "INSERT INTO products (id, name, categoryId, unit, sellPrice, costPrice, lowStockLevel, isActive, createdAt, updatedAt) VALUES (1, 'চাল', 1, 'KG', 7500, 6800, 5000, 1, 1, 1)",
        "INSERT INTO products (id, name, categoryId, unit, sellPrice, costPrice, lowStockLevel, isActive, createdAt, updatedAt) VALUES (2, 'সাবান', NULL, 'PIECE', 4500, NULL, NULL, 0, 1, 2)",
        "INSERT INTO stock_movements (id, productId, type, quantity, unitCost, referenceSaleId, note, createdAt) VALUES (1, 1, 'OPENING', 20000, NULL, NULL, NULL, 1)",
        "INSERT INTO stock_movements (id, productId, type, quantity, unitCost, referenceSaleId, note, createdAt) VALUES (2, 1, 'PURCHASE', 10500, 6800, NULL, 'হাট থেকে', 2)",
        "INSERT INTO customers (id, name, phone, address, note, createdAt, updatedAt) VALUES (1, 'করিম', '01811111111', 'বাজার রোড', NULL, 1, 1)",
        "INSERT INTO sales (id, receiptNo, customerId, subtotal, discount, deliveryCharge, total, paidAmount, paymentMethod, dueAmount, note, createdAt, saleDate) VALUES (1, '261112-001', 1, 22500, 500, 0, 22000, 2000, 'BKASH', 20000, NULL, 3, 3)",
        "INSERT INTO sale_items (id, saleId, productId, productNameSnapshot, quantity, unitPrice, costPriceSnapshot, lineTotal) VALUES (1, 1, 1, 'চাল', 3000, 7500, 6800, 22500)",
        "INSERT INTO stock_movements (id, productId, type, quantity, unitCost, referenceSaleId, note, createdAt) VALUES (3, 1, 'SALE', -3000, NULL, 1, NULL, 3)",
        "INSERT INTO ledger_entries (id, customerId, type, amount, paymentMethod, saleId, note, entryDate, createdAt) VALUES (1, 1, 'SALE_DUE', 20000, NULL, 1, NULL, 3, 3)",
        "INSERT INTO ledger_entries (id, customerId, type, amount, paymentMethod, saleId, note, entryDate, createdAt) VALUES (2, 1, 'PAYMENT_RECEIVED', 5000, 'CASH', NULL, 'আংশিক', 4, 4)",
        "INSERT INTO sales (id, receiptNo, customerId, subtotal, discount, deliveryCharge, total, paidAmount, paymentMethod, dueAmount, note, createdAt, saleDate) VALUES (2, '261112-002', NULL, 4500, 0, 1000, 5500, 5500, 'CASH', 0, 'ভুল বিক্রি', 5, 5)",
        "INSERT INTO sale_items (id, saleId, productId, productNameSnapshot, quantity, unitPrice, costPriceSnapshot, lineTotal) VALUES (2, 2, 2, 'সাবান', 1000, 4500, NULL, 4500)",
        "INSERT INTO sale_cancellations (id, saleId, reason, createdAt) VALUES (1, 2, NULL, 6)",
        "INSERT INTO expense_categories (id, name, isPersonal, isActive, systemKey) VALUES (1, 'ভাড়া', 0, 1, 'RENT')",
        "INSERT INTO expense_categories (id, name, isPersonal, isActive, systemKey) VALUES (2, 'পরিবার', 1, 0, 'FAMILY')",
        "INSERT INTO expenses (id, expenseCategoryId, amount, reason, expenseDate, isDeleted, createdAt, updatedAt) VALUES (1, 1, 500000, 'নভেম্বর', 7, 0, 7, 7)",
        "INSERT INTO expenses (id, expenseCategoryId, amount, reason, expenseDate, isDeleted, createdAt, updatedAt) VALUES (2, 2, 1200, NULL, 8, 1, 8, 9)"
    )

    /** "B": what happens on the phone after the backup was made. */
    private fun changeToB() = sql(
        "INSERT INTO customers (id, name, phone, address, note, createdAt, updatedAt) VALUES (2, 'নতুন কাস্টমার', NULL, NULL, NULL, 10, 10)",
        "INSERT INTO ledger_entries (id, customerId, type, amount, paymentMethod, saleId, note, entryDate, createdAt) VALUES (3, 2, 'MANUAL_DUE', 9900, NULL, NULL, NULL, 10, 10)",
        "UPDATE shop_profile SET name = 'রহিম স্টোর ২' WHERE id = 1",
        "INSERT INTO expenses (id, expenseCategoryId, amount, reason, expenseDate, isDeleted, createdAt, updatedAt) VALUES (3, 1, 300, 'চা', 11, 0, 11, 11)"
    )

    private fun tablesOf(db: androidx.sqlite.db.SupportSQLiteDatabase): List<String> =
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('room_master_table', 'android_metadata') ORDER BY name"
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    /** Every row of every table, every column, as text. */
    private fun snapshot(): Map<String, List<List<String?>>> {
        val db = openLive().openHelper.readableDatabase
        return tablesOf(db).associateWith { table ->
            db.query("SELECT * FROM `$table` ORDER BY rowid").use { c ->
                buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
            }
        }
    }

    private fun liveCount(table: String): Long =
        openLive().openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getLong(0) }

    private fun assertLiveIsHealthy() {
        val db = openLive().openHelper.writableDatabase
        assertEquals(CURRENT_SCHEMA_VERSION, db.version)
        assertEquals("ok", db.query("PRAGMA integrity_check").use { it.moveToFirst(); it.getString(0) })
        // It can still be written to.
        db.execSQL("INSERT INTO categories (name) VALUES ('পরীক্ষা')")
        db.execSQL("DELETE FROM categories WHERE name = 'পরীক্ষা'")
    }

    // ---- backup files ----

    /** Makes a backup of the live data and keeps a copy of the zip in the test folder. */
    private suspend fun backup(): Pair<File, BackupManifest> {
        val result = engine().createBackup()
        assertTrue("expected Created, got $result", result is CreateResult.Created)
        result as CreateResult.Created
        val copy = File(files, result.zip.name)
        result.zip.copyTo(copy, overwrite = true)
        return copy to result.manifest
    }

    private fun entriesOf(zip: File): Map<String, ByteArray> = ZipFile(zip).use { zf ->
        zf.entries().toList().associate { e -> e.name to zf.getInputStream(e).use { it.readBytes() } }
    }

    private fun zipOf(name: String, vararg entries: Pair<String, ByteArray>): File {
        val file = File(files, name)
        ZipOutputStream(FileOutputStream(file)).use { out ->
            for ((entry, bytes) in entries) {
                out.putNextEntry(ZipEntry(entry))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return file
    }

    private fun setKey(manifest: String, key: String, value: String?): String {
        val lines = manifest.lines().filter { it.isNotEmpty() && !it.startsWith("$key=") }
        return (if (value == null) lines else lines + "$key=$value").joinToString("\n", postfix = "\n")
    }

    private fun sha(bytes: ByteArray) = sha256Hex(bytes.inputStream())

    /** A changed copy of a good backup. [fixHash] writes the size and hash of the changed database into the manifest. */
    private fun rebuild(
        good: File,
        name: String,
        fixHash: Boolean = false,
        editManifest: (String) -> String = { it },
        editDb: (ByteArray) -> ByteArray = { it }
    ): File {
        val entries = entriesOf(good)
        val db = editDb(entries.getValue(DATABASE_ENTRY))
        var manifest = String(entries.getValue(MANIFEST_ENTRY), Charsets.UTF_8)
        if (fixHash) manifest = setKey(setKey(manifest, "dbSize", db.size.toString()), "dbSha256", sha(db))
        return zipOf(name, MANIFEST_ENTRY to editManifest(manifest).toByteArray(), DATABASE_ENTRY to db)
    }

    /** A clean single-file database of [version] built from its saved schema, with shop data. */
    private fun oldDatabase(version: Int): File {
        val file = File(files, "old-v$version.db")
        createDatabaseFromSchema(file, version).apply {
            insertShopData()
            if (version >= 2) execSQL("INSERT INTO sale_cancellations (id, saleId, reason, createdAt) VALUES (1, 1, 'ভুল', 6)")
            // A version 5 file written by the app always has a real sale date.
            if (version >= 5) execSQL("UPDATE sales SET saleDate = createdAt")
            rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
            close()
        }
        return file
    }

    private fun countsOf(file: File): Map<String, Long> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val tables = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                    "AND name NOT IN ('room_master_table', 'android_metadata')", null
            ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            tables.associateWith { t -> db.rawQuery("SELECT COUNT(*) FROM `$t`", null).use { it.moveToFirst(); it.getLong(0) } }
        }

    /** A backup zip around any database file, with a correct manifest for it. */
    private fun zipAround(db: File, version: Int, name: String): File {
        val bytes = db.readBytes()
        val manifest = BackupManifest(
            dbSchemaVersion = version, appVersionCode = 1, appVersionName = "old", createdAt = now,
            dbSize = bytes.size.toLong(), dbSha256 = sha(bytes), counts = countsOf(db)
        )
        return zipOf(name, MANIFEST_ENTRY to writeManifest(manifest).toByteArray(), DATABASE_ENTRY to bytes)
    }

    // ---- refusals ----

    /** The restore is refused with [problem], nothing is left behind, and the live data is exactly as before. */
    private suspend fun assertRefused(zip: File, problem: BackupProblem, e: BackupEngine = engine()) {
        val before = snapshot()
        val result = e.prepareRestore(zip)
        assertEquals(PrepareResult.Refused(problem), result)
        assertFalse("no prepared file is left", e.paths.prepared.exists())
        assertFalse("no marker", e.paths.marker.exists())
        assertFalse("no check folder is left", File(e.paths.workDir, "check").exists())
        assertEquals("the live data did not change", before, snapshot())
        assertLiveIsHealthy()
    }

    // =====================================================================================
    // Create and restore
    // =====================================================================================

    @Test
    fun aRestoreBringsBackEveryRowOfEveryTable() = runBlocking {
        fillA()
        val a = snapshot()
        assertEquals(11, a.size)
        assertTrue(a.values.all { it.isNotEmpty() })
        val (zip, _) = backup()

        changeToB()
        assertFalse(a == snapshot())

        val e = engine()
        val ready = e.prepareRestore(zip)
        assertTrue("expected Ready, got $ready", ready is PrepareResult.Ready)
        ready as PrepareResult.Ready
        assertEquals(CURRENT_SCHEMA_VERSION, ready.restore.fromSchemaVersion)
        assertEquals(a.mapValues { it.value.size.toLong() }, ready.restore.counts)
        assertEquals(RestoreResult.Done(ready.restore.counts), e.restore(ready.restore))

        assertNull("the engine closed the live database", live)
        assertEquals(a, snapshot())
        assertLiveIsHealthy()
        assertTrue(e.hasSafetyCopy())
        assertFalse(e.paths.marker.exists())
        assertFalse(e.paths.prepared.exists())
        assertFalse(e.paths.workDir.exists())

        val result = e.readRestoreResult()!!
        assertEquals("false", result["undo"])
        assertEquals(CURRENT_SCHEMA_VERSION.toString(), result["fromSchemaVersion"])
        assertEquals(now.toString(), result["backupCreatedAt"])
        assertEquals("2", result["count.sales"])
        e.clearRestoreResult()
        assertNull(e.readRestoreResult())
    }

    @Test
    fun theBackupFileHasItsNameTwoEntriesAndACorrectManifest() = runBlocking {
        fillA()
        val (zip, manifest) = backup()
        assertEquals(backupFileName(now), zip.name)
        assertEquals(listOf(MANIFEST_ENTRY, DATABASE_ENTRY), ZipFile(zip).use { zf -> zf.entries().toList().map { it.name } })

        val entries = entriesOf(zip)
        val text = String(entries.getValue(MANIFEST_ENTRY), Charsets.UTF_8)
        assertTrue(text.startsWith("format=dokanhisab-backup\nformatVersion=1\nplatform=android\n"))
        assertEquals(ManifestResult.Ok(manifest), parseManifest(text))
        assertEquals(CURRENT_SCHEMA_VERSION, manifest.dbSchemaVersion)
        assertEquals(5L, manifest.appVersionCode)
        assertEquals(now, manifest.createdAt)

        val db = entries.getValue(DATABASE_ENTRY)
        assertEquals(manifest.dbSize, db.size.toLong())
        assertEquals(manifest.dbSha256, sha(db))
        // One clean file: bytes 18 and 19 of the SQLite header are 1 (rollback journal), not 2 (WAL).
        assertEquals(1, db[18].toInt())
        assertEquals(1, db[19].toInt())
        assertEquals(snapshot().mapValues { it.value.size.toLong() }, manifest.counts)
        // Nothing of the job is left in the work folder except the finished zip.
        assertFalse(File(workRoot, "backup-work/create").exists())
    }

    @Test
    fun anEmptyShopCanBeBackedUpAndRestored() = runBlocking {
        openLive().openHelper.writableDatabase
        val (zip, manifest) = backup()
        assertTrue(manifest.counts.values.all { it == 0L })
        fillA()
        val e = engine()
        val ready = e.prepareRestore(zip) as PrepareResult.Ready
        e.restore(ready.restore)
        assertTrue(snapshot().values.all { it.isEmpty() })
        assertLiveIsHealthy()
    }

    @Test
    fun aBackupWhileSalesAreBeingSavedHoldsOnlyWholeSales() = runBlocking {
        val db = openLive()
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO products (id, name, categoryId, unit, sellPrice, costPrice, lowStockLevel, isActive, createdAt, updatedAt) VALUES (1, 'চাল', NULL, 'KG', 7500, NULL, NULL, 1, 1, 1)"
        )
        // Each sale is one transaction of three rows, like a real sale.
        val writer = launch(Dispatchers.IO) {
            var i = 0
            while (isActive) {
                i++
                db.withTransaction {
                    val w = db.openHelper.writableDatabase
                    w.execSQL(
                        "INSERT INTO sales (id, receiptNo, customerId, subtotal, discount, deliveryCharge, total, paidAmount, paymentMethod, dueAmount, note, createdAt) " +
                            "VALUES ($i, 'W-$i', NULL, 7500, 0, 0, 7500, 7500, 'CASH', 0, NULL, $i)"
                    )
                    w.execSQL(
                        "INSERT INTO sale_items (id, saleId, productId, productNameSnapshot, quantity, unitPrice, costPriceSnapshot, lineTotal) " +
                            "VALUES ($i, $i, 1, 'চাল', 1000, 7500, NULL, 7500)"
                    )
                    w.execSQL("INSERT INTO stock_movements (id, productId, type, quantity, unitCost, referenceSaleId, note, createdAt) VALUES ($i, 1, 'SALE', -1000, NULL, $i, NULL, $i)")
                }
            }
        }
        while (liveCount("sales") < 20) delay(5)
        val (zip, manifest) = backup()
        writer.cancelAndJoin()

        val inBackup = manifest.counts.getValue("sales")
        assertTrue("the backup has the sales saved before it ($inBackup)", inBackup >= 20)
        assertEquals("every sale has its item", inBackup, manifest.counts.getValue("sale_items"))
        assertEquals("every sale has its stock movement", inBackup, manifest.counts.getValue("stock_movements"))
        // And the file itself is whole and passes every restore check.
        val ready = engine().prepareRestore(zip)
        assertTrue("$ready", ready is PrepareResult.Ready)
        assertTrue("the shop went on selling during the backup", liveCount("sales") >= inBackup)
    }

    @Test
    fun undoBringsBackTheDataFromBeforeTheRestoreAndTheSafetyCopyIsReplacedEachTime() = runBlocking {
        fillA()
        val a = snapshot()
        val (zip, _) = backup()
        changeToB()
        val b = snapshot()

        val e = engine()
        assertFalse(e.hasSafetyCopy())
        e.restore((e.prepareRestore(zip) as PrepareResult.Ready).restore)
        assertEquals(a, snapshot())

        // "আগের ডেটা ফিরিয়ে আনুন"
        val undo = e.prepareUndo()
        assertTrue("$undo", undo is PrepareResult.Ready)
        undo as PrepareResult.Ready
        assertNull(undo.restore.manifest)
        assertEquals(RestoreResult.Done(undo.restore.counts), e.restore(undo.restore))
        assertEquals(b, snapshot())
        assertLiveIsHealthy()
        assertEquals("true", e.readRestoreResult()!!["undo"])

        // The safety copy now holds A (the data before the undo), so undo again gives A.
        e.restore((e.prepareUndo() as PrepareResult.Ready).restore)
        assertEquals(a, snapshot())
        // Only one safety folder with only the live file names.
        val names = e.paths.safetyDir.listFiles()!!.map { it.name }.toSet()
        assertTrue(names.all { it.startsWith(liveName) })
    }

    @Test
    fun undoWithoutASafetyCopyIsRefused() = runBlocking {
        fillA()
        val before = snapshot()
        assertEquals(PrepareResult.Refused(BackupProblem.CANNOT_READ), engine().prepareUndo())
        assertEquals(before, snapshot())
    }

    // =====================================================================================
    // Older versions
    // =====================================================================================

    private suspend fun restoreOld(version: Int) {
        fillA()
        val zip = zipAround(oldDatabase(version), version, "old-v$version.zip")
        val e = engine()
        val ready = e.prepareRestore(zip)
        assertTrue("expected Ready, got $ready", ready is PrepareResult.Ready)
        ready as PrepareResult.Ready
        assertEquals(version, ready.restore.fromSchemaVersion)
        assertEquals(0L, ready.restore.counts["shop_profile"])
        assertTrue("sale_cancellations exists after the upgrade", "sale_cancellations" in ready.restore.counts)
        e.restore(ready.restore)

        assertLiveIsHealthy()
        val db = openLive()
        assertEquals(7_000L, db.stockMovementDao().getStock(1)) // 10 kg - 3 kg sold
        assertEquals(20_000L, db.ledgerDao().getBalance(1))
        assertEquals("করিম", db.openHelper.readableDatabase.query("SELECT name FROM customers WHERE id = 1").use { it.moveToFirst(); it.getString(0) })
        db.openHelper.readableDatabase.query("SELECT isPersonal, isActive, systemKey FROM expense_categories").use {
            assertEquals(2, it.count)
            while (it.moveToNext()) {
                assertEquals(0, it.getInt(0))
                assertEquals(1, it.getInt(1))
                assertTrue(it.isNull(2))
            }
        }
        assertEquals(if (version >= 2) 1L else 0L, liveCount("sale_cancellations"))
        assertEquals(3L, liveCount("expenses"))
        // Every old sale keeps its date (version 5: saleDate = createdAt) and has no warranty.
        db.openHelper.readableDatabase.query("SELECT createdAt, saleDate FROM sales").use {
            assertTrue(it.count > 0)
            while (it.moveToNext()) assertEquals(it.getLong(0), it.getLong(1))
        }
        assertEquals(0L, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sale_items WHERE warrantyValue IS NOT NULL").use { it.moveToFirst(); it.getLong(0) })
        // Every backup ends up with the version 4 stock index.
        val hasStockIndex = db.openHelper.readableDatabase.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = 'index_stock_movements_productId_quantity'"
        ).use { it.moveToFirst() }
        assertTrue(hasStockIndex)
    }

    @Test
    fun aVersion1BackupIsBroughtUpToDate() = runBlocking { restoreOld(1) }

    @Test
    fun aVersion2BackupIsBroughtUpToDate() = runBlocking { restoreOld(2) }

    @Test
    fun aVersion3BackupIsBroughtUpToDate() = runBlocking { restoreOld(3) }

    @Test
    fun aVersion4BackupIsBroughtUpToDate() = runBlocking { restoreOld(4) }

    @Test
    fun aVersion5BackupIsAcceptedAsItIs() = runBlocking {
        assertEquals(5, CURRENT_SCHEMA_VERSION)
        restoreOld(5)
    }

    // =====================================================================================
    // Every refusal leaves the live data alone
    // =====================================================================================

    @Test
    fun aFileThatIsNotAZipIsRefused() = runBlocking {
        fillA()
        val junk = File(files, "junk.zip").apply { writeBytes(Random(1).nextBytes(5_000)) }
        assertRefused(junk, BackupProblem.NOT_A_ZIP)
        val empty = File(files, "empty.zip").apply { writeBytes(ByteArray(0)) }
        assertRefused(empty, BackupProblem.NOT_A_ZIP)
    }

    @Test
    fun aCutOffFileIsRefused() = runBlocking {
        fillA()
        val (zip, _) = backup()
        val bytes = zip.readBytes()
        val half = File(files, "half.zip").apply { writeBytes(bytes.copyOf(bytes.size / 2)) }
        assertRefused(half, BackupProblem.NOT_A_ZIP)
    }

    @Test
    fun aChangedDatabaseIsRefusedByItsHash() = runBlocking {
        fillA()
        val (zip, _) = backup()
        val flipped = rebuild(zip, "flipped.zip") { db -> db.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() } }
        assertRefused(flipped, BackupProblem.HASH_MISMATCH)
        val shorter = rebuild(zip, "shorter.zip") { db -> db.copyOf(db.size - 4096) }
        assertRefused(shorter, BackupProblem.HASH_MISMATCH)
    }

    @Test
    fun aNewerFormatOrSchemaIsRefusedAsTooNew() = runBlocking {
        fillA()
        val (zip, _) = backup()
        assertRefused(rebuild(zip, "f2.zip", editManifest = { setKey(it, "formatVersion", "2") }), BackupProblem.FORMAT_TOO_NEW)
        assertRefused(rebuild(zip, "enc.zip", editManifest = { setKey(it, "encryption", "aes") }), BackupProblem.FORMAT_TOO_NEW)
        val newer = (CURRENT_SCHEMA_VERSION + 1).toString()
        assertRefused(rebuild(zip, "newer.zip", editManifest = { setKey(it, "dbSchemaVersion", newer) }), BackupProblem.SCHEMA_TOO_NEW)
    }

    @Test
    fun aDatabaseNewerThanItsManifestSaysIsRefused() = runBlocking {
        // The manifest says the current version, the file inside says one newer (user_version):
        // never trust the manifest alone.
        fillA()
        val (zip, _) = backup()
        val newerDb = File(files, "newer.db")
        entriesOf(zip).getValue(DATABASE_ENTRY).let(newerDb::writeBytes)
        SQLiteDatabase.openDatabase(newerDb.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = CURRENT_SCHEMA_VERSION + 1 }
        val bytes = newerDb.readBytes()
        assertRefused(rebuild(zip, "newer-inside.zip", fixHash = true) { bytes }, BackupProblem.SCHEMA_TOO_NEW)
    }

    @Test
    fun aZipOfSomethingElseIsRefused() = runBlocking {
        fillA()
        val (zip, _) = backup()
        assertRefused(rebuild(zip, "other.zip", editManifest = { setKey(it, "format", "something-else") }), BackupProblem.NOT_DOKAN_BACKUP)
        assertRefused(zipOf("photos.zip", "a.jpg" to ByteArray(10)), BackupProblem.UNSAFE_ZIP)
        assertRefused(zipOf("manifest-only.zip", MANIFEST_ENTRY to entriesOf(zip).getValue(MANIFEST_ENTRY)), BackupProblem.NOT_DOKAN_BACKUP)
    }

    @Test
    fun aMissingOrBrokenManifestIsRefused() = runBlocking {
        fillA()
        val (zip, _) = backup()
        assertRefused(zipOf("no-manifest.zip", DATABASE_ENTRY to entriesOf(zip).getValue(DATABASE_ENTRY)), BackupProblem.MANIFEST_MISSING)
        assertRefused(rebuild(zip, "no-hash.zip", editManifest = { setKey(it, "dbSha256", null) }), BackupProblem.MANIFEST_BAD)
        assertRefused(rebuild(zip, "bad-entry.zip", editManifest = { setKey(it, "dbEntry", "database/x.db") }), BackupProblem.MANIFEST_BAD)
    }

    @Test
    fun aPathEscapeOrAnExtraEntryIsRefused() = runBlocking {
        fillA()
        val (zip, _) = backup()
        val entries = entriesOf(zip)
        val escape = zipOf(
            "escape.zip",
            MANIFEST_ENTRY to entries.getValue(MANIFEST_ENTRY),
            DATABASE_ENTRY to entries.getValue(DATABASE_ENTRY),
            "../../evil.txt" to "x".toByteArray()
        )
        assertRefused(escape, BackupProblem.UNSAFE_ZIP)
        assertFalse(File(context.filesDir.parentFile, "evil.txt").exists())
        val extra = zipOf(
            "extra.zip",
            MANIFEST_ENTRY to entries.getValue(MANIFEST_ENTRY),
            DATABASE_ENTRY to entries.getValue(DATABASE_ENTRY),
            "files/photo.jpg" to ByteArray(10)
        )
        assertRefused(extra, BackupProblem.UNSAFE_ZIP)
    }

    @Test
    fun aZipBombIsRefused() = runBlocking {
        fillA()
        val small = BackupLimits(maxEntryBytes = 1_000_000, ratioFloorBytes = 100_000, maxRatio = 100)
        val zeros = ByteArray(3_000_000)
        val manifest = BackupManifest(
            dbSchemaVersion = 3, appVersionCode = 1, appVersionName = "x", createdAt = now,
            dbSize = zeros.size.toLong(), dbSha256 = sha(zeros), counts = emptyMap()
        )
        val bomb = zipOf("bomb.zip", MANIFEST_ENTRY to writeManifest(manifest).toByteArray(), DATABASE_ENTRY to zeros)
        // Bigger than the unpack limit:
        assertRefused(bomb, BackupProblem.FILE_TOO_LARGE, engine(limits = small))
        // Within the size limit, but it packs far too well:
        assertRefused(bomb, BackupProblem.UNSAFE_ZIP, engine(limits = small.copy(maxEntryBytes = 10_000_000)))
    }

    @Test
    fun aFileBiggerThanTheLimitIsRefused() = runBlocking {
        fillA()
        val (zip, _) = backup()
        assertRefused(zip, BackupProblem.FILE_TOO_LARGE, engine(limits = BackupLimits(maxBackupBytes = 1_000)))
    }

    @Test
    fun aDamagedDatabaseIsRefused() = runBlocking {
        fillA()
        val (zip, _) = backup()
        // The right hash, but the database pages are garbage.
        val garbled = rebuild(zip, "garbled.zip", fixHash = true) { db ->
            db.copyOf().also { for (i in 4096 until minOf(db.size, 3 * 4096)) it[i] = 0x55 }
        }
        assertRefused(garbled, BackupProblem.DB_DAMAGED)
        // Not a SQLite file at all.
        val text = rebuild(zip, "text.zip", fixHash = true) { "hello, this is not a database".repeat(200).toByteArray() }
        assertRefused(text, BackupProblem.DB_DAMAGED)
    }

    @Test
    fun aDatabaseOfAnotherAppIsRefused() = runBlocking {
        fillA()
        val other = File(files, "other.db")
        SQLiteDatabase.openOrCreateDatabase(other, null).use { db ->
            db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY, text TEXT)")
            db.execSQL("INSERT INTO notes (text) VALUES ('hello')")
            db.version = 3
            db.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
        }
        assertRefused(zipAround(other, 3, "other.zip"), BackupProblem.NOT_THIS_APP)
    }

    @Test
    fun countsThatDoNotMatchAreRefused() = runBlocking {
        fillA()
        val (zip, _) = backup()
        assertRefused(rebuild(zip, "counts.zip", editManifest = { setKey(it, "count.sales", "99") }), BackupProblem.COUNTS_MISMATCH)
        assertRefused(rebuild(zip, "nocount.zip", editManifest = { setKey(it, "count.expenses", null) }), BackupProblem.COUNTS_MISMATCH)
    }

    @Test
    fun noFreeSpaceIsRefusedForBackupAndRestore() = runBlocking {
        fillA()
        val (zip, _) = backup()
        val full = engine(space = { 0L })
        assertEquals(CreateResult.Failed(BackupProblem.NO_SPACE), full.createBackup())
        assertRefused(zip, BackupProblem.NO_SPACE, full)
        val (copied, problem) = full.copyIncoming(FileInputStream(zip), zip.length())
        assertNull(copied)
        assertEquals(BackupProblem.NO_SPACE, problem)
    }

    @Test
    fun copyingThePickedFileStopsAtTheLimit() = runBlocking {
        fillA()
        val (zip, _) = backup()
        val (ok, noProblem) = engine().copyIncoming(FileInputStream(zip), null)
        assertNotNull(ok)
        assertNull(noProblem)
        assertTrue(ok!!.readBytes().contentEquals(zip.readBytes()))
        val small = engine(limits = BackupLimits(maxBackupBytes = 1_000))
        // The size is unknown (null) and the stream is longer than the limit: stopped while copying.
        assertEquals(null to BackupProblem.FILE_TOO_LARGE, small.copyIncoming(FileInputStream(zip), null))
        assertEquals(null to BackupProblem.FILE_TOO_LARGE, small.copyIncoming(FileInputStream(zip), zip.length()))
    }

    // =====================================================================================
    // The swap, stopped at every step, then the next start
    // =====================================================================================

    @Test
    fun aRestoreStoppedAtAnyStepGivesTheOldOrTheNewDataNeverAMix() = runBlocking {
        val old = RecoveryOutcome.NOT_FINISHED_NOTHING_CHANGED
        val cases = listOf(
            // step the crash happens after -> (what the next start reports, true = the backup's data A)
            BackupStep.OLD_SAFETY_CLEARED to (RecoveryOutcome.NOTHING to false),
            BackupStep.MARKER_PREPARED to (old to false),
            BackupStep.LIVE_CLOSED to (old to false),
            BackupStep.LIVE_WAL_MOVED to (old to false),
            BackupStep.LIVE_DB_MOVED to (old to false),
            BackupStep.MARKER_SAFETY to (old to false),
            BackupStep.PREPARED_MOVED_IN to (old to false),
            BackupStep.MARKER_SWAPPED to (RecoveryOutcome.FINISHED to true),
            BackupStep.CLEANED_UP to (RecoveryOutcome.NOTHING to true)
        )
        // Every step of the swap is tested.
        assertEquals(BackupStep.entries.filter { it.ordinal >= BackupStep.OLD_SAFETY_CLEARED.ordinal }, cases.map { it.first })

        for ((step, expected) in cases) {
            cleanAll()
            fillA()
            val a = snapshot()
            val (zip, _) = backup()
            changeToB()
            val b = snapshot()

            val crashing = engine(fault = { if (it == step) throw SimulatedCrash() })
            val ready = crashing.prepareRestore(zip) as PrepareResult.Ready
            try {
                crashing.restore(ready.restore)
                fail("$step: the crash did not happen")
            } catch (expectedCrash: SimulatedCrash) {
                // the battery died here
            }

            // The next start: the app process is new, so nothing is open; the check runs first.
            closeLive()
            val outcome = BackupEngine.recover(crashing.paths)
            assertEquals("$step outcome", expected.first, outcome)
            assertEquals("$step data", if (expected.second) a else b, snapshot())
            assertLiveIsHealthy()
            assertFalse("$step marker", crashing.paths.marker.exists())
            assertFalse("$step prepared", crashing.paths.prepared.exists())
            assertFalse("$step work folder", crashing.paths.workDir.exists())
            // A second start finds nothing more to do.
            closeLive()
            assertEquals(RecoveryOutcome.NOTHING, BackupEngine.recover(crashing.paths))
            assertEquals("$step data after a second start", if (expected.second) a else b, snapshot())
        }
    }

    @Test
    fun anUnreadableMarkerIsTreatedAsTheEarliestStage() = runBlocking {
        fillA()
        val before = snapshot()
        closeLive()
        val paths = engine().paths
        workRoot.mkdirs()
        paths.marker.writeText("%%%")
        assertEquals(RecoveryOutcome.NOT_FINISHED_NOTHING_CHANGED, BackupEngine.recover(paths))
        assertEquals(before, snapshot())
        assertFalse(paths.marker.exists())
    }

    @Test
    fun aStartWithNoRestoreRunningChangesNothing() = runBlocking {
        fillA()
        val before = snapshot()
        closeLive()
        assertEquals(RecoveryOutcome.NOTHING, BackupEngine.recover(engine().paths))
        assertEquals(before, snapshot())
    }

    @Test
    fun theOnlyUsefulOutputOfAFailedBackupIsNothing() = runBlocking {
        // A crash while making a backup leaves the live data alone and no half zip in the work folder.
        fillA()
        val before = snapshot()
        val crashing = engine(fault = { if (it == BackupStep.SNAPSHOT_COPIED) throw SimulatedCrash() })
        try {
            crashing.createBackup()
            fail("no crash")
        } catch (expected: SimulatedCrash) {
        }
        assertFalse(File(crashing.paths.workDir, "create").exists())
        assertFalse(crashing.isBusy)
        assertEquals(before, snapshot())
        // The next backup works.
        val (zip, _) = backup()
        assertTrue(zip.length() > 0)
        val bytes = ByteArrayOutputStream().also { out -> FileInputStream(zip).use { it.copyTo(out) } }.toByteArray()
        assertEquals(zip.length(), bytes.size.toLong())
    }
}

package com.example.dukanhisabnext.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phone test: a version 1 database (what the app had before বিক্রি বাতিল) is upgraded to version 2.
 *
 * The old database is built from the SAVED schema file app/schemas/.../1.json (packed into the test
 * APK), not from today's code, and filled with shop data. Then it is opened with the real
 * AppDatabase + migrations, exactly like the app does after an update. While opening, Room runs
 * MIGRATION_1_2 and checks every table, column, index and foreign key against version 2; if
 * anything is different it refuses to open. It uses its own file ("migration-test"), never the
 * real shop database.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clean() {
        context.deleteDatabase(testDb)
    }

    @After
    fun cleanAfter() {
        context.deleteDatabase(testDb)
    }

    /** Builds an empty version 1 database from 1.json: every table, every index and Room's own table. */
    private fun createVersion1Schema(): SQLiteDatabase {
        val text = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.dukanhisabnext.data.AppDatabase/1.json")
            .bufferedReader().use { it.readText() }
        val schema = JSONObject(text).getJSONObject("database")
        assertEquals(1, schema.getInt("version"))

        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(testDb), null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        // Room's own bookkeeping table with the version 1 identity hash.
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = 1
        return db
    }

    /** A small shop in version 1: a customer, a product with stock, a sale with an item and a ৳200 due. */
    private fun createVersion1WithData() {
        createVersion1Schema().apply {
            assertFalse("v1 must not have the new table", hasTable(this, "sale_cancellations"))
            execSQL("INSERT INTO customers (id, name, phone, createdAt, updatedAt) VALUES (1, 'করিম', '01711111111', 1, 1)")
            execSQL(
                "INSERT INTO products (id, name, unit, sellPrice, costPrice, isActive, createdAt, updatedAt) " +
                    "VALUES (1, 'চাল', 'KG', 10000, 8000, 1, 1, 1)"
            )
            execSQL("INSERT INTO stock_movements (id, productId, type, quantity, createdAt) VALUES (1, 1, 'OPENING', 10000, 1)")
            execSQL(
                "INSERT INTO sales (id, receiptNo, customerId, subtotal, discount, deliveryCharge, total, " +
                    "paidAmount, paymentMethod, dueAmount, createdAt) " +
                    "VALUES (1, '261109-001', 1, 30000, 0, 0, 30000, 10000, 'CASH', 20000, 2)"
            )
            execSQL(
                "INSERT INTO sale_items (id, saleId, productId, productNameSnapshot, quantity, unitPrice, costPriceSnapshot, lineTotal) " +
                    "VALUES (1, 1, 1, 'চাল', 3000, 10000, 8000, 30000)"
            )
            execSQL(
                "INSERT INTO stock_movements (id, productId, type, quantity, referenceSaleId, createdAt) " +
                    "VALUES (2, 1, 'SALE', -3000, 1, 2)"
            )
            execSQL(
                "INSERT INTO ledger_entries (id, customerId, type, amount, saleId, entryDate, createdAt) " +
                    "VALUES (1, 1, 'SALE_DUE', 20000, 1, 2, 2)"
            )
            close()
        }
    }

    /** Opens the file the same way the app does: the real database class plus the migrations. */
    private fun openUpgraded(vararg migrations: Migration = ALL_MIGRATIONS): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, testDb).addMigrations(*migrations).build()

    private fun hasTable(db: SQLiteDatabase, name: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use { it.moveToFirst() }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    /** All rows of a query as text, so old and new values can be compared in one line. */
    private fun SupportSQLiteDatabase.rows(sql: String): List<List<String?>> =
        query(sql).use { c ->
            buildList {
                while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) })
            }
        }

    @Test
    fun version1To2KeepsAllDataAndTheNewTableWorks() {
        createVersion1WithData()

        val room = openUpgraded()
        try {
            // Opening runs the migration and Room's full schema check against version 2.
            val db = room.openHelper.writableDatabase
            assertEquals(2, db.version)

            // Every old row is still there, with the same values.
            assertEquals(
                listOf(listOf("1", "করিম", "01711111111", null, null, "1", "1")),
                db.rows("SELECT id, name, phone, address, note, createdAt, updatedAt FROM customers")
            )
            assertEquals(
                listOf(listOf("1", "চাল", null, "KG", "10000", "8000", null, "1", "1", "1")),
                db.rows("SELECT id, name, categoryId, unit, sellPrice, costPrice, lowStockLevel, isActive, createdAt, updatedAt FROM products")
            )
            assertEquals(
                listOf(listOf("1", "261109-001", "1", "30000", "0", "0", "30000", "10000", "CASH", "20000", null, "2")),
                db.rows(
                    "SELECT id, receiptNo, customerId, subtotal, discount, deliveryCharge, total, paidAmount, " +
                        "paymentMethod, dueAmount, note, createdAt FROM sales"
                )
            )
            assertEquals(
                listOf(listOf("1", "1", "1", "চাল", "3000", "10000", "8000", "30000")),
                db.rows("SELECT id, saleId, productId, productNameSnapshot, quantity, unitPrice, costPriceSnapshot, lineTotal FROM sale_items")
            )
            assertEquals(
                listOf(
                    listOf("1", "1", "OPENING", "10000", null, null, null, "1"),
                    listOf("2", "1", "SALE", "-3000", null, "1", null, "2")
                ),
                db.rows("SELECT id, productId, type, quantity, unitCost, referenceSaleId, note, createdAt FROM stock_movements ORDER BY id")
            )
            assertEquals(
                listOf(listOf("1", "1", "SALE_DUE", "20000", null, "1", null, "2", "2")),
                db.rows("SELECT id, customerId, type, amount, paymentMethod, saleId, note, entryDate, createdAt FROM ledger_entries")
            )

            // The new table exists (empty) and its index on saleId is UNIQUE.
            assertEquals(0, db.count("sale_cancellations"))
            val uniqueFlag = db.rows("PRAGMA index_list(`sale_cancellations`)")
                .firstOrNull { it[1] == "index_sale_cancellations_saleId" }
            assertTrue("unique index on saleId is missing", uniqueFlag != null)
            assertEquals("1", uniqueFlag!![2]) // column 2 of index_list = "unique"

            // The new table works, and allows only one cancellation per sale.
            db.execSQL("INSERT INTO sale_cancellations (saleId, reason, createdAt) VALUES (1, NULL, 3)")
            try {
                db.execSQL("INSERT INTO sale_cancellations (saleId, reason, createdAt) VALUES (1, 'again', 4)")
                fail("A second cancellation for the same sale must be refused")
            } catch (expected: SQLiteConstraintException) {
                // good
            }
            assertEquals(1, db.count("sale_cancellations"))
        } finally {
            room.close()
        }
    }

    @Test
    fun anOldSaleCanBeCancelledAfterTheUpgrade() = runBlocking {
        createVersion1WithData()

        val room = openUpgraded()
        try {
            // The DAOs read the old data correctly: 10 kg - 3 kg sold, ৳200 due.
            assertEquals(7_000L, room.stockMovementDao().getStock(1))
            assertEquals(20_000L, room.ledgerDao().getBalance(1))

            val result = SaleRepository(room, { 5L }).cancelSale(1, null)

            assertTrue("expected Cancelled, got $result", result is CancelResult.Cancelled)
            assertEquals(10_000L, room.stockMovementDao().getStock(1)) // stock came back
            assertEquals(0L, room.ledgerDao().getBalance(1))            // the due was reversed
            // The old sale row itself is unchanged.
            assertEquals(20_000L, room.saleDao().getSale(1)!!.dueAmount)
        } finally {
            room.close()
        }
    }

    @Test
    fun aMigrationThatForgetsTheNewTableIsRefused() {
        // Proves the schema check is real: a "migration" that does nothing must fail to open.
        createVersion1WithData()
        val doesNothing = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }

        val room = openUpgraded(doesNothing)
        try {
            room.openHelper.writableDatabase
            fail("Room must refuse a version 2 database without the sale_cancellations table")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty(), expected.message.orEmpty().contains("sale_cancellations"))
        } finally {
            room.close()
        }
    }
}

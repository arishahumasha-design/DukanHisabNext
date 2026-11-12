package com.example.dukanhisabnext.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dukanhisabnext.expense.DefaultCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phone test: an OLD database file is upgraded to the newest version, the way the app does after
 * an update. Your real phone still has a version 1 file and the emulator a version 2 file, so both
 * paths are tested: 1 -> 2 -> 3 in one go, and 2 -> 3.
 *
 * The old database is built from the SAVED schema file (app/schemas/.../N.json, packed into the test
 * APK), not from today's code, and filled with shop data. Then it is opened with the real
 * AppDatabase + migrations. While opening, Room runs the migrations and checks every table, column,
 * index and foreign key against the newest version; if anything is different it refuses to open.
 * It uses its own file ("migration-test"), never the real shop database.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private val defaultNames = listOf("বেতন", "ভাড়া", "বিল", "চা-নাস্তা", "যাতায়াত", "পরিবার", "অন্যান্য")
    private fun nameOf(d: DefaultCategory) = defaultNames[d.ordinal]

    @Before
    fun clean() {
        context.deleteDatabase(testDb)
    }

    @After
    fun cleanAfter() {
        context.deleteDatabase(testDb)
    }

    /** Builds an empty database of [version] from its schema file: every table, every index and Room's own table. */
    private fun createSchema(version: Int): SQLiteDatabase {
        val text = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.dukanhisabnext.data.AppDatabase/$version.json")
            .bufferedReader().use { it.readText() }
        val schema = JSONObject(text).getJSONObject("database")
        assertEquals(version, schema.getInt("version"))

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
        // Room's own bookkeeping table with this version's identity hash.
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = version
        return db
    }

    /** The shop data that exists in every version: a customer, a product with stock, a sale with an item and a ৳200 due. */
    private fun SQLiteDatabase.insertShopData() {
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
        // Two expense categories made before version 3 (one has the name of a default) and three expenses (one deleted).
        execSQL("INSERT INTO expense_categories (id, name) VALUES (1, 'ভাড়া')")
        execSQL("INSERT INTO expense_categories (id, name) VALUES (2, 'মেরামত')")
        execSQL(
            "INSERT INTO expenses (id, expenseCategoryId, amount, reason, expenseDate, isDeleted, createdAt, updatedAt) " +
                "VALUES (1, 1, 500000, 'দোকান ভাড়া', 3, 0, 3, 3)"
        )
        execSQL(
            "INSERT INTO expenses (id, expenseCategoryId, amount, reason, expenseDate, isDeleted, createdAt, updatedAt) " +
                "VALUES (2, 2, 15000, NULL, 4, 0, 4, 4)"
        )
        execSQL(
            "INSERT INTO expenses (id, expenseCategoryId, amount, reason, expenseDate, isDeleted, createdAt, updatedAt) " +
                "VALUES (3, 2, 99900, 'ভুল', 5, 1, 5, 6)"
        )
    }

    /** Opens the file the same way the app does: the real database class plus the migrations. */
    private fun openUpgraded(vararg migrations: Migration = ALL_MIGRATIONS): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, testDb).addMigrations(*migrations).build()

    // ---- Comparing every row before and after ----

    private val tablesWithData = listOf(
        "customers", "products", "stock_movements", "sales", "sale_items", "ledger_entries",
        "expense_categories", "expenses"
    )

    private fun columnsOf(db: SQLiteDatabase, table: String): List<String> =
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }

    private fun Cursor.allRows(): List<List<String?>> = use { c ->
        buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
    }

    /** Every row of every table, only the columns the OLD version had, by id. */
    private fun snapshot(db: SQLiteDatabase): Map<String, Pair<List<String>, List<List<String?>>>> =
        tablesWithData.associateWith { table ->
            val columns = columnsOf(db, table)
            val sql = "SELECT ${columns.joinToString { "`$it`" }} FROM `$table` ORDER BY id"
            columns to db.rawQuery(sql, null).allRows()
        }

    private fun assertSameRows(before: Map<String, Pair<List<String>, List<List<String?>>>>, after: SupportSQLiteDatabase) {
        for ((table, pair) in before) {
            val (columns, rows) = pair
            assertTrue("$table should have data in this test", rows.isNotEmpty())
            val now = after.query("SELECT ${columns.joinToString { "`$it`" }} FROM `$table` ORDER BY id").allRows()
            assertEquals("rows of $table", rows, now)
        }
    }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun SupportSQLiteDatabase.indexIsUnique(table: String, index: String): Boolean? =
        query("PRAGMA index_list(`$table`)").use { c ->
            while (c.moveToNext()) {
                if (c.getString(c.getColumnIndexOrThrow("name")) == index) return@use c.getInt(c.getColumnIndexOrThrow("unique")) == 1
            }
            null
        }

    // =====================================================================================
    // 1 -> 2 -> 3 in one go (your real phone)
    // =====================================================================================

    @Test
    fun version1To3InOneGoKeepsEveryRowAndEverythingNewWorks() = runBlocking {
        val before = createSchema(1).let { db ->
            db.insertShopData()
            assertFalse("v1 has no sale_cancellations", hasTable(db, "sale_cancellations"))
            snapshot(db).also { db.close() }
        }

        val room = openUpgraded()
        try {
            // Opening runs 1 -> 2 -> 3 and Room's full check against version 3.
            val db = room.openHelper.writableDatabase
            assertEquals(3, db.version)
            assertSameRows(before, db)

            // The new columns have sane defaults on the old categories.
            db.query("SELECT id, isPersonal, isActive, systemKey FROM expense_categories ORDER BY id").use {
                while (it.moveToNext()) {
                    assertEquals(0, it.getInt(1))  // a shop cost
                    assertEquals(1, it.getInt(2))  // shown
                    assertTrue(it.isNull(3))       // not a default (yet)
                }
            }

            // The indexes from both migrations exist, the right ones unique.
            assertEquals(true, db.indexIsUnique("sale_cancellations", "index_sale_cancellations_saleId"))
            assertEquals(true, db.indexIsUnique("expense_categories", "index_expense_categories_systemKey"))
            assertEquals(false, db.indexIsUnique("expenses", "index_expenses_expenseDate"))

            // The defaults are made once, also when asked twice. "ভাড়া" (made before v3) is adopted, not doubled.
            val repo = ExpenseRepository(room, { 10L })
            repo.ensureDefaultCategories(::nameOf)
            repo.ensureDefaultCategories(::nameOf)
            val categories = room.expenseDao().getCategories()
            assertEquals(2 + 6, categories.size) // the 2 old ones + 6 new defaults
            assertEquals(1, categories.count { it.name == "ভাড়া" })
            assertEquals("RENT", categories.single { it.id == 1L }.systemKey)
            assertNull(categories.single { it.id == 2L }.systemKey) // মেরামত stays the user's own
            assertEquals(DefaultCategory.entries.map { it.name }.toSet(), categories.mapNotNull { it.systemKey }.toSet())
            assertTrue(categories.single { it.systemKey == "FAMILY" }.isPersonal)

            // The unique indexes work: a second category with the same systemKey, a second cancellation.
            assertConstraint { db.execSQL("INSERT INTO expense_categories (name, isPersonal, isActive, systemKey) VALUES ('x', 0, 1, 'RENT')") }
            db.execSQL("INSERT INTO sale_cancellations (saleId, reason, createdAt) VALUES (1, NULL, 7)")
            assertConstraint { db.execSQL("INSERT INTO sale_cancellations (saleId, reason, createdAt) VALUES (1, 'again', 8)") }

            // The old expenses read correctly; the deleted one stays hidden.
            val all = DhakaRangeAll
            assertEquals(2, room.expenseDao().observeRowsInRange(all.first, all.second).first().size)
            assertEquals(515_000L, room.expenseDao().observeTotalInRange(all.first, all.second).first())
        } finally {
            room.close()
        }
    }

    @Test
    fun anOldSaleCanBeCancelledAfterUpgradingFromVersion1() = runBlocking {
        createSchema(1).apply { insertShopData(); close() }

        val room = openUpgraded()
        try {
            assertEquals(7_000L, room.stockMovementDao().getStock(1)) // 10 kg - 3 kg sold
            assertEquals(20_000L, room.ledgerDao().getBalance(1))

            val result = SaleRepository(room, { 5L }).cancelSale(1, null)

            assertTrue("expected Cancelled, got $result", result is CancelResult.Cancelled)
            assertEquals(10_000L, room.stockMovementDao().getStock(1))
            assertEquals(0L, room.ledgerDao().getBalance(1))
            assertEquals(20_000L, room.saleDao().getSale(1)!!.dueAmount) // the sale row is unchanged
        } finally {
            room.close()
        }
    }

    // =====================================================================================
    // 2 -> 3 (the emulator)
    // =====================================================================================

    @Test
    fun version2To3KeepsEveryRowAndAddsTheExpenseColumns() = runBlocking {
        val before = createSchema(2).let { db ->
            db.insertShopData()
            db.execSQL("INSERT INTO sale_cancellations (id, saleId, reason, createdAt) VALUES (1, 1, 'ভুল', 6)")
            snapshot(db).also { db.close() }
        }

        val room = openUpgraded()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(3, db.version)
            assertSameRows(before, db)
            assertEquals(1, db.count("sale_cancellations"))

            db.query("SELECT isPersonal, isActive, systemKey FROM expense_categories").use {
                while (it.moveToNext()) {
                    assertEquals(0, it.getInt(0))
                    assertEquals(1, it.getInt(1))
                    assertTrue(it.isNull(2))
                }
            }
            assertEquals(true, db.indexIsUnique("expense_categories", "index_expense_categories_systemKey"))
            assertEquals(false, db.indexIsUnique("expenses", "index_expenses_expenseDate"))

            // Many user categories may have no systemKey (NULLs do not clash in a unique index).
            db.execSQL("INSERT INTO expense_categories (name, isPersonal, isActive, systemKey) VALUES ('ক', 0, 1, NULL)")
            db.execSQL("INSERT INTO expense_categories (name, isPersonal, isActive, systemKey) VALUES ('খ', 0, 1, NULL)")

            val repo = ExpenseRepository(room, { 10L })
            repo.ensureDefaultCategories(::nameOf)
            repo.ensureDefaultCategories(::nameOf)
            val categories = room.expenseDao().getCategories()
            assertEquals(DefaultCategory.entries.size, categories.count { it.systemKey != null })
            assertEquals(1, categories.count { it.name == "ভাড়া" })
        } finally {
            room.close()
        }
    }

    @Test
    fun aMigrationThatForgetsTheNewTableIsRefused() {
        // Proves the schema check is real: a 1 -> 2 "migration" that does nothing must fail to open.
        createSchema(1).apply { insertShopData(); close() }
        val doesNothing = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }

        val room = openUpgraded(doesNothing, MIGRATION_2_3)
        try {
            room.openHelper.writableDatabase
            fail("Room must refuse a database without the sale_cancellations table")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty(), expected.message.orEmpty().contains("sale_cancellations"))
        } finally {
            room.close()
        }
    }

    @Test
    fun a2To3MigrationThatForgetsAColumnIsRefused() {
        createSchema(2).apply { insertShopData(); close() }
        val forgetsSystemKey = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expense_categories` ADD COLUMN `isPersonal` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `expense_categories` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_expenseDate` ON `expenses` (`expenseDate`)")
            }
        }

        val room = openUpgraded(MIGRATION_1_2, forgetsSystemKey)
        try {
            room.openHelper.writableDatabase
            fail("Room must refuse a version 3 database without the systemKey column")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty(), expected.message.orEmpty().contains("expense_categories"))
        } finally {
            room.close()
        }
    }

    // ---- small helpers ----

    private fun hasTable(db: SQLiteDatabase, name: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use { it.moveToFirst() }

    private fun assertConstraint(block: () -> Unit) {
        try {
            block()
            fail("Expected the database to refuse this")
        } catch (expected: SQLiteConstraintException) {
            // good
        }
    }

    /** Every moment (start included, end excluded). */
    private val DhakaRangeAll = 0L to Long.MAX_VALUE
}

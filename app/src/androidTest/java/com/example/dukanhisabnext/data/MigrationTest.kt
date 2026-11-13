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
import com.example.dukanhisabnext.testing.IsolatedContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
 * an update. A phone may hold a version 1, 2 or 3 file, so every path is tested: 1 -> 2 -> 3 -> 4 in
 * one go, 2 -> 3 -> 4, and 3 -> 4.
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
    // Every file of this test (its database "migration-test") lives in an isolated context of the test
    // package, never in the app's real databases folder.
    private val isolated = IsolatedContext.create(InstrumentationRegistry.getInstrumentation().targetContext, "migration")
    private val context: Context = isolated

    private val defaultNames = listOf("বেতন", "ভাড়া", "বিল", "চা-নাস্তা", "যাতায়াত", "পরিবার", "অন্যান্য")
    private fun nameOf(d: DefaultCategory) = defaultNames[d.ordinal]

    @Before
    fun clean() {
        context.deleteDatabase(testDb)
    }

    @After
    fun cleanAfter() {
        context.deleteDatabase(testDb)
        isolated.cleanUp()
    }

    /** An empty database of [version] built from its saved schema file (shared helper in SchemaFiles.kt). */
    private fun createSchema(version: Int): SQLiteDatabase = createDatabaseFromSchema(context.getDatabasePath(testDb), version)

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
    // 1 -> 2 -> 3 -> 4 in one go (your real phone)
    // =====================================================================================

    @Test
    fun version1To4InOneGoKeepsEveryRowAndEverythingNewWorks() = runBlocking {
        val before = createSchema(1).let { db ->
            db.insertShopData()
            assertFalse("v1 has no sale_cancellations", hasTable(db, "sale_cancellations"))
            snapshot(db).also { db.close() }
        }

        val room = openUpgraded()
        try {
            // Opening runs 1 -> 2 -> 3 -> 4 -> 5 and Room's full check against version 5.
            val db = room.openHelper.writableDatabase
            assertEquals(5, db.version)
            assertSameRows(before, db)
            assertEquals(false, db.indexIsUnique("stock_movements", STOCK_INDEX))

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
    // 2 -> 3 -> 4
    // =====================================================================================

    @Test
    fun version2To4KeepsEveryRowAndAddsTheExpenseColumns() = runBlocking {
        val before = createSchema(2).let { db ->
            db.insertShopData()
            db.execSQL("INSERT INTO sale_cancellations (id, saleId, reason, createdAt) VALUES (1, 1, 'ভুল', 6)")
            snapshot(db).also { db.close() }
        }

        val room = openUpgraded()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(5, db.version)
            assertSameRows(before, db)
            assertEquals(false, db.indexIsUnique("stock_movements", STOCK_INDEX))
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

        val room = openUpgraded(doesNothing, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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

        val room = openUpgraded(MIGRATION_1_2, forgetsSystemKey, MIGRATION_3_4, MIGRATION_4_5)
        try {
            room.openHelper.writableDatabase
            fail("Room must refuse a database without the systemKey column")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty(), expected.message.orEmpty().contains("expense_categories"))
        } finally {
            room.close()
        }
    }

    // =====================================================================================
    // 3 -> 4 (M9: the stock index)
    // =====================================================================================

    @Test
    fun version3To4KeepsEveryRowAndAddsTheCoveringStockIndex() = runBlocking {
        val before = createSchema(3).let { db ->
            db.insertShopData()
            db.execSQL("INSERT INTO sale_cancellations (id, saleId, reason, createdAt) VALUES (1, 1, 'ভুল', 6)")
            assertFalse("v3 has no stock index yet", hasIndex(db, STOCK_INDEX))
            snapshot(db).also { db.close() }
        }

        val room = openUpgraded()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(5, db.version)
            assertSameRows(before, db)
            assertEquals(1, db.count("sale_cancellations"))
            assertEquals(false, db.indexIsUnique("stock_movements", STOCK_INDEX))
            // The old productId index is still there too.
            assertEquals(false, db.indexIsUnique("stock_movements", "index_stock_movements_productId"))

            // The stock is the same number, and SQLite now reads it from the covering index.
            assertEquals(7_000L, room.stockMovementDao().getStock(1))
            val plan = db.query(
                "EXPLAIN QUERY PLAN SELECT p.id, COALESCE((SELECT SUM(m.quantity) FROM stock_movements m WHERE m.productId = p.id), 0) FROM products p"
            ).use { c -> buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("detail"))) } }
            assertTrue(plan.toString(), plan.any { it.contains("COVERING INDEX $STOCK_INDEX") })
        } finally {
            room.close()
        }
    }

    @Test
    fun a3To4MigrationThatForgetsTheIndexIsRefused() {
        // Proves Room checks indexes too: a 3 -> 4 "migration" that does nothing must fail to open.
        createSchema(3).apply { insertShopData(); close() }
        val doesNothing = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }

        val room = openUpgraded(MIGRATION_1_2, MIGRATION_2_3, doesNothing, MIGRATION_4_5)
        try {
            room.openHelper.writableDatabase
            fail("Room must refuse a version 4 database without the stock index")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty(), expected.message.orEmpty().contains("stock_movements"))
        } finally {
            room.close()
        }
    }

    // =====================================================================================
    // 4 -> 5 (sale date and warranty)
    // =====================================================================================

    @Test
    fun version4To5KeepsEveryRowAndEveryOldSaleKeepsItsDate() = runBlocking {
        // A real-looking 13 Nov 2026 sale (12:23 Dhaka) next to the shared shop data, and a cancelled one.
        val nov13 = 1_763_015_000_000L
        val before = createSchema(4).let { db ->
            db.insertShopData()
            db.execSQL(
                "INSERT INTO sales (id, receiptNo, customerId, subtotal, discount, deliveryCharge, total, " +
                    "paidAmount, paymentMethod, dueAmount, createdAt) VALUES (2, '261113-003', NULL, 9000, 0, 0, 9000, 9000, 'CASH', 0, $nov13)"
            )
            db.execSQL(
                "INSERT INTO sale_items (id, saleId, productId, productNameSnapshot, quantity, unitPrice, costPriceSnapshot, lineTotal) " +
                    "VALUES (2, 2, 1, 'চাল', 1000, 9000, NULL, 9000)"
            )
            db.execSQL("INSERT INTO sale_cancellations (id, saleId, reason, createdAt) VALUES (1, 1, 'ভুল', 6)")
            assertFalse("v4 has no sale date index", hasIndex(db, SALE_DATE_INDEX))
            snapshot(db).also { db.close() }
        }

        val room = openUpgraded()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(5, db.version)
            assertSameRows(before, db) // every old column of every row is unchanged

            // Every old sale got its own createdAt as its sale date; none is left at the default 0.
            db.query("SELECT id, createdAt, saleDate, receiptNo FROM sales ORDER BY id").use {
                val rows = buildList { while (it.moveToNext()) add(listOf(it.getLong(0), it.getLong(1), it.getLong(2))) }
                assertEquals(listOf(listOf(1L, 2L, 2L), listOf(2L, nov13, nov13)), rows)
            }
            assertEquals(0, db.query("SELECT COUNT(*) FROM sales WHERE saleDate = 0 AND createdAt != 0").use { it.moveToFirst(); it.getInt(0) })

            // No warranty anywhere yet.
            assertEquals(0, db.query("SELECT COUNT(*) FROM products WHERE warrantyValue IS NOT NULL OR warrantyUnit IS NOT NULL").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(
                0,
                db.query("SELECT COUNT(*) FROM sale_items WHERE warrantyValue IS NOT NULL OR warrantyUnit IS NOT NULL OR warrantyUntil IS NOT NULL")
                    .use { it.moveToFirst(); it.getInt(0) }
            )
            assertEquals(false, db.indexIsUnique("sales", SALE_DATE_INDEX))

            // The lists and reports read the old sales on their old days.
            val (start, end) = com.example.dukanhisabnext.util.DhakaTime.today(nov13).let { it.startMillis to it.endMillisExclusive }
            val row = room.saleDao().observeList(start, end).first().single()
            assertEquals("261113-003", row.receiptNo)
            assertEquals(nov13, row.saleDate)
            assertEquals(9_000L, room.reportDao().observeSalesReport(start, end).first().total)
            // Stock and balance are the same sums as before (the cancelled sale's due still counts:
            // these rows were written by hand, without the cancel's own reversal entry).
            assertEquals(7_000L, room.stockMovementDao().getStock(1))
            assertEquals(20_000L, room.ledgerDao().getBalance(1))

            // A new sale on 9 Nov 2026 continues that day's numbering after the old 261109-001.
            val nov9Noon = java.time.Instant.parse("2026-11-09T06:00:00Z").toEpochMilli()
            val saved = SaleRepository(room, { nov9Noon }).completeSale(
                SaleDraft("after-upgrade", listOf(SaleDraftLine(1, 1_000, 10_000)), 0, 0, 10_000, com.example.dukanhisabnext.data.entity.PaymentMethod.CASH, null)
            ) as SaleResult.Saved
            assertEquals("261109-002", saved.receiptNo)
        } finally {
            room.close()
        }
    }

    @Test
    fun a4To5MigrationThatForgetsTheSaleDateIsRefused() {
        createSchema(4).apply { insertShopData(); close() }
        val forgetsSaleDate = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `products` ADD COLUMN `warrantyValue` INTEGER")
                db.execSQL("ALTER TABLE `products` ADD COLUMN `warrantyUnit` TEXT")
                db.execSQL("ALTER TABLE `sale_items` ADD COLUMN `warrantyValue` INTEGER")
                db.execSQL("ALTER TABLE `sale_items` ADD COLUMN `warrantyUnit` TEXT")
                db.execSQL("ALTER TABLE `sale_items` ADD COLUMN `warrantyUntil` INTEGER")
            }
        }
        val room = openUpgraded(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, forgetsSaleDate)
        try {
            room.openHelper.writableDatabase
            fail("Room must refuse a version 5 database without sales.saleDate")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty(), expected.message.orEmpty().contains("sales"))
        } finally {
            room.close()
        }
    }

    // ---- small helpers ----

    private fun hasIndex(db: SQLiteDatabase, name: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf(name)).use { it.moveToFirst() }

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

/** The covering index added in version 4. */
private const val STOCK_INDEX = "index_stock_movements_productId_quantity"

/** The sale date index added in version 5. */
private const val SALE_DATE_INDEX = "index_sales_saleDate"

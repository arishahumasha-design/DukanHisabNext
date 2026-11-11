package com.example.dukanhisabnext.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phone test: a version 1 database (what the app had before বিক্রি বাতিল) is upgraded to version 2
 * and every row is still there. The helper builds the old database from app/schemas/.../1.json in
 * a separate test file ("migration-test"), never the real shop database.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    /** A small shop in version 1: one customer, one product, one sale with a ৳200 due. */
    private fun createVersion1() {
        helper.createDatabase(testDb, 1).apply {
            execSQL("INSERT INTO customers (id, name, phone, createdAt, updatedAt) VALUES (1, 'করিম', '01711111111', 1, 1)")
            execSQL(
                "INSERT INTO products (id, name, unit, sellPrice, costPrice, isActive, createdAt, updatedAt) " +
                    "VALUES (1, 'চাল', 'KG', 10000, 8000, 1, 1, 1)"
            )
            execSQL("INSERT INTO stock_movements (productId, type, quantity, createdAt) VALUES (1, 'OPENING', 10000, 1)")
            execSQL(
                "INSERT INTO sales (id, receiptNo, customerId, subtotal, discount, deliveryCharge, total, " +
                    "paidAmount, paymentMethod, dueAmount, createdAt) " +
                    "VALUES (1, '261109-001', 1, 30000, 0, 0, 30000, 10000, 'CASH', 20000, 2)"
            )
            execSQL(
                "INSERT INTO sale_items (saleId, productId, productNameSnapshot, quantity, unitPrice, costPriceSnapshot, lineTotal) " +
                    "VALUES (1, 1, 'চাল', 3000, 10000, 8000, 30000)"
            )
            execSQL("INSERT INTO stock_movements (productId, type, quantity, referenceSaleId, createdAt) VALUES (1, 'SALE', -3000, 1, 2)")
            execSQL(
                "INSERT INTO ledger_entries (customerId, type, amount, saleId, entryDate, createdAt) " +
                    "VALUES (1, 'SALE_DUE', 20000, 1, 2, 2)"
            )
            close()
        }
    }

    @Test
    fun version1To2KeepsAllDataAndMatchesTheSchema() {
        createVersion1()

        // Runs MIGRATION_1_2 and checks the result is exactly the version 2 schema (2.json).
        val db = helper.runMigrationsAndValidate(testDb, 2, true, MIGRATION_1_2)

        fun count(table: String) = db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, count("customers"))
        assertEquals(1, count("products"))
        assertEquals(1, count("sales"))
        assertEquals(1, count("sale_items"))
        assertEquals(2, count("stock_movements"))
        assertEquals(1, count("ledger_entries"))
        assertEquals(0, count("sale_cancellations"))

        db.query("SELECT receiptNo, total, dueAmount FROM sales WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("261109-001", it.getString(0))
            assertEquals(30_000L, it.getLong(1))
            assertEquals(20_000L, it.getLong(2))
        }

        // The new table works, and allows only one cancellation per sale.
        db.execSQL("INSERT INTO sale_cancellations (saleId, reason, createdAt) VALUES (1, NULL, 3)")
        try {
            db.execSQL("INSERT INTO sale_cancellations (saleId, reason, createdAt) VALUES (1, 'again', 4)")
            fail("A second cancellation for the same sale must be refused")
        } catch (expected: SQLiteConstraintException) {
            // good
        }
        db.close()
    }

    @Test
    fun anOldSaleCanBeCancelledAfterTheUpgrade() = runBlocking {
        createVersion1()
        helper.runMigrationsAndValidate(testDb, 2, true, MIGRATION_1_2).close()

        // Open the upgraded file the same way the app does (with the migrations).
        val room = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            testDb
        ).addMigrations(*ALL_MIGRATIONS).build()
        try {
            assertEquals(7_000L, room.stockMovementDao().getStock(1))
            assertEquals(20_000L, room.ledgerDao().getBalance(1))

            val result = SaleRepository(room, { 5L }).cancelSale(1, null)

            assertEquals(CancelResult.Cancelled::class, result::class)
            assertEquals(10_000L, room.stockMovementDao().getStock(1))
            assertEquals(0L, room.ledgerDao().getBalance(1))
        } finally {
            room.close()
        }
    }
}

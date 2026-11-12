package com.example.dukanhisabnext.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals

// Shared by MigrationTest and BackupEngineTest: an OLD database built from its SAVED schema file
// (app/schemas/.../N.json, packed into the test APK), not from today's code.

/** Builds an empty database of [version] at [file] from its schema file: every table, every index and Room's own table. */
fun createDatabaseFromSchema(file: File, version: Int): SQLiteDatabase {
    val text = InstrumentationRegistry.getInstrumentation().context.assets
        .open("com.example.dukanhisabnext.data.AppDatabase/$version.json")
        .bufferedReader().use { it.readText() }
    val schema = JSONObject(text).getJSONObject("database")
    assertEquals(version, schema.getInt("version"))

    val db = SQLiteDatabase.openOrCreateDatabase(file, null)
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

/** The shop data that exists in every version: a customer, a product with stock, a sale with an item and a ৳200 due, expenses. */
fun SQLiteDatabase.insertShopData() {
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

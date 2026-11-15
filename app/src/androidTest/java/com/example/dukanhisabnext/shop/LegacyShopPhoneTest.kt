package com.example.dukanhisabnext.shop

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dukanhisabnext.data.ALL_MIGRATIONS
import com.example.dukanhisabnext.data.AppDatabase
import com.example.dukanhisabnext.data.createDatabaseFromSchema
import com.example.dukanhisabnext.data.insertShopData
import com.example.dukanhisabnext.testing.DisplayDefaultsRule
import com.example.dukanhisabnext.testing.IsolatedContext
import java.io.File
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The update of an existing single-shop install: its database becomes shop 1 WITHOUT being copied, moved or
 * changed. Registering it leaves the file byte-identical, and opening it as shop 1 shows exactly the same data.
 * Everything is in an isolated folder; the database is built from the saved schema file of that version.
 */
@RunWith(AndroidJUnit4::class)
class LegacyShopPhoneTest {
    @get:Rule
    val displayDefaults = DisplayDefaultsRule()

    private val isolated = IsolatedContext.create(InstrumentationRegistry.getInstrumentation().targetContext, "legacy-shop")
    private val databasesDir: File = isolated.getDatabasePath("probe").parentFile!!
    private val store = ShopStore(databasesDir, isolated.filesDir)

    @After
    fun tearDown() = isolated.cleanUp()

    private fun sha(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes())

    /** Every row of every table (Room's own table too), read-only, in a fixed order. */
    private fun dump(file: File): Map<String, List<String>> {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT IN ('android_metadata') ORDER BY name", null)
                .use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }
            return tables.associateWith { table ->
                db.rawQuery("SELECT * FROM `$table`", null).use { c ->
                    generateSequence {
                        if (!c.moveToNext()) null else (0 until c.columnCount).joinToString("|") { c.getString(it) ?: "∅" }
                    }.toList().sorted()
                }
            }
        } finally {
            db.close()
        }
    }

    private fun openAsShop(file: File): AppDatabase =
        Room.databaseBuilder(isolated, AppDatabase::class.java, file.absolutePath).addMigrations(*ALL_MIGRATIONS).build()

    @Test
    fun theOldDatabaseBecomesShopOneByteIdenticalAndWithTheSameData() {
        val legacy = store.paths(LEGACY_SHOP_ID).database
        createDatabaseFromSchema(legacy, 5).apply { insertShopData(); close() }
        val shaBefore = sha(legacy)
        val dataBefore = dump(legacy)
        assertTrue(dataBefore.getValue("sales").isNotEmpty())

        val registry = store.load()
        assertEquals(listOf(LEGACY_SHOP_ID), registry.active.map { it.id })
        assertTrue(registry.selected.opened)
        assertEquals(ShopHealth.OK, store.fileHealth(registry.selected))
        assertArrayEquals("registering never touches the file", shaBefore, sha(legacy))

        // Opened as shop 1, exactly the way the app opens a shop (by its full path).
        val db = openAsShop(legacy)
        val sql = db.openHelper.writableDatabase
        assertEquals(1, sql.query("SELECT COUNT(*) FROM sales").use { c -> c.moveToFirst(); c.getInt(0) })
        db.close()
        assertEquals("the same data after opening it as shop 1", dataBefore, dump(legacy))
    }

    @Test
    fun anOlderDatabaseIsStillConvertedWhenOpenedAsShopOne() {
        val legacy = store.paths(LEGACY_SHOP_ID).database
        createDatabaseFromSchema(legacy, 4).apply { insertShopData(); close() }
        val shaBefore = sha(legacy)
        store.load()
        assertArrayEquals(shaBefore, sha(legacy))
        val db = openAsShop(legacy)
        val sql = db.openHelper.writableDatabase
        assertEquals(5, sql.version)
        for ((table, rows) in mapOf("sales" to 1, "customers" to 1, "products" to 1, "expenses" to 3, "ledger_entries" to 1)) {
            assertEquals(table, rows, sql.query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst(); c.getInt(0) })
        }
        db.close()
    }

    @Test
    fun aSecondShopGetsItsOwnFileNextToTheFirst() {
        val legacy = store.paths(LEGACY_SHOP_ID).database
        createDatabaseFromSchema(legacy, 5).apply { insertShopData(); close() }
        val shaBefore = sha(legacy)
        val second = store.paths(2).database
        val db = openAsShop(second)
        val sql = db.openHelper.writableDatabase
        assertEquals(0, sql.query("SELECT COUNT(*) FROM sales").use { c -> c.moveToFirst(); c.getInt(0) })
        db.close()
        assertEquals(File(File(isolated.filesDir, "shop-databases"), "shop-2.db"), second)
        assertTrue(second.exists())
        assertArrayEquals("making shop 2 never touches shop 1", shaBefore, sha(legacy))
    }
}

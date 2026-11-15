package com.example.dukanhisabnext.shop

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dukanhisabnext.data.AppDatabase
import com.example.dukanhisabnext.data.createDatabaseFromSchema
import com.example.dukanhisabnext.data.insertShopData
import com.example.dukanhisabnext.testing.DisplayDefaultsRule
import com.example.dukanhisabnext.testing.IsolatedContext
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Making, editing, switching and deleting shops on real database files, in an isolated folder. */
@RunWith(AndroidJUnit4::class)
class ShopManagerPhoneTest {
    @get:Rule
    val displayDefaults = DisplayDefaultsRule()

    private val isolated = IsolatedContext.create(InstrumentationRegistry.getInstrumentation().targetContext, "shop-manager")
    private val store = ShopStore(isolated.getDatabasePath("probe").parentFile!!, isolated.filesDir)
    private var busy = false
    private var saving = false
    private var restarts = 0
    private val manager = ShopManager(isolated, store, clock = { 5_000L }, backupBusy = { busy }, saving = { saving }, restart = { restarts++ })

    @After
    fun tearDown() = isolated.cleanUp()

    private fun sha(f: java.io.File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes())

    /** An old install with data, registered as shop 1 and named. */
    private fun oldShop(): ByteArray {
        val legacy = store.paths(LEGACY_SHOP_ID).database
        createDatabaseFromSchema(legacy, 5).apply { insertShopData(); close() }
        store.save(store.load().renamed(LEGACY_SHOP_ID, "Taqwa Tech", null))
        return sha(legacy)
    }

    private fun profileName(id: Int): String? = runBlocking {
        val db = AppDatabase.open(isolated, store.paths(id).database)
        try { db.shopProfileDao().get()?.name } finally { db.close() }
    }

    @Test
    fun aNewShopGetsItsOwnEmptyDatabaseWithItsProfileAndShopOneIsUntouched() = runBlocking {
        val before = oldShop()
        val made = manager.create(ShopDetails("Grocery", "Mirpur 10", "Rahim", "01711111111")) as ShopChange.Done
        assertEquals(2, made.shop.id)
        assertEquals("Grocery", profileName(2))
        val db = AppDatabase.open(isolated, store.paths(2).database)
        try {
            val sql = db.openHelper.readableDatabase
            assertEquals(0, sql.query("SELECT COUNT(*) FROM sales").use { c -> c.moveToFirst(); c.getInt(0) })
            assertEquals("Rahim", db.shopProfileDao().get()?.ownerName)
        } finally {
            db.close()
        }
        assertEquals(listOf(1, 2), store.load().active.map { it.id })
        assertEquals("making a shop does not open it", 1, store.load().selectedId)
        assertArrayEquals(before, sha(store.paths(1).database))
    }

    @Test
    fun aNameThatExistsIsRefusedAndNothingIsMade() = runBlocking {
        oldShop()
        assertEquals(ShopChange.BadName(ShopNameProblem.DUPLICATE), manager.create(ShopDetails(" taqwa  TECH ", null, null, null)))
        assertFalse(store.paths(2).database.exists())
        assertEquals(1, store.load().active.size)
    }

    @Test
    fun anotherShopCanBeRenamedWithoutOpeningIt() = runBlocking {
        oldShop()
        manager.create(ShopDetails("Grocery", null, null, null))
        manager.create(ShopDetails("Plastic", null, null, null))
        assertEquals(ShopChange.BadName(ShopNameProblem.DUPLICATE), manager.edit(3, ShopDetails("grocery", null, null, null)))
        val done = manager.edit(3, ShopDetails("Plastic House", "Gazipur", null, null)) as ShopChange.Done
        assertEquals("Plastic House", done.shop.name)
        assertEquals("Plastic House", profileName(3))
        assertEquals("Gazipur", store.load().find(3)!!.address)
        assertEquals(1, store.load().selectedId)
    }

    @Test
    fun switchingIsRefusedWhileBusyAndOtherwiseRestartsIntoTheChosenShop() = runBlocking {
        oldShop()
        manager.create(ShopDetails("Grocery", null, null, null))
        busy = true
        assertEquals(SwitchBlock.BACKUP_RUNNING, manager.switchTo(2))
        busy = false
        saving = true
        assertEquals(SwitchBlock.SAVING, manager.switchTo(2))
        saving = false
        assertEquals(0, restarts)
        assertEquals(1, store.load().selectedId)
        assertNull(manager.switchTo(2))
        assertEquals(1, restarts)
        assertEquals(2, store.load().selectedId)
        assertEquals(SwitchBlock.ALREADY_OPEN, manager.switchTo(2))
    }

    @Test
    fun deletingRemovesOnlyThatShopAndNeverTheOpenOrLastOne() = runBlocking {
        val before = oldShop()
        manager.create(ShopDetails("Grocery", null, null, null))
        assertEquals(DeleteProblem.IS_OPEN, manager.delete(1))
        assertNull(manager.delete(2))
        assertFalse(store.paths(2).database.exists())
        assertFalse(store.paths(2).workRoot.exists())
        assertEquals(DeleteProblem.IS_OPEN, manager.delete(1))
        assertArrayEquals(before, sha(store.paths(1).database))
        // The id is not reused: the next shop gets new, empty files.
        val next = manager.create(ShopDetails("Grocery", null, null, null)) as ShopChange.Done
        assertEquals(3, next.shop.id)
        assertTrue(store.paths(3).database.exists())
    }
}

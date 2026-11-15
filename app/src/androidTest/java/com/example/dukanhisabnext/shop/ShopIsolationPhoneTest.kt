package com.example.dukanhisabnext.shop

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dukanhisabnext.backup.BackupEngine
import com.example.dukanhisabnext.backup.BackupStep
import com.example.dukanhisabnext.backup.CreateResult
import com.example.dukanhisabnext.backup.PrepareResult
import com.example.dukanhisabnext.backup.RecoveryOutcome
import com.example.dukanhisabnext.backup.RestoreResult
import com.example.dukanhisabnext.data.AppDatabase
import com.example.dukanhisabnext.data.FileSaleDraftJournal
import com.example.dukanhisabnext.data.ReportRepository
import com.example.dukanhisabnext.data.SaleDraft
import com.example.dukanhisabnext.data.SaleDraftLine
import com.example.dukanhisabnext.data.SaleRepository
import com.example.dukanhisabnext.data.SaleResult
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType
import com.example.dukanhisabnext.testing.DisplayDefaultsRule
import com.example.dukanhisabnext.testing.IsolatedContext
import com.example.dukanhisabnext.ui.sell.CartStore
import com.example.dukanhisabnext.ui.sell.FileCartStorage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two shops on one phone, laid out exactly like the app does it (ShopStore/ShopPaths), in an isolated folder:
 * a sale in shop A is invisible in shop B, each shop has its own cart, a backup of A restored into B leaves A
 * alone, and a restore stopped by a crash is repaired in that shop only.
 */
@RunWith(AndroidJUnit4::class)
class ShopIsolationPhoneTest {
    @get:Rule
    val displayDefaults = DisplayDefaultsRule()

    private val isolated = IsolatedContext.create(InstrumentationRegistry.getInstrumentation().targetContext, "shop-isolation")
    private val store = ShopStore(isolated.getDatabasePath("probe").parentFile!!, isolated.filesDir)
    private val a = store.paths(LEGACY_SHOP_ID)
    private val b = store.paths(2)
    private val open = mutableMapOf<Int, AppDatabase>()

    private fun db(p: ShopPaths): AppDatabase = open.getOrPut(p.id) { AppDatabase.open(isolated, p.database) }
    private fun close(p: ShopPaths) {
        open.remove(p.id)?.close()
    }

    @After
    fun tearDown() {
        open.values.forEach { it.close() }
        isolated.cleanUp()
    }

    private fun count(p: ShopPaths, table: String): Long =
        db(p).openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$table`").use { c -> c.moveToFirst(); c.getLong(0) }

    private suspend fun product(p: ShopPaths, name: String): Product {
        val id = db(p).productDao().insertProduct(
            Product(name = name, unit = ProductUnit.PIECE, sellPrice = 150_000, createdAt = 1, updatedAt = 1)
        )
        db(p).stockMovementDao().insert(StockMovement(productId = id, type = StockMovementType.OPENING, quantity = 10_000, createdAt = 1))
        return db(p).productDao().getProducts(listOf(id)).single()
    }

    private suspend fun sell(p: ShopPaths, productId: Long, draftId: String): SaleResult.Saved {
        val repo = SaleRepository(db(p), journal = FileSaleDraftJournal(p.journalFile))
        val result = repo.completeSale(SaleDraft(draftId, listOf(SaleDraftLine(productId, 1_000, 150_000)), 0, 0, 150_000, PaymentMethod.CASH, null))
        return result as SaleResult.Saved
    }

    private val names = mapOf(1 to "Taqwa Tech", 2 to "তাকওয়া স্টোর")

    private fun engine(p: ShopPaths, fault: (BackupStep) -> Unit = {}) = BackupEngine(
        context = isolated, getLive = { db(p) }, closeLive = { close(p) },
        appVersionCode = 1, appVersionName = "test", liveDb = p.database, workRoot = p.workRoot, fault = fault,
        shopId = p.id, shopName = { names[p.id] }
    )

    @Test
    fun aSaleInShopAIsNotSeenInShopB() = runBlocking {
        val fan = product(a, "Fan")
        val saved = sell(a, fan.id, "draft-a")
        assertEquals(1L, count(a, "sales"))

        for (table in listOf("sales", "sale_items", "products", "stock_movements", "ledger_entries", "customers", "expenses")) {
            assertEquals("shop B: $table", 0L, count(b, table))
        }
        val reportB = ReportRepository(db(b))
        assertTrue(a.database.exists() && b.database.exists() && a.database != b.database)
        assertTrue("A's journal knows the cart, B's file does not even exist", a.journalFile.exists() && !b.journalFile.exists())
        // B numbers its own receipts from the start.
        val shirt = product(b, "Shirt")
        assertEquals(saved.receiptNo, sell(b, shirt.id, "draft-b").receiptNo)
        assertEquals(1L, count(a, "sales"))
        assertEquals(1L, count(b, "sales"))
        assertTrue(reportB.toString().isNotEmpty())
    }

    @Test
    fun anUnsavedCartStaysInItsShop() = runBlocking {
        val fan = product(a, "Fan")
        CartStore(FileCartStorage(a.cartFile)).addProduct(fan)

        // "Switch" to B: a fresh process would read B's cart file.
        assertTrue(CartStore(FileCartStorage(b.cartFile)).state.value.lines.isEmpty())
        // Back to A: the sale in progress is still there.
        val back = CartStore(FileCartStorage(a.cartFile)).state.value
        assertEquals(listOf(fan.id), back.lines.map { it.productId })
    }

    @Test
    fun aBackupOfShopARestoredIntoShopBLeavesShopAAlone() = runBlocking {
        val fan = product(a, "Fan")
        sell(a, fan.id, "draft-a")
        product(b, "Shirt")
        val made = engine(a).createBackup() as CreateResult.Created
        val zip = made.zip
        assertTrue(zip.name, zip.name.startsWith("Taqwa-Tech-backup-"))
        assertEquals("Taqwa Tech", made.manifest.shopName)
        val aSales = count(a, "sales")
        val aSha = run { close(a); a.database.readBytes().contentHashCode() }

        val prepared = engine(b).prepareRestore(zip).let { it as? PrepareResult.Ready ?: throw AssertionError(it.toString()) }.restore
        assertEquals("the restore knows which shop the backup came from", "Taqwa Tech", prepared.manifest?.shopName)
        assertTrue(engine(b).restore(prepared) is RestoreResult.Done)
        assertTrue("a Bengali-only name gives Shop-<n>", engine(b).fileNameAt(0).startsWith("Shop-2-backup-"))
        close(b)

        assertEquals("B now holds A's data", aSales, count(b, "sales"))
        assertEquals(listOf("Fan"), db(b).productDao().getProducts(listOf(fan.id)).map { it.name })
        close(a)
        assertEquals("A's file is untouched", aSha, a.database.readBytes().contentHashCode())
        assertTrue("the safety copy is B's", File(b.backupPaths.safetyDir, b.database.name).exists())
        assertTrue(!a.backupPaths.safetyDir.exists())
    }

    @Test
    fun aRestoreStoppedByACrashIsRepairedInThatShopOnly() = runBlocking {
        val fan = product(a, "Fan")
        sell(a, fan.id, "draft-a")
        product(b, "Shirt")
        val zip = (engine(a).createBackup() as CreateResult.Created).zip
        close(a)
        val aBytes = a.database.readBytes()

        class Crash : Error()
        val crashing = engine(b, fault = { if (it == BackupStep.MARKER_SAFETY) throw Crash() })
        val prepared = crashing.prepareRestore(zip).let { it as? PrepareResult.Ready ?: throw AssertionError(it.toString()) }.restore
        assertTrue(runCatching { crashing.restore(prepared) }.exceptionOrNull() is Crash)
        open.remove(b.id)
        assertTrue("B's marker", b.backupPaths.marker.exists())

        // The next start runs the recovery of every shop: A has nothing to do, B is rolled back.
        assertEquals(RecoveryOutcome.NOTHING, BackupEngine.recover(a.backupPaths))
        assertEquals(RecoveryOutcome.NOT_FINISHED_NOTHING_CHANGED, BackupEngine.recover(b.backupPaths))
        assertEquals("B is back to its own data", 0L, count(b, "sales"))
        assertEquals(1L, count(b, "products"))
        close(a)
        assertTrue("A is untouched", aBytes.contentEquals(a.database.readBytes()))
    }

    @Test
    fun aNewShopWhoseWorkFolderDoesNotExistYetCanRestoreABackup() = runBlocking {
        // A backup of shop A, restored into shop 3 that was just made (no work folder, no cart, no earlier job).
        val fan = product(a, "Fan")
        sell(a, fan.id, "draft-a")
        val zip = (engine(a).createBackup() as CreateResult.Created).zip
        val c = store.paths(3)
        db(c).openHelper.writableDatabase // the shop's database exists (it was made with its profile)
        assertTrue(!c.workRoot.exists())
        val engine = engine(c)
        val (copied, problem) = engine.copyIncoming(zip.inputStream(), zip.length())
        assertEquals("no 'not enough space' for a folder that is not made yet: $problem", null, problem)
        val prepared = engine.prepareRestore(copied!!).let { it as? PrepareResult.Ready ?: throw AssertionError(it.toString()) }.restore
        assertTrue(engine.restore(prepared) is RestoreResult.Done)
        close(c)
        assertEquals(1L, count(c, "sales"))
    }
}

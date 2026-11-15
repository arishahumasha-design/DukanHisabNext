package com.example.dukanhisabnext.shop

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The registry on disk: the first start of an old install, the .bak copy, rebuilding, and crash-safe deleting. */
class ShopStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val databases by lazy { tmp.newFolder("databases") }
    private val files by lazy { tmp.newFolder("files") }
    private val deletedPrefs = mutableListOf<String>()
    private fun store() = ShopStore(databases, files, clock = { 1_000L }, deletePrefs = { deletedPrefs += it })

    private fun sha(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes())

    /** An old single-shop install: the database (with its -wal), the cart, the journal, a finished restore's files. */
    private fun oldInstall(): List<File> {
        val made = listOf(
            File(databases, "dokan_hisab.db"), File(databases, "dokan_hisab.db-wal"),
            File(files, "cart/cart.json"), File(files, "cart/sale-attempts.txt"),
            File(files, "restore-safety/dokan_hisab.db")
        )
        for ((i, f) in made.withIndex()) {
            f.parentFile.mkdirs()
            f.writeBytes(ByteArray(1000 + i) { (it * 7 + i).toByte() })
        }
        return made
    }

    @Test
    fun theFirstStartOfAnOldInstallRegistersItWithoutTouchingAnyFile() {
        val old = oldInstall()
        val before = old.map { Triple(sha(it), it.length(), it.lastModified()) }
        val r = store().load()
        assertEquals(listOf(1), r.active.map { it.id })
        assertTrue("the old database counts as opened", r.selected.opened)
        assertEquals(old.map { Triple(sha(it), it.length(), it.lastModified()) }.map { it.first.toList() to it.second },
            before.map { it.first.toList() to it.second })
        for ((i, f) in old.withIndex()) assertEquals(before[i].third, f.lastModified())
        assertTrue(store().registryFile.isFile)
        assertEquals(store().paths(1).database, old[0])
    }

    @Test
    fun aNewInstallAlsoStartsWithShopOneWhoseFileIsMadeLater() {
        val r = store().load()
        assertEquals(listOf(1), r.active.map { it.id })
        assertFalse(r.selected.opened)
        assertEquals("a never-opened shop without a file is not 'missing'", ShopHealth.OK, store().fileHealth(r.selected))
    }

    @Test
    fun aShopWhoseFileIsGoneIsMissingNeverRecreated() {
        oldInstall()
        val r = store().load()
        File(databases, "dokan_hisab.db").delete()
        assertEquals(ShopHealth.MISSING, store().fileHealth(r.selected))
        assertFalse(File(databases, "dokan_hisab.db").exists())
    }

    @Test
    fun theRegistrySurvivesADamagedFileThroughItsBackupCopy() {
        val s = store()
        var r = s.load().renamed(1, "Taqwa Tech", null)
        s.save(r)
        r = r.withNewShop("Grocery", "Mirpur", 2_000L).first
        s.save(r)
        s.registryFile.writeText("garbage")
        val back = store().load()
        assertEquals("the .bak holds the version before the last save", listOf("Taqwa Tech"), back.active.map { it.name })
        assertTrue("and it is written back as the registry", parseRegistry(s.registryFile.readText()) != null)
    }

    @Test
    fun withoutAnyRegistryTheShopsAreFoundFromTheirFiles() {
        oldInstall()
        for (id in listOf(2, 5)) File(files, "shop-databases/shop-$id.db").apply { parentFile.mkdirs(); writeText("x") }
        File(files, "shop-databases/notes.txt").writeText("not a shop")
        val r = store().load()
        assertEquals(listOf(1, 2, 5), r.active.map { it.id })
        assertEquals(6, r.nextId)
        assertEquals(1, r.selectedId)
    }

    // ---- delete ----

    private fun twoShops(): ShopRegistry {
        oldInstall()
        val s = store()
        val (r, b) = s.load().withNewShop("B", null, 2_000L)
        val p = s.paths(b.id)
        for (f in listOf(p.database, File(p.database.path + "-wal"), p.cartFile, p.journalFile, File(p.backupPaths.safetyDir, "x.db"))) {
            f.parentFile.mkdirs(); f.writeText("B data")
        }
        s.save(r)
        return r
    }

    @Test
    fun deletingAnotherShopRemovesOnlyItsFiles() {
        twoShops()
        val shopOneBefore = files.walkTopDown().filter { it.isFile && !it.path.contains("shops") && !it.path.contains("shop-databases") && !it.path.contains("shop-registry") }.map { sha(it).toList() }.toList()
        val r = store().delete(2)
        assertEquals(listOf(1), r.active.map { it.id })
        assertFalse(File(files, "shop-databases/shop-2.db").exists())
        assertFalse(File(files, "shop-databases/shop-2.db-wal").exists())
        assertFalse(File(files, "shops/2").exists())
        assertEquals(listOf("backup_prefs_shop_2"), deletedPrefs)
        assertTrue(File(databases, "dokan_hisab.db").exists())
        val shopOneAfter = files.walkTopDown().filter { it.isFile && !it.path.contains("shops") && !it.path.contains("shop-databases") && !it.path.contains("shop-registry") }.map { sha(it).toList() }.toList()
        assertEquals(shopOneBefore, shopOneAfter)
    }

    @Test
    fun deletingShopOneKeepsTheOtherShopsAndTheRegistry() {
        val r = twoShops()
        store().save(r.selecting(2))
        val bFiles = (databases.walkTopDown() + files.walkTopDown()).filter { it.isFile && it.path.contains("shop") && !it.path.contains("shop-registry") }.toList()
        val bBefore = bFiles.map { sha(it).toList() }
        val after = store().delete(1)
        assertEquals(listOf(2), after.active.map { it.id })
        assertFalse(File(databases, "dokan_hisab.db").exists())
        assertFalse(File(databases, "dokan_hisab.db-wal").exists())
        assertFalse(File(files, "cart").exists())
        assertFalse(File(files, "restore-safety").exists())
        assertEquals(bBefore, bFiles.map { sha(it).toList() })
        assertTrue(store().registryFile.isFile)
        assertEquals(listOf("backup_prefs"), deletedPrefs)
    }

    @Test
    fun theOpenShopAndTheLastShopAreNeverDeleted() {
        twoShops()
        val s = store()
        assertTrue(runCatching { s.delete(1) }.isFailure)
        assertTrue(File(databases, "dokan_hisab.db").exists())
        s.delete(2)
        assertTrue(runCatching { s.delete(1) }.isFailure)
        assertTrue(File(databases, "dokan_hisab.db").exists())
    }

    @Test
    fun aDeleteStoppedHalfwayIsFinishedAtTheNextStart() {
        val r = twoShops()
        // The crash: the entry was marked and only the database went.
        store().save(r.markedDeleting(2))
        File(files, "shop-databases/shop-2.db").delete()
        val wal = File(files, "shop-databases/shop-2.db-wal")
        assertTrue(wal.exists())
        val after = store().finishPendingDeletes()
        assertEquals(listOf(1), after.shops.map { it.id })
        assertFalse(wal.exists())
        assertFalse(File(files, "shops/2").exists())
        assertArrayEquals(writeRegistry(after).toByteArray(), store().registryFile.readBytes())
    }
}

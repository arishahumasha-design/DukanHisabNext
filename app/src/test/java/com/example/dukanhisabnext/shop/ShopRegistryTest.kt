package com.example.dukanhisabnext.shop

import com.example.dukanhisabnext.backup.BACKUP_PREFS_NAME
import com.example.dukanhisabnext.data.AppDatabase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopRegistryTest {

    private val t = 1_763_190_000_000L

    private fun registryWith(vararg names: String): ShopRegistry {
        var r = ShopRegistry.legacy(t, opened = true).renamed(LEGACY_SHOP_ID, names[0], null)
        for (n in names.drop(1)) r = r.withNewShop(n, null, t).first
        return r
    }

    // ---- the legacy shop ----

    @Test
    fun anOldInstallBecomesShopOneWithItsOldFileNames() {
        val r = ShopRegistry.legacy(t, opened = true)
        assertEquals(listOf(1), r.active.map { it.id })
        assertEquals(1, r.selectedId)
        assertEquals(2, r.nextId)
        val p = ShopPaths(1, File("/d/databases"), File("/d/files"))
        assertEquals(File("/d/databases/dokan_hisab.db"), p.database)
        assertEquals(AppDatabase.FILE_NAME, p.database.name)
        assertEquals(File("/d/files/cart/cart.json"), p.cartFile)
        assertEquals(File("/d/files/cart/sale-attempts.txt"), p.journalFile)
        assertEquals(File("/d/files/restore.state"), p.backupPaths.marker)
        assertEquals(File("/d/files/restore-safety"), p.backupPaths.safetyDir)
        assertEquals(BACKUP_PREFS_NAME, p.backupPrefsName)
    }

    @Test
    fun otherShopsHaveTheirOwnFolders() {
        val p = ShopPaths(7, File("/d/databases"), File("/d/files"))
        assertEquals(File("/d/files/shop-databases/shop-7.db"), p.database)
        assertEquals(File("/d/files/shops/7/cart/cart.json"), p.cartFile)
        assertEquals(File("/d/files/shops/7/cart/sale-attempts.txt"), p.journalFile)
        assertEquals(File("/d/files/shops/7/restore.state"), p.backupPaths.marker)
        assertEquals(File("/d/files/shops/7/backup-work"), p.backupPaths.workDir)
        assertEquals("backup_prefs_shop_7", p.backupPrefsName)
    }

    @Test
    fun deletingShopOneNeverNamesTheWholeFilesFolderOrAnotherShop() {
        val files = File("/d/files")
        val owned = ShopPaths(1, File("/d/databases"), files).ownedFiles()
        assertFalse("the files folder itself holds the other shops and the registry", files in owned)
        assertTrue(owned.none { it.path.startsWith(File(files, "shops").path) })
        assertTrue(owned.none { it.path.contains("shop-registry") })
        assertTrue(File("/d/databases/dokan_hisab.db") in owned)
        assertTrue(File("/d/files/cart") in owned)
        val other = ShopPaths(2, File("/d/databases"), files).ownedFiles()
        assertTrue(File("/d/files/shops/2") in other)
        assertTrue(other.none { it == files || it.path.contains("dokan_hisab") || it.path.contains("shops/3") || it.path.contains("shops\\3") })
    }

    // ---- names ----

    @Test
    fun namesAreUniqueIgnoringCaseAndSpacing() {
        val r = registryWith("Taqwa Tech", "Grocery")
        assertEquals(ShopNameProblem.DUPLICATE, checkShopName(r, "taqwa   TECH"))
        assertEquals(ShopNameProblem.DUPLICATE, checkShopName(r, " grocery "))
        assertNull(checkShopName(r, "Plastic"))
        assertNull("a shop keeps its own name when edited", checkShopName(r, "TAQWA tech", exceptId = 1))
        assertEquals(ShopNameProblem.EMPTY, checkShopName(r, "   "))
        assertNull("Bengali names work too", checkShopName(r, "তাকওয়া স্টোর"))
    }

    @Test
    fun theLimitIsTwentyShops() {
        var r = ShopRegistry.legacy(t, opened = true).renamed(1, "Shop 1", null)
        for (i in 2..MAX_SHOPS) {
            assertTrue(canAddShop(r))
            r = r.withNewShop("Shop $i", null, t).first
        }
        assertEquals(20, r.active.size)
        assertFalse(canAddShop(r))
    }

    @Test
    fun idsAreNeverReused() {
        var r = registryWith("A", "B", "C") // ids 1, 2, 3
        r = r.markedDeleting(3).without(3)
        val (after, entry) = r.withNewShop("D", null, t)
        assertEquals(4, entry.id)
        assertEquals(5, after.nextId)
        assertEquals(listOf(1, 2, 4), after.active.map { it.id })
    }

    // ---- delete rules ----

    @Test
    fun onlyAShopThatIsNotOpenAndNotTheLastCanBeDeleted() {
        val one = registryWith("A")
        assertEquals(DeleteProblem.IS_OPEN, checkDelete(one, 1))
        val two = registryWith("A", "B")
        assertEquals(DeleteProblem.IS_OPEN, checkDelete(two, 1))
        assertNull(checkDelete(two, 2))
        assertEquals(DeleteProblem.NOT_FOUND, checkDelete(two, 9))
        val onlyBOpen = two.selecting(2).markedDeleting(1)
        assertEquals("a shop being deleted does not count", DeleteProblem.IS_OPEN, checkDelete(onlyBOpen, 2))
    }

    @Test
    fun theDeleteButtonNeedsTheNameAndABackupOrTheExplicitTick() {
        assertFalse(deleteAllowed("Grocery", "Grocery", backedUp = false, noBackupTicked = false))
        assertTrue(deleteAllowed("Grocery", "Grocery", backedUp = true, noBackupTicked = false))
        assertTrue(deleteAllowed(" grocery ", "Grocery", backedUp = false, noBackupTicked = true))
        assertFalse(deleteAllowed("Grocer", "Grocery", backedUp = true, noBackupTicked = true))
        assertFalse(deleteAllowed("", "", backedUp = true, noBackupTicked = true))
    }

    // ---- the file ----

    @Test
    fun theRegistryFileRoundTripsIncludingBengaliAndSpecialCharacters() {
        var r = registryWith("Taqwa Tech", "তাকওয়া স্টোর", "A=B: #1 \\ x")
        r = r.renamed(2, "তাকওয়া স্টোর", "ঢাকা\nমিরপুর").markedDeleting(3).selecting(2)
        val back = parseRegistry(writeRegistry(r))
        assertEquals(r, back)
    }

    @Test
    fun aBrokenRegistryIsRefusedNotGuessed() {
        val good = writeRegistry(registryWith("A", "B"))
        assertNull(parseRegistry(""))
        assertNull(parseRegistry("hello"))
        assertNull(parseRegistry(good.replace("format=dokanhisab-shops", "format=other")))
        assertNull(parseRegistry(good.replace("version=1", "version=2")))
        assertNull(parseRegistry(good.replace("nextId=3", "nextId=2")))
        assertNull(parseRegistry(good.replace("shop.2.createdAt=", "shop.2.createdAtX=")))
        assertNull(parseRegistry(good.replace("ids=1,2", "ids=1,1")))
        assertNull(parseRegistry(good.lines().take(6).joinToString("\n")))
    }

    @Test
    fun anOpenShopThatIsGoneOpensTheFirstShopInstead() {
        val r = registryWith("A", "B").selecting(2).markedDeleting(2)
        assertEquals(1, parseRegistry(writeRegistry(r))!!.selectedId)
    }

    // ---- backup file names ----

    @Test
    fun theBackupFileNameStartsWithTheShopNameInAscii() {
        val at = 1_763_190_540_000L // 2025-11-15 13:09 in Dhaka
        assertEquals("Taqwa-Tech-backup-2025-11-15-1309.zip", shopBackupFileName("Taqwa Tech", 1, at))
        assertEquals("Shop-3-backup-2025-11-15-1309.zip", shopBackupFileName("তাকওয়া স্টোর", 3, at))
        assertEquals("Rahim-Store-backup-2025-11-15-1309.zip", shopBackupFileName("রহিম Rahim Store!!", 1, at).removePrefix("-"))
        assertEquals("Shop-1-backup-2025-11-15-1309.zip", shopBackupFileName("", 1, at))
        assertEquals("Shop-2-backup-2025-11-15-1309.zip", shopBackupFileName("2024", 2, at))
    }

    @Test
    fun theSlugIsSafeAndShort() {
        assertEquals("Taqwa-Tech", asciiShopSlug("  Taqwa / Tech \\ ", 1))
        assertEquals("A-B", asciiShopSlug("A..::B", 1))
        assertEquals("Shop-9", asciiShopSlug(null, 9))
        val long = asciiShopSlug("x".repeat(100), 1)
        assertEquals(40, long.length)
        for (name in listOf("../../etc", "a\u0000b", "con.zip", "ক-খ")) {
            val s = asciiShopSlug(name, 4)
            assertTrue(s, s.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' })
            assertFalse(s.startsWith("-") || s.endsWith("-"))
        }
    }

    @Test
    fun differentShopNamesAreNoticedButCaseAndMissingNamesAreNot() {
        assertTrue(shopNamesDiffer("Grocery", "Taqwa Tech"))
        assertFalse(shopNamesDiffer("Taqwa Tech", "taqwa  tech"))
        assertFalse(shopNamesDiffer(null, "Taqwa Tech"))
        assertFalse(shopNamesDiffer("Taqwa Tech", ""))
    }
}

package com.example.dukanhisabnext.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Several shops: the optional "shopName" of the manifest (format version stays 1), the per-shop file name, and
 * what the restore dialog says about the shop a backup came from.
 */
class BackupShopNameTest {

    private fun manifest(shop: String? = null) = BackupManifest(
        dbSchemaVersion = 5, appVersionCode = 1, appVersionName = "1.0.0", createdAt = 1_000, dbSize = 4096,
        dbSha256 = "a".repeat(64), counts = mapOf("sales" to 2L), shopName = shop
    )

    private fun parsed(text: String): BackupManifest = (parseManifest(text) as ManifestResult.Ok).manifest

    @Test
    fun withoutAShopNameTheManifestIsExactlyAsBefore() {
        val text = writeManifest(manifest())
        assertFalse(text.contains("shopName"))
        assertNull(parsed(text).shopName)
    }

    @Test
    fun theFullShopNameRoundTripsBengaliAndSymbolsIncluded() {
        for (name in listOf("Taqwa Tech", "তাকওয়া স্টোর", "A=B: #1 \\ ঢাকা")) {
            val back = parsed(writeManifest(manifest(name)))
            assertEquals(name, back.shopName)
        }
    }

    @Test
    fun aJunkShopNameIsCleanedOrIgnoredAndTheBackupStaysUsable() {
        val base = writeManifest(manifest())
        assertNull(parsed(base + "shopName=   \n").shopName)
        assertEquals("ab", parsed(base + "shopName=a\\u0007b\n").shopName)
        assertEquals(80, parsed(base + "shopName=" + "x".repeat(500) + "\n").shopName!!.length)
    }

    @Test
    fun theSaveFileNameCarriesTheShop() {
        val at = 1_763_190_540_000L // 2025-11-15 13:09 in Dhaka
        assertEquals("Taqwa-Tech-backup-2025-11-15-1309.zip", saveFileName(at, "Taqwa Tech", 1))
        assertEquals("Shop-2-backup-2025-11-15-1309.zip", saveFileName(at, "তাকওয়া স্টোর", 2))
        assertEquals("without a shop the old name stays", backupFileName(at), saveFileName(at, "Taqwa Tech", null))
        assertTrue(saveFileName(at, "দোকান", 4).all { it.code < 128 })
    }

    @Test
    fun theRestoreDialogWarnsOnlyForAnotherShop() {
        assertEquals(RestoreShopNote.SAME, restoreShopNote("Taqwa Tech", false, "taqwa  tech"))
        assertEquals(RestoreShopNote.OTHER_SHOP, restoreShopNote("Grocery", false, "Taqwa Tech"))
        assertEquals(RestoreShopNote.UNKNOWN, restoreShopNote(null, false, "Taqwa Tech"))
        assertEquals("an undo brings back this shop's own data", RestoreShopNote.SAME, restoreShopNote(null, true, "Taqwa Tech"))
        assertEquals("a shop without a name yet cannot be compared", RestoreShopNote.SAME, restoreShopNote("Grocery", false, ""))
    }
}

package com.example.dukanhisabnext.backup

import com.example.dukanhisabnext.util.AppCurrency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The optional "displayCurrency" of the backup manifest: written only when known, read when it is a plausible
 * code, ignored otherwise. Format version stays 1: an older manifest without it reads exactly as before.
 */
class BackupCurrencyTest {

    private fun manifest(currency: String? = null) = BackupManifest(
        dbSchemaVersion = 5, appVersionCode = 1, appVersionName = "1.0.0", createdAt = 1_000, dbSize = 4096,
        dbSha256 = "a".repeat(64), counts = mapOf("sales" to 2L), displayCurrency = currency
    )

    private fun parsed(text: String): BackupManifest = (parseManifest(text) as ManifestResult.Ok).manifest

    @Test
    fun withoutACurrencyTheManifestIsExactlyAsBefore() {
        val text = writeManifest(manifest())
        assertFalse(text.contains("displayCurrency"))
        val expected = listOf(
            "format=$BACKUP_FORMAT_NAME", "formatVersion=1", "platform=$BACKUP_PLATFORM", "dbSchemaVersion=5", "appVersionCode=1",
            "appVersionName=1.0.0", "createdAt=1000", "dbEntry=$DATABASE_ENTRY", "dbSize=4096", "dbSha256=${"a".repeat(64)}",
            "encryption=none", "count.sales=2"
        ).joinToString("\n", postfix = "\n")
        assertEquals(expected, text)
        assertEquals(BACKUP_FORMAT_VERSION, 1)
    }

    @Test
    fun aNewManifestRoundTripsTheCurrency() {
        val text = writeManifest(manifest("INR"))
        assertTrue(text.endsWith("displayCurrency=INR\n"))
        val back = parsed(text)
        assertEquals("INR", back.displayCurrency)
        assertEquals(manifest("INR"), back)
        assertEquals(1, back.formatVersion)
    }

    @Test
    fun anOldManifestWithoutTheFieldReadsAsBefore() {
        val back = parsed(writeManifest(manifest()))
        assertNull(back.displayCurrency)
        assertEquals(manifest(), back)
    }

    @Test
    fun aCorruptCurrencyIsIgnoredAndTheBackupStillReads() {
        val base = writeManifest(manifest())
        for (junk in listOf("", "inr", "₹", "IN", "INR1", "TOOLONGCODE", "IN R", "\\u0000")) {
            val result = parseManifest(base + "displayCurrency=$junk\n")
            assertTrue("$junk must not refuse the backup", result is ManifestResult.Ok)
            assertNull("$junk is ignored", (result as ManifestResult.Ok).manifest.displayCurrency)
        }
    }

    @Test
    fun anUnknownButWellFormedCodeIsKeptButNotApplied() {
        // A newer app may know more currencies: the code is read, but this app keeps its own currency.
        val back = parsed(writeManifest(manifest()) + "displayCurrency=XYZ\n")
        assertEquals("XYZ", back.displayCurrency)
        assertEquals(RestoreCurrencyCase.NotInBackup(AppCurrency.BDT), restoreCurrencyCase(back.displayCurrency, AppCurrency.BDT))
    }

    @Test
    fun everyCurrencyOfTheAppRoundTrips() {
        for (c in AppCurrency.entries) assertEquals(c.code, parsed(writeManifest(manifest(c.code))).displayCurrency)
    }

    @Test
    fun theRestoreDialogCase() {
        assertEquals(RestoreCurrencyCase.NotInBackup(AppCurrency.BDT), restoreCurrencyCase(null, AppCurrency.BDT))
        assertEquals(RestoreCurrencyCase.NotInBackup(AppCurrency.INR), restoreCurrencyCase(null, AppCurrency.INR))
        assertEquals(RestoreCurrencyCase.Same(AppCurrency.INR), restoreCurrencyCase("INR", AppCurrency.INR))
        assertEquals(RestoreCurrencyCase.Different(AppCurrency.INR, AppCurrency.BDT), restoreCurrencyCase("INR", AppCurrency.BDT))
        assertEquals(RestoreCurrencyCase.Different(AppCurrency.BDT, AppCurrency.USD), restoreCurrencyCase("BDT", AppCurrency.USD))
    }

    @Test
    fun theCodeCheck() {
        assertEquals("INR", currencyCodeOrNull(" INR "))
        assertEquals("BDT", currencyCodeOrNull("BDT"))
        assertNull(currencyCodeOrNull(null))
        assertNull(currencyCodeOrNull("bdt"))
    }
}

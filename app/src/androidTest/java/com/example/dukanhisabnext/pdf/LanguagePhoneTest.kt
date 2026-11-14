package com.example.dukanhisabnext.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.CustomerRepository
import com.example.dukanhisabnext.data.DatabaseTestBase
import com.example.dukanhisabnext.data.LedgerRepository
import com.example.dukanhisabnext.data.ProductRepository
import com.example.dukanhisabnext.data.ShopRepository
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.ShopProfile
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType
import com.example.dukanhisabnext.util.AppCurrency
import com.example.dukanhisabnext.util.AppLanguage
import com.example.dukanhisabnext.util.CurrentCurrency
import com.example.dukanhisabnext.util.CurrentLanguage
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.withLanguage
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Switching the language and the currency, on the phone: the right resources load for each language, every
 * string exists in both, and the PDFs follow the language (user-entered Bengali names still render in an
 * English PDF). Only an isolated database and folders are used; all data is made up.
 */
@RunWith(AndroidJUnit4::class)
class LanguagePhoneTest : DatabaseTestBase() {

    @After
    fun backToDefaults() {
        CurrentLanguage.value = AppLanguage.BENGALI
        CurrentCurrency.value = AppCurrency.DEFAULT
    }

    private fun strings(language: AppLanguage) = testContext.withLanguage(language).resources

    // =====================================================================================
    // The resources
    // =====================================================================================

    @Test
    fun theTwoLanguagesLoadTheirOwnTexts() {
        assertEquals("হোম", strings(AppLanguage.BENGALI).getString(R.string.tab_home))
        assertEquals("Home", strings(AppLanguage.ENGLISH).getString(R.string.tab_home))
        assertEquals("খাতা", strings(AppLanguage.BENGALI).getString(R.string.tab_khata))
        assertEquals("Due Book", strings(AppLanguage.ENGLISH).getString(R.string.tab_khata))
    }

    @Test
    fun everyStringExistsInBothLanguagesAndEnglishIsReallyEnglish() {
        val bengali = strings(AppLanguage.BENGALI)
        val english = strings(AppLanguage.ENGLISH)
        // The same text in both on purpose: symbols, names that must be readable in either language, numbers.
        val same = setOf(
            "unit_taka", "unit_percent", "price_format", "phone_country_prefix", "pdf_button", "cancel_reason_counter", "ledger_note_counter",
            "movement_line", "settings_language_title", "settings_currency_title", "language_english", "in_cart_none", "backup_ok"
        )
        var checked = 0
        for (field in R.string::class.java.fields) {
            val id = field.getInt(null)
            val name = field.name
            val bn = bengali.getString(id)
            val en = english.getString(id)
            checked++
            assertTrue("$name is empty in English", en.isNotBlank())
            if (name !in same && !name.startsWith("currency_name_") && name != "language_bengali" && !name.startsWith("app_name")) {
                assertNotEquals("$name has the same text in both languages", bn, en)
            }
            if (name !in setOf("settings_language_title", "settings_currency_title", "language_bengali")) {
                assertTrue("Bengali letters in the English $name: $en", !en.any { it in 'ঀ'..'৲' || it in '৴'..'৿' })
            }
        }
        assertTrue("many strings were checked ($checked)", checked > 500)
    }

    @Test
    fun pluralsAreRightInEnglish() {
        val en = strings(AppLanguage.ENGLISH)
        assertEquals("1 sale", en.getQuantityString(R.plurals.home_sales_count, 1, "1"))
        assertEquals("3 sales", en.getQuantityString(R.plurals.home_sales_count, 3, "3"))
        assertEquals("0 sales", en.getQuantityString(R.plurals.home_sales_count, 0, "0"))
        assertEquals("1 product", en.getQuantityString(R.plurals.home_low_stock_count, 1, "1"))
        assertEquals("2 products", en.getQuantityString(R.plurals.home_low_stock_count, 2, "2"))
        assertEquals("1 entry", en.getQuantityString(R.plurals.expense_count, 1, "1"))
        assertEquals("5 entries", en.getQuantityString(R.plurals.expense_count, 5, "5"))
        assertEquals("1 sale · total ৳ 150", en.getQuantityString(R.plurals.sales_summary, 1, "1", "150"))
        val bn = strings(AppLanguage.BENGALI)
        assertEquals("১ টি বিক্রি", bn.getQuantityString(R.plurals.home_sales_count, 1, "১"))
        assertEquals("৩ টি বিক্রি", bn.getQuantityString(R.plurals.home_sales_count, 3, "৩"))
    }

    @Test
    fun monthNamesAndDefaultCategoriesFollowTheLanguage() {
        assertEquals("নভেম্বর", strings(AppLanguage.BENGALI).getStringArray(R.array.months_bn)[10])
        assertEquals("Nov", strings(AppLanguage.ENGLISH).getStringArray(R.array.months_bn)[10])
        assertEquals("Rent", strings(AppLanguage.ENGLISH).getStringArray(R.array.expense_default_categories)[1])
        assertEquals("ভাড়া", strings(AppLanguage.BENGALI).getStringArray(R.array.expense_default_categories)[1])
        assertEquals(12, strings(AppLanguage.ENGLISH).getStringArray(R.array.months_bn).size)
    }

    // =====================================================================================
    // The PDFs
    // =====================================================================================

    private val previewDir: File get() = File(testContext.root.parentFile, "language-preview").also { it.mkdirs() }

    private fun service() = PdfExportService(
        testContext, ShopRepository(db), LedgerRepository(db, { now }), CustomerRepository(db), ProductRepository(db), clock = { now }
    )

    private var customerId = 0L

    private suspend fun seed() {
        ShopRepository(db).save(ShopProfile(name = "টেস্ট দোকান (Test Shop)", phone = "01700000000", address = "পরীক্ষার ঠিকানা, Dhaka"))
        customerId = db.customerDao().insert(Customer(name = "টেস্ট কাস্টমার রহিম", phone = "01711111111", createdAt = now, updatedAt = now))
        db.customerDao().insert(Customer(name = "Test Customer Karim", phone = "01822222222", createdAt = now, updatedAt = now)).let { id ->
            db.ledgerDao().insert(LedgerEntry(customerId = id, type = LedgerEntryType.MANUAL_DUE, amount = 5_000_000, entryDate = now, createdAt = now))
        }
        db.ledgerDao().insert(LedgerEntry(customerId = customerId, type = LedgerEntryType.OPENING_DUE, amount = 250_000, entryDate = now - 86_400_000L, createdAt = now))
        db.ledgerDao().insert(LedgerEntry(customerId = customerId, type = LedgerEntryType.PAYMENT_RECEIVED, amount = 100_000, entryDate = now, createdAt = now))
        repeat(3) { n ->
            val id = db.productDao().insertProduct(
                Product(name = if (n == 0) "টেস্ট পণ্য চাল" else "Test product $n", unit = ProductUnit.KG, sellPrice = 12_000L * (n + 1), costPrice = 9_000L * (n + 1), createdAt = now, updatedAt = now)
            )
            db.stockMovementDao().insert(StockMovement(productId = id, type = StockMovementType.OPENING, quantity = 10_500, createdAt = now))
        }
    }

    private fun render(file: File, name: String): List<Bitmap> {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        try {
            return (0 until renderer.pageCount).map { i ->
                renderer.openPage(i).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    FileOutputStream(File(previewDir, "$name-p${i + 1}.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap
                }
            }
        } finally {
            renderer.close()
            fd.close()
        }
    }

    private fun hasInk(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.red(it) < 120 && Color.green(it) < 120 && Color.blue(it) < 120 } > 500
    }

    @Test
    fun englishPdfsRenderWithEnglishWordsAndBengaliNames() = runBlocking {
        seed()
        CurrentLanguage.value = AppLanguage.ENGLISH
        val svc = service()
        // One at a time: every export deletes the PDF of the one before.
        val exports: List<Pair<String, suspend () -> PdfResult>> = listOf(
            "en-due-list" to { svc.dueList() }, "en-products" to { svc.productList(true) }, "en-statement" to { svc.statement(customerId) }
        )
        for ((name, export) in exports) {
            val result = export()
            val file = (result as? PdfResult.Done)?.file ?: throw AssertionError("$name: $result")
            val pages = render(file, name)
            assertTrue("$name page 1 has text", hasInk(pages[0]))
        }
        Unit
    }

    @Test
    fun theStatementFileKeepsItsAsciiNameInEnglishToo() = runBlocking {
        seed()
        CurrentLanguage.value = AppLanguage.ENGLISH
        val file = (service().statement(customerId) as PdfResult.Done).file
        assertEquals("statement-$customerId-2025-11-09.pdf", file.name)
        Unit
    }

    @Test
    fun aDifferentCurrencyAndLanguageGiveAPdf() = runBlocking {
        seed()
        CurrentLanguage.value = AppLanguage.ENGLISH
        CurrentCurrency.value = AppCurrency.INR
        val inr = (service().dueList() as PdfResult.Done).file
        assertTrue(hasInk(render(inr, "en-inr-due-list")[0]))
        CurrentLanguage.value = AppLanguage.BENGALI
        CurrentCurrency.value = AppCurrency.USD
        val usd = (service().dueList() as PdfResult.Done).file
        assertTrue(hasInk(render(usd, "bn-usd-due-list")[0]))
        // The same stored amounts, only written another way.
        CurrentCurrency.value = AppCurrency.BDT
        assertEquals("১২,৩৪,৫৬৭", formatTaka(123_456_700))
        CurrentLanguage.value = AppLanguage.ENGLISH
        CurrentCurrency.value = AppCurrency.USD
        assertEquals("1,234,567", formatTaka(123_456_700))
        Unit
    }

    @Test
    fun theEmptyMessagesFollowTheLanguage() {
        assertEquals("There are no products, so there is nothing to put in a PDF.", strings(AppLanguage.ENGLISH).getString(R.string.pdf_empty_products))
    }
}

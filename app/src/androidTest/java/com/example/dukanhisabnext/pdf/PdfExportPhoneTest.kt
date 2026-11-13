package com.example.dukanhisabnext.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.CustomerRepository
import com.example.dukanhisabnext.data.DatabaseTestBase
import com.example.dukanhisabnext.data.ExpenseRepository
import com.example.dukanhisabnext.data.LedgerRepository
import com.example.dukanhisabnext.data.ProductRepository
import com.example.dukanhisabnext.data.ReportRepository
import com.example.dukanhisabnext.data.SaleDraft
import com.example.dukanhisabnext.data.SaleDraftLine
import com.example.dukanhisabnext.data.SaleRepository
import com.example.dukanhisabnext.data.SaleResult
import com.example.dukanhisabnext.data.ShopRepository
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.ShopProfile
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType
import com.example.dukanhisabnext.expense.DefaultCategory
import com.example.dukanhisabnext.expense.ExpenseInput
import com.example.dukanhisabnext.expense.expenseSummary
import com.example.dukanhisabnext.ledger.KhataSort
import com.example.dukanhisabnext.ledger.khataAllSummary
import com.example.dukanhisabnext.ledger.periodSummary
import com.example.dukanhisabnext.ledger.visibleKhataCustomers
import com.example.dukanhisabnext.ui.report.ReportBody
import com.example.dukanhisabnext.ui.report.ReportTab
import com.example.dukanhisabnext.util.DhakaTime
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The four PDF documents, made from test data in an isolated database, opened again with PdfRenderer:
 * the page count, that page 1 is not blank, and a PNG of every page for the owner to look at.
 * All data here is made up (clearly named test shop, test customers, test products). The PDFs and the
 * PNGs are written inside the isolated test folder, never into the app's real cache/pdf folder.
 */
@RunWith(AndroidJUnit4::class)
class PdfExportPhoneTest : DatabaseTestBase() {

    private val day = LocalDate.of(2025, 11, 9)
    private val range = DhakaTime.thisYear(now)

    private fun service() = PdfExportService(
        testContext, ShopRepository(db), LedgerRepository(db, { now }), CustomerRepository(db), ProductRepository(db), clock = { now }
    )

    /** Where the PNG previews go: a sibling of the isolated root, so cleaning the root keeps them. */
    private val previewDir: File get() = File(testContext.root.parentFile, "pdf-preview").also { it.mkdirs() }

    // =====================================================================================
    // Test data
    // =====================================================================================

    private var customerIds = emptyList<Long>()

    private suspend fun seed(products: Int = 70) {
        ShopRepository(db).save(
            ShopProfile(name = "টেস্ট দোকান (পরীক্ষার তথ্য)", ownerName = "টেস্ট মালিক", phone = "01700000000", address = "পরীক্ষার ঠিকানা, ঢাকা ১২০৫")
        )
        val productRepo = ProductRepository(db)
        val grocery = productRepo.addCategory("মুদি")!!
        val electric = productRepo.addCategory("বৈদ্যুতিক সামগ্রী")!!
        val units = ProductUnit.entries
        val ids = (1..products).map { n ->
            val name = if (n % 9 == 0) "টেস্ট পণ্য $n — একটি অনেক লম্বা নামের পণ্য যার নাম দুই লাইনে ভাঙবে" else "টেস্ট পণ্য $n"
            val id = db.productDao().insertProduct(
                Product(
                    name = name, categoryId = if (n % 2 == 0) grocery else electric, unit = units[n % units.size],
                    sellPrice = 10_000L + n * 150, costPrice = if (n % 7 == 0) null else 8_000L + n * 100, isActive = n % 11 != 0,
                    createdAt = now, updatedAt = now
                )
            )
            db.stockMovementDao().insert(StockMovement(productId = id, type = StockMovementType.OPENING, quantity = 50_000L + n * 500, createdAt = now))
            id
        }

        // Customers with a due, an advance and a clear balance.
        val customers = listOf(
            Customer(name = "টেস্ট কাস্টমার রহিম", phone = "01711111111", createdAt = now, updatedAt = now),
            Customer(name = "টেস্ট কাস্টমার করিম উদ্দিন আহমেদ চৌধুরী (খুব লম্বা নামের পরীক্ষা)", phone = "01822222222", createdAt = now, updatedAt = now),
            Customer(name = "টেস্ট কাস্টমার সালমা", phone = null, createdAt = now, updatedAt = now),
            Customer(name = "টেস্ট কাস্টমার অগ্রিম", phone = "01933333333", createdAt = now, updatedAt = now),
            Customer(name = "টেস্ট কাস্টমার পরিষ্কার", phone = "01644444444", createdAt = now, updatedAt = now)
        )
        customerIds = customers.map { db.customerDao().insert(it) }
        val dayMs = 24L * 3600 * 1000
        fun entry(c: Int, type: LedgerEntryType, amount: Long, daysAgo: Long, note: String? = null) = LedgerEntry(
            customerId = customerIds[c], type = type, amount = amount, note = note, entryDate = now - daysAgo * dayMs, createdAt = now - daysAgo * dayMs,
            paymentMethod = if (type == LedgerEntryType.PAYMENT_RECEIVED) PaymentMethod.CASH else null
        )
        val entries = listOf(
            entry(0, LedgerEntryType.OPENING_DUE, 250_000, 30), entry(0, LedgerEntryType.MANUAL_DUE, 80_000, 20, "পরীক্ষার বাকি"),
            entry(0, LedgerEntryType.PAYMENT_RECEIVED, 100_000, 10), entry(0, LedgerEntryType.CORRECTION_MINUS, 5_000, 5, "হিসাব মেলানো"),
            entry(1, LedgerEntryType.MANUAL_DUE, 1_250_000, 15), entry(1, LedgerEntryType.PAYMENT_RECEIVED, 250_000, 3),
            entry(2, LedgerEntryType.OPENING_DUE, 45_000, 40),
            entry(3, LedgerEntryType.MANUAL_DUE, 30_000, 12), entry(3, LedgerEntryType.PAYMENT_RECEIVED, 80_000, 2),
            entry(4, LedgerEntryType.MANUAL_DUE, 20_000, 9), entry(4, LedgerEntryType.PAYMENT_RECEIVED, 20_000, 1)
        )
        entries.forEach { db.ledgerDao().insert(it) }

        // Two real sales (one with a due on a customer, one for a walk-in customer).
        val sales = SaleRepository(db, { now })
        var n = 0
        suspend fun sell(productIndex: Int, qty: Long, received: Long, customer: Long?, discount: Long = 0) {
            val p = ids[productIndex]
            val price = 10_000L + (productIndex + 1) * 150
            val draft = SaleDraft(
                draftId = "pdf-test-${n++}", lines = listOf(SaleDraftLine(p, qty, price)), discount = discount, deliveryCharge = 0,
                received = received, paymentMethod = PaymentMethod.CASH, customerId = customer
            )
            assertTrue(sales.completeSale(draft) is SaleResult.Saved)
        }
        sell(0, 2_000, 20_000, customerIds[1])
        sell(1, 3_000, 60_000, null, discount = 500)
        sell(2, 1_000, 0, customerIds[0])

        // A few expenses (shop and personal).
        val expenses = ExpenseRepository(db, { now })
        expenses.ensureDefaultCategories { it.name }
        val cats = db.expenseDao().getCategories()
        fun cat(key: DefaultCategory) = cats.first { it.systemKey == key.name }.id
        expenses.addExpense("f1", ExpenseInput(cat(DefaultCategory.RENT), 500_000, day, "দোকান ভাড়া"))
        expenses.addExpense("f2", ExpenseInput(cat(DefaultCategory.TEA_SNACKS), 12_000, day, null))
        expenses.addExpense("f3", ExpenseInput(cat(DefaultCategory.TRANSPORT), 8_000, day, null))
    }

    // =====================================================================================
    // Reading the PDF back
    // =====================================================================================

    private class Opened(val renderer: PdfRenderer, val fd: ParcelFileDescriptor) {
        fun close() {
            renderer.close()
            fd.close()
        }
    }

    private fun open(file: File) = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).let { Opened(PdfRenderer(it), it) }

    /** Renders page [index] (2x, white background). */
    private fun render(opened: Opened, index: Int): Bitmap {
        opened.renderer.openPage(index).use { page ->
            val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }

    private fun isBlank(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.all { it == Color.WHITE }
    }

    private fun darkPixels(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.red(it) < 120 && Color.green(it) < 120 && Color.blue(it) < 120 }
    }

    /** Checks a finished PDF, saves a PNG of each page, returns the page count. */
    private fun check(result: PdfResult, previewName: String, minPages: Int = 1, maxPages: Int = 200): Int {
        val file = (result as? PdfResult.Done)?.file ?: throw AssertionError("expected a PDF, got $result")
        assertTrue(file.exists() && file.length() > 1_000)
        assertTrue("the file is in the pdf cache folder", file.parentFile!!.name == PDF_FOLDER)
        assertTrue("an ASCII name: ${file.name}", file.name.all { it.code in 0x21..0x7E })
        val opened = open(file)
        try {
            val count = opened.renderer.pageCount
            assertTrue("$previewName has $count pages", count in minPages..maxPages)
            for (i in 0 until count) {
                val bitmap = render(opened, i)
                if (i == 0) {
                    assertTrue("page 1 of $previewName is not blank", !isBlank(bitmap))
                    assertTrue("page 1 of $previewName has real text", darkPixels(bitmap) > 500)
                }
                val suffix = if (count == 1) "" else "-p${i + 1}"
                FileOutputStream(File(previewDir, "$previewName$suffix.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            return count
        } finally {
            opened.close()
        }
    }

    // =====================================================================================
    // The four documents
    // =====================================================================================

    @Test
    fun dueListPdf() = runBlocking {
        seed()
        check(service().dueList(), "due-list")
        Unit
    }

    @Test
    fun productListPdfWithoutAndWithCostPrice() = runBlocking {
        seed(products = 70)
        val without = check(service().productList(showCost = false), "products-no-cost", minPages = 2)
        val with = check(service().productList(showCost = true), "products-with-cost", minPages = 2)
        assertTrue(without >= 2 && with >= 2)
        Unit
    }

    @Test
    fun aThousandProductsMakeAPdfWithManyPages() = runBlocking {
        seed(products = 1_000)
        val pages = check(service().productList(showCost = true), "products-1000", minPages = 20)
        assertTrue(pages >= 20)
        Unit
    }

    @Test
    fun reportPdfOfEveryTab() = runBlocking {
        seed()
        val reports = ReportRepository(db)
        val sales = SaleRepository(db, { now })
        val ledger = LedgerRepository(db, { now })
        val expenses = ExpenseRepository(db, { now })
        val svc = service()
        val period = "এই বছর · ০১/০১/২০২৫ – ৩১/১২/২০২৫"

        val customers = ledger.observeKhataCustomers().first()
        val salesBody = ReportBody.Sales(reports.observeSalesReport(range).first(), sales.observeSalesList(range).first().filterNot { it.isCancelled })
        val dueBody = ReportBody.Due(
            khataAllSummary(customers),
            visibleKhataCustomers(customers, query = "", onlyOwing = true, sort = KhataSort.AMOUNT_HIGH_TO_LOW),
            periodSummary(ledger.observeEntriesInRange(range).first().map { it.entry })
        )
        val rows = expenses.observeRowsInRange(range).first()
        val expenseBody = ReportBody.Expenses(expenseSummary(rows), rows.size)
        val profitBody = ReportBody.Profit(reports.observeProfit(range).first())

        check(svc.report(ReportTab.SALES, period, salesBody), "report-sales")
        check(svc.report(ReportTab.DUE, period, dueBody), "report-due")
        check(svc.report(ReportTab.EXPENSES, period, expenseBody), "report-expenses")
        check(svc.report(ReportTab.PROFIT, period, profitBody), "report-profit")
        Unit
    }

    @Test
    fun customerStatementPdf() = runBlocking {
        seed()
        check(service().statement(customerIds[0]), "statement")
        check(service().statement(customerIds[1]), "statement-long-name")
        Unit
    }

    // =====================================================================================
    // Nothing to export, cleaning, privacy
    // =====================================================================================

    @Test
    fun nothingToExportGivesAMessageNotAnEmptyPdf() = runBlocking {
        ShopRepository(db).save(ShopProfile(name = "টেস্ট দোকান"))
        val svc = service()
        assertEquals(PdfResult.Empty(EmptyReason.NO_DUE), svc.dueList())
        assertEquals(PdfResult.Empty(EmptyReason.NO_PRODUCTS), svc.productList(false))
        val id = db.customerDao().insert(Customer(name = "টেস্ট কাস্টমার", createdAt = now, updatedAt = now))
        assertEquals(PdfResult.Empty(EmptyReason.NO_ENTRIES), svc.statement(id))
        assertTrue(File(testContext.cacheDir, PDF_FOLDER).listFiles().isNullOrEmpty())
    }

    @Test
    fun eachExportDeletesTheOlderPdfs() = runBlocking {
        seed()
        val svc = service()
        val first = (svc.dueList() as PdfResult.Done).file
        assertTrue(first.exists())
        val second = (svc.productList(false) as PdfResult.Done).file
        assertTrue(second.exists())
        assertTrue("the older PDF is gone", !first.exists())
        assertEquals(1, File(testContext.cacheDir, PDF_FOLDER).listFiles()!!.size)
        clearPdfFolder(testContext.cacheDir)
        assertTrue(File(testContext.cacheDir, PDF_FOLDER).listFiles().isNullOrEmpty())
    }

    @Test
    fun theStatementFileNameHasNoNameOrPhone() = runBlocking {
        seed()
        val file = (service().statement(customerIds[0]) as PdfResult.Done).file
        assertEquals("statement-${customerIds[0]}-2025-11-09.pdf", file.name)
        Unit
    }
}

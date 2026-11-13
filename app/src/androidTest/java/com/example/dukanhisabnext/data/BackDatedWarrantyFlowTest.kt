package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType
import com.example.dukanhisabnext.data.entity.WarrantyUnit
import com.example.dukanhisabnext.receipt.ReceiptLabels
import com.example.dukanhisabnext.receipt.buildReceiptText
import com.example.dukanhisabnext.receipt.receiptSaleDate
import com.example.dukanhisabnext.receipt.receiptWarrantyCaption
import com.example.dukanhisabnext.receipt.receiptWrittenOn
import com.example.dukanhisabnext.ui.sales.SalesFilter
import com.example.dukanhisabnext.ui.sales.SalesListItem
import com.example.dukanhisabnext.ui.sales.buildSalesPageUi
import com.example.dukanhisabnext.ui.sales.isBackDated
import com.example.dukanhisabnext.ui.sales.salesFilterRange
import com.example.dukanhisabnext.ui.sales.saleRowTitle
import com.example.dukanhisabnext.ui.sales.salesSearchParams
import com.example.dukanhisabnext.ui.sales.toSalesSummary
import com.example.dukanhisabnext.ui.sell.CartStore
import com.example.dukanhisabnext.ui.sell.buildDraft
import com.example.dukanhisabnext.ui.sell.isBackDated
import com.example.dukanhisabnext.ui.sell.summarize
import com.example.dukanhisabnext.util.DhakaTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole flow of a back-dated sale with a warranty, with the real pieces and no screen:
 * the cart (CartStore: the product's default warranty, a line changed for this sale only, an older
 * sale day) -> the draft -> SaleRepository -> the receipt (ReceiptRepository, the shared text) ->
 * the sales list (its queries, its day groups, its period chips). Uses an in-memory database.
 */
@RunWith(AndroidJUnit4::class)
class BackDatedWarrantyFlowTest : DatabaseTestBase() {

    private val today = LocalDate.of(2026, 11, 13)
    private val sold = LocalDate.of(2026, 11, 10)

    private fun at(day: LocalDate, hour: Int = 15, minute: Int = 20): Long =
        LocalDateTime.of(day, LocalTime.of(hour, minute)).atZone(DhakaTime.ZONE).toInstant().toEpochMilli()

    private var clockNow = at(today)

    private val months = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )

    private val labels = ReceiptLabels(
        shopPlaceholder = "বিক্রয় রসিদ", mobile = "মোবাইল", receiptNo = "রসিদ নম্বর", date = "তারিখ", customer = "কাস্টমার",
        subtotal = "সাব-টোটাল", discount = "ছাড়", delivery = "ডেলিভারি চার্জ", grandTotal = "মোট প্রদেয়", paid = "পরিশোধ",
        change = "ফেরত", saleDue = "এই বিক্রির বাকি", customerTotalDue = "আপনার মোট বাকি", note = "মন্তব্য",
        cancelledNotice = "বাতিল করা হয়েছে", cancelReason = "কারণ", taka = "৳",
        unit = { "পিস" }, method = { "নগদ" }, period = { "দুপুর" },
        warrantyUnit = {
            when (it) {
                WarrantyUnit.DAY -> "দিন"
                WarrantyUnit.MONTH -> "মাস"
                WarrantyUnit.YEAR -> "বছর"
            }
        },
        monthNames = months,
        warrantyUntil = { day -> "$day পর্যন্ত" },
        warrantyCaption = { period, until -> "ওয়ারেন্টি: $period ($until)" }
    )

    private suspend fun product(name: String, price: Long, warrantyValue: Int? = null, warrantyUnit: WarrantyUnit? = null): Product {
        val id = db.productDao().insertProduct(
            Product(
                name = name, unit = ProductUnit.PIECE, sellPrice = price, costPrice = price / 2,
                createdAt = now, updatedAt = now, warrantyValue = warrantyValue, warrantyUnit = warrantyUnit
            )
        )
        db.stockMovementDao().insert(StockMovement(productId = id, type = StockMovementType.OPENING, quantity = 50_000, createdAt = now))
        return db.productDao().getProduct(id)!!
    }

    @Test
    fun aBackDatedSaleWithAWarrantyFromTheCartToTheReceiptAndTheSalesList() = runBlocking {
        // A phone with a 6-month default warranty, and a cable without one.
        val phone = product("TEST-ফোন", 150_000, warrantyValue = 6, warrantyUnit = WarrantyUnit.MONTH)
        val cable = product("TEST-কেবল", 5_000)

        // ---- The cart ----
        val cart = CartStore(today = { today }) { "flow-cart" }
        cart.addProduct(phone)
        cart.addProduct(cable)
        assertEquals("the line copies the default", 6, cart.state.value.lines.first { it.productId == phone.id }.warrantyValue)
        cart.setLineWarranty(cable.id, 1, WarrantyUnit.YEAR) // added for this sale only
        cart.setSaleDay(sold)                                 // sold 3 days ago
        assertTrue(cart.state.value.isBackDated(today))
        assertNull("the product is untouched", db.productDao().getProduct(cable.id)!!.warrantyValue)

        // ---- The draft and the save (3 days later than the sale) ----
        val summary = summarize(cart.state.value)
        assertTrue(summary.canContinue)
        val draft = buildDraft(summary, summary.total, PaymentMethod.CASH, null, cart.draftId, null, cart.state.value.saleDay)
        clockNow = at(today)
        val result = SaleRepository(db, { clockNow }).completeSale(draft) as SaleResult.Saved
        assertEquals("261110-001", result.receiptNo)

        // ---- The receipt ----
        val receipt = ReceiptRepository(db).load(result.saleId, 0)!!
        assertTrue(receipt.isBackDated)
        assertEquals(at(sold), receipt.saleDate)
        assertEquals(clockNow, receipt.createdAt)
        assertEquals("১০/১১/২০২৬", receiptSaleDate(receipt, labels))           // the date only
        assertEquals("১৩/১১/২০২৬ দুপুর ৩:২০", receiptWrittenOn(receipt, labels)) // the screen's "লেখা হয়েছে" line
        val phoneLine = receipt.lines.first { it.name == "TEST-ফোন" }
        assertEquals("ওয়ারেন্টি: ৬ মাস (১০ মে ২০২৭ পর্যন্ত)", receiptWarrantyCaption(phoneLine, labels))
        val cableLine = receipt.lines.first { it.name == "TEST-কেবল" }
        assertEquals("ওয়ারেন্টি: ১ বছর (১০ নভেম্বর ২০২৭ পর্যন্ত)", receiptWarrantyCaption(cableLine, labels))

        // The shared text: the sale date, the warranty captions, and no "written on" line.
        val text = buildReceiptText(receipt, labels)
        assertTrue(text, text.contains("তারিখ: ১০/১১/২০২৬"))
        assertEquals(2, text.lines().count { it.contains("ওয়ারেন্টি") })
        assertFalse(text.contains("লেখা"))

        // ---- The sales list ----
        // Today's window does not hold it; the 7-day chip, the month, the year, "সব" and the single day do.
        fun rowsIn(range: DhakaTime.TimeRange) = runBlocking { db.saleDao().observeList(range.startMillis, range.endMillisExclusive).first() }
        assertTrue(rowsIn(salesFilterRange(SalesFilter.TODAY, clockNow)).isEmpty())
        for (chip in listOf(SalesFilter.LAST_7_DAYS, SalesFilter.THIS_MONTH, SalesFilter.THIS_YEAR, SalesFilter.ALL)) {
            assertEquals(chip.name, listOf(result.saleId), rowsIn(salesFilterRange(chip, clockNow)).map { it.id })
        }
        val oneDay = salesFilterRange(SalesFilter.CUSTOM, clockNow, sold, sold)
        assertEquals(listOf(result.saleId), rowsIn(oneDay).map { it.id })
        val dayAfter = salesFilterRange(SalesFilter.CUSTOM, clockNow, sold.plusDays(1), sold.plusDays(1))
        assertTrue(rowsIn(dayAfter).isEmpty())

        // The page of the list: grouped under 10 Nov, the row shows the number without a time.
        val range = salesFilterRange(SalesFilter.LAST_7_DAYS, clockNow)
        val rows = db.saleDao().observeListPage(
            range.startMillis, range.endMillisExclusive, "", "", "", "", 300
        ).first()
        val summaryRow = db.saleDao().observeListSummary(range.startMillis, range.endMillisExclusive, "", "", "", "").first()
        val ui = buildSalesPageUi(rows, summaryRow.toSalesSummary(), 1)
        val header = ui.items.first() as SalesListItem.Header
        assertEquals(sold, header.date)
        val row = (ui.items[1] as SalesListItem.Entry).row
        assertTrue(row.isBackDated())
        assertEquals("২৬১১১০-০০১", saleRowTitle(row, "দুপুর ৩:২০"))
        assertEquals(1, ui.summary.count)

        // The search still finds it (by receipt number) in every window, as before.
        val search = salesSearchParams("261110")
        val found = db.saleDao().observeListPage(0, Long.MAX_VALUE, search.query, search.compactQuery, search.digits, search.digitsWithout88, 300).first()
        assertEquals(listOf(result.saleId), found.map { it.id })

        // Stock came out on the sale day.
        val moved = db.stockMovementDao().observeForProduct(phone.id).first().single { it.type == StockMovementType.SALE }
        assertEquals(at(sold), moved.createdAt)
    }

    @Test
    fun aSaleSavedTodayShowsItsTimeAndNoWrittenOnLine() = runBlocking {
        val phone = product("TEST-ফোন", 150_000, warrantyValue = 7, warrantyUnit = WarrantyUnit.DAY)
        val cart = CartStore(today = { today }) { "flow-cart-2" }
        cart.addProduct(phone)
        val summary = summarize(cart.state.value)
        val draft = buildDraft(summary, summary.total, PaymentMethod.CASH, null, cart.draftId, null, cart.state.value.saleDay)
        val result = SaleRepository(db, { clockNow }).completeSale(draft) as SaleResult.Saved
        assertEquals("261113-001", result.receiptNo)

        val receipt = ReceiptRepository(db).load(result.saleId, 0)!!
        assertFalse(receipt.isBackDated)
        assertEquals("১৩/১১/২০২৬ দুপুর ৩:২০", receiptSaleDate(receipt, labels))
        assertNull(receiptWrittenOn(receipt, labels))
        assertEquals("ওয়ারেন্টি: ৭ দিন (২০ নভেম্বর ২০২৬ পর্যন্ত)", receiptWarrantyCaption(receipt.lines.single(), labels))
        val row = db.saleDao().observeList(0, Long.MAX_VALUE).first().single()
        assertFalse(row.isBackDated())
        assertEquals("২৬১১১৩-০০১  ·  দুপুর ৩:২০", saleRowTitle(row, "দুপুর ৩:২০"))
    }
}

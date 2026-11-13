package com.example.dukanhisabnext.receipt

import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.SaleItem
import com.example.dukanhisabnext.data.entity.WarrantyUnit
import com.example.dukanhisabnext.util.DhakaTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The warranty and the back-dated date on the receipt (version 5, part 2). */
class ReceiptWarrantyTest {

    private val months = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )

    /** The test labels plus the warranty words (the same as strings.xml). */
    private val labels = testLabels.copy(
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

    private val until = LocalDate.of(2027, 5, 13).toEpochDay()
    private val warrantyLine = ReceiptLine("ফোন", 1_000, ProductUnit.PIECE, 150_000, 150_000, 6, WarrantyUnit.MONTH, until)
    private val plainLine = ReceiptLine("কেবল", 1_000, ProductUnit.PIECE, 5_000, 5_000)

    // =====================================================================================
    // The caption under a product
    // =====================================================================================

    @Test
    fun aLineWithAWarrantyPrintsTheCaption() {
        assertEquals("ওয়ারেন্টি: ৬ মাস (১৩ মে ২০২৭ পর্যন্ত)", receiptWarrantyCaption(warrantyLine, labels))
    }

    @Test
    fun aLineWithoutAWarrantyPrintsNothing() {
        assertNull(receiptWarrantyCaption(plainLine, labels))
    }

    @Test
    fun aHalfWarrantyIsNeverPrinted() {
        assertNull(receiptWarrantyCaption(warrantyLine.copy(warrantyUntil = null), labels))
        assertNull(receiptWarrantyCaption(warrantyLine.copy(warrantyUnit = null), labels))
        assertNull(receiptWarrantyCaption(warrantyLine.copy(warrantyValue = null), labels))
    }

    @Test
    fun theSharedTextHasTheCaptionUnderItsProductOnly() {
        val text = buildReceiptText(receipt(lines = listOf(warrantyLine, plainLine), subtotal = 155_000, total = 155_000, paid = 155_000), labels)
        val lines = text.lines()
        val phone = lines.indexOfFirst { it.contains("ফোন") }
        val cable = lines.indexOfFirst { it.contains("কেবল") }
        assertEquals("   ওয়ারেন্টি: ৬ মাস (১৩ মে ২০২৭ পর্যন্ত)", lines[phone + 2]) // name, quantity line, then the caption
        assertEquals(1, lines.count { it.contains("ওয়ারেন্টি") })               // the cable has none
        assertTrue(cable > phone + 2)
        assertFalse(lines[cable + 2].contains("ওয়ারেন্টি"))
    }

    @Test
    fun aReceiptWithoutAnyWarrantyHasNoWarrantyWord() {
        assertFalse(buildReceiptText(receipt(), labels).contains("ওয়ারেন্টি"))
    }

    @Test
    fun theWarrantyTravelsFromTheSavedItemToTheReceiptLine() {
        val item = SaleItem(1, 7, 11, "ফোন", 1_000, 150_000, null, 150_000, 6, WarrantyUnit.MONTH, until)
        val data = buildReceiptData(aSale(), listOf(item, savedItems[1]), units, null, null, null, 0)
        assertEquals(6, data.lines[0].warrantyValue)
        assertEquals(WarrantyUnit.MONTH, data.lines[0].warrantyUnit)
        assertEquals(until, data.lines[0].warrantyUntil)
        assertNull(data.lines[1].warrantyValue)
    }

    // =====================================================================================
    // The date: date only for a back-dated sale; "লেখা হয়েছে" only on the screen
    // =====================================================================================

    private val saved: Long = SALE_TIME // 23:45, 10 Nov 2026 in Dhaka

    /** 9 Nov 2026 at 23:45 in Dhaka: the day before SALE_TIME. */
    private val earlier: Long = LocalDateTime.of(2026, 11, 9, 23, 45).atZone(DhakaTime.ZONE).toInstant().toEpochMilli()

    @Test
    fun aNormalSaleShowsDateAndTime() {
        val data = receipt().copy(saleDate = saved)
        assertFalse(data.isBackDated)
        assertEquals("১০/১১/২০২৬ রাত ১১:৪৫", receiptSaleDate(data, labels))
        assertNull(receiptWrittenOn(data, labels))
    }

    @Test
    fun aBackDatedSaleShowsTheDateOnly() {
        val data = receipt().copy(saleDate = earlier)
        assertTrue(data.isBackDated)
        assertEquals("০৯/১১/২০২৬", receiptSaleDate(data, labels))
    }

    @Test
    fun aBackDatedSaleKnowsWhenItWasReallyWritten() {
        val data = receipt().copy(saleDate = earlier)
        assertEquals("১০/১১/২০২৬ রাত ১১:৪৫", receiptWrittenOn(data, labels))
    }

    @Test
    fun theSharedTextShowsTheSaleDateAndNeverTheWrittenOnLine() {
        val data = receipt().copy(saleDate = earlier)
        val text = buildReceiptText(data, labels)
        assertTrue(text.contains("তারিখ: ০৯/১১/২০২৬"))
        assertFalse(text.contains("১১:৪৫"))     // no time of saving
        assertFalse(text.contains("লেখা"))       // the written-on line is for the screen only
    }

    @Test
    fun theReceiptDataTakesTheSaleDateFromTheSale() {
        val sale = aSale().copy(saleDate = earlier)
        val data = buildReceiptData(sale, savedItems, units, null, null, null, 0)
        assertEquals(earlier, data.saleDate)
        assertEquals(SALE_TIME, data.createdAt)
        assertTrue(data.isBackDated)
        assertFalse(buildReceiptData(aSale(), savedItems, units, null, null, null, 0).isBackDated)
    }

    @Test
    fun theSameDayAtAnotherTimeIsNotBackDated() {
        val morning = Instant.parse("2026-11-10T02:00:00Z").toEpochMilli() // 08:00 Dhaka, same day as SALE_TIME
        assertFalse(receipt().copy(saleDate = morning).isBackDated)
    }
}

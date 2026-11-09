package com.example.dukanhisabnext.ui.products

import com.example.dukanhisabnext.util.StockMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StockFormTest {

    private fun purchase(quantity: String, unitCost: String = "") =
        validateStockForm(StockMode.PURCHASE, quantity, unitCost, counted = "", currentStock = 0)

    private fun correction(counted: String, current: Long) =
        validateStockForm(StockMode.CORRECTION, quantity = "", unitCost = "", counted = counted, currentStock = current)

    // ---- Purchase ----

    @Test
    fun purchaseWithoutPrice() {
        val r = purchase("5") as StockFormResult.Purchase
        assertEquals(5_000L, r.quantity)
        assertNull(r.unitCost)
    }

    @Test
    fun purchaseWithPriceAndBengaliDigits() {
        val r = purchase("২.৫", "১২০.৫০") as StockFormResult.Purchase
        assertEquals(2_500L, r.quantity)
        assertEquals(12_050L, r.unitCost)
    }

    @Test
    fun purchaseQuantityRules() {
        fun quantityError(text: String) = (purchase(text) as StockFormResult.Invalid).errors.quantity
        assertEquals(FormError.REQUIRED, quantityError(""))
        assertEquals(FormError.INVALID_NUMBER, quantityError("abc"))
        assertEquals(FormError.MUST_BE_POSITIVE, quantityError("0"))
    }

    @Test
    fun purchaseBadPriceIsReported() {
        val e = (purchase("5", "1.234") as StockFormResult.Invalid).errors
        assertEquals(FormError.INVALID_NUMBER, e.unitCost)
        assertNull(e.quantity)
    }

    // ---- Correction ----

    @Test
    fun correctionReturnsTheCountedStock() {
        val r = correction("10", current = 12_000) as StockFormResult.Correction
        assertEquals(10_000L, r.counted)
    }

    @Test
    fun correctionToZeroIsAllowed() {
        assertEquals(StockFormResult.Correction(0L), correction("0", current = 3_000))
    }

    @Test
    fun correctionFromNegativeStockIsAllowed() {
        assertEquals(StockFormResult.Correction(2_000L), correction("2", current = -1_000))
    }

    @Test
    fun correctionThatChangesNothingIsRejected() {
        val e = (correction("12", current = 12_000) as StockFormResult.Invalid).errors
        assertEquals(FormError.NO_CHANGE, e.counted)
    }

    @Test
    fun correctionNeedsAReadableNumber() {
        assertEquals(FormError.REQUIRED, (correction("", 1_000) as StockFormResult.Invalid).errors.counted)
        assertEquals(FormError.INVALID_NUMBER, (correction("x", 1_000) as StockFormResult.Invalid).errors.counted)
    }

    // ---- StockMath ----

    @Test
    fun differenceIsCountedMinusCurrent() {
        assertEquals(-2_000L, StockMath.correctionDifference(counted = 10_000, current = 12_000))
        assertEquals(3_500L, StockMath.correctionDifference(counted = 5_000, current = 1_500))
        assertEquals(0L, StockMath.correctionDifference(counted = 700, current = 700))
        assertEquals(2_000L, StockMath.correctionDifference(counted = 1_000, current = -1_000))
    }

    @Test
    fun stockStatusRules() {
        assertEquals(StockStatus.NEGATIVE, stockStatus(-1, lowStockLevel = 5_000))
        assertEquals(StockStatus.NEGATIVE, stockStatus(-1, lowStockLevel = null))
        assertEquals(StockStatus.LOW, stockStatus(5_000, lowStockLevel = 5_000)) // equal counts as low
        assertEquals(StockStatus.LOW, stockStatus(0, lowStockLevel = 5_000))
        assertEquals(StockStatus.NORMAL, stockStatus(5_001, lowStockLevel = 5_000))
        assertEquals(StockStatus.NORMAL, stockStatus(0, lowStockLevel = null))
    }
}

package com.example.dukanhisabnext.ui.products

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductFormTest {

    private fun input(
        name: String = "চাল",
        sellPrice: String = "120",
        costPrice: String = "",
        openingStock: String = "",
        lowStockLevel: String = "",
        isNew: Boolean = true
    ) = ProductFormInput(name, sellPrice, costPrice, openingStock, lowStockLevel, isNew)

    private fun valid(input: ProductFormInput): ValidatedProduct =
        (validateProductForm(input) as ProductFormResult.Valid).product

    private fun errors(input: ProductFormInput): ProductFormErrors =
        (validateProductForm(input) as ProductFormResult.Invalid).errors

    @Test
    fun minimalProductIsValid() {
        val p = valid(input())
        assertEquals("চাল", p.name)
        assertEquals(12_000L, p.sellPrice)
        assertNull(p.costPrice)
        assertNull(p.lowStockLevel)
        assertEquals(0L, p.openingStock) // blank opening stock = 0
    }

    @Test
    fun fullProductWithBengaliDigits() {
        val p = valid(
            input(
                name = "  মিনিকেট চাল  ",
                sellPrice = "১২৫.৫",
                costPrice = "১০০",
                openingStock = "২৫.৫",
                lowStockLevel = "৫"
            )
        )
        assertEquals("মিনিকেট চাল", p.name) // trimmed
        assertEquals(12_550L, p.sellPrice)
        assertEquals(10_000L, p.costPrice)
        assertEquals(25_500L, p.openingStock)
        assertEquals(5_000L, p.lowStockLevel)
    }

    @Test
    fun nameIsRequired() {
        assertEquals(FormError.REQUIRED, errors(input(name = "")).name)
        assertEquals(FormError.REQUIRED, errors(input(name = "   ")).name)
    }

    @Test
    fun sellPriceIsRequiredAndMustBePositive() {
        assertEquals(FormError.REQUIRED, errors(input(sellPrice = "")).sellPrice)
        assertEquals(FormError.INVALID_NUMBER, errors(input(sellPrice = "abc")).sellPrice)
        assertEquals(FormError.INVALID_NUMBER, errors(input(sellPrice = "1.234")).sellPrice)
        assertEquals(FormError.MUST_BE_POSITIVE, errors(input(sellPrice = "0")).sellPrice)
    }

    @Test
    fun costPriceAndLowStockAreOptionalButZeroIsAllowed() {
        val p = valid(input(costPrice = "0", lowStockLevel = "0"))
        assertEquals(0L, p.costPrice)
        assertEquals(0L, p.lowStockLevel)
    }

    @Test
    fun optionalFieldsReportBadNumbers() {
        val e = errors(input(costPrice = "x", openingStock = "1.2345", lowStockLevel = "-3"))
        assertEquals(FormError.INVALID_NUMBER, e.costPrice)
        assertEquals(FormError.INVALID_NUMBER, e.openingStock)
        assertEquals(FormError.INVALID_NUMBER, e.lowStockLevel)
        assertNull(e.name)
        assertNull(e.sellPrice)
    }

    @Test
    fun allErrorsAreReportedAtOnce() {
        val e = errors(input(name = "", sellPrice = ""))
        assertEquals(FormError.REQUIRED, e.name)
        assertEquals(FormError.REQUIRED, e.sellPrice)
        assertTrue(e.hasErrors)
    }

    @Test
    fun openingStockIsIgnoredWhenEditing() {
        // When editing, the opening stock box is hidden, so even junk in it must not matter.
        val p = valid(input(openingStock = "junk", isNew = false))
        assertEquals(0L, p.openingStock)
    }
}

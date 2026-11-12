package com.example.dukanhisabnext.ui.products

import com.example.dukanhisabnext.data.dao.ProductWithStock
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "কম স্টক" filter of the product list: the same rule as the tags on the rows. */
class ProductListFilterTest {

    private fun item(id: Long, stock: Long, level: Long? = null) = ProductWithStock(
        Product(id = id, name = "পণ্য $id", unit = ProductUnit.KG, sellPrice = 100, lowStockLevel = level, createdAt = 0, updatedAt = 0),
        stock
    )

    private val all = listOf(
        item(1, stock = -1_000),                 // negative: low
        item(2, stock = 2_000, level = 5_000),   // below the alert: low
        item(3, stock = 5_000, level = 5_000),   // exactly at the alert: low
        item(4, stock = 6_000, level = 5_000),   // above: fine
        item(5, stock = 0),                      // no alert level and not negative: fine
        item(6, stock = 0, level = 0),           // 0 at a level of 0: low
        item(7, stock = 9_000)                   // plenty, no level: fine
    )

    @Test
    fun aProductIsLowWhenItsTagIsNotNormal() {
        for (p in all) {
            assertEquals("product ${p.product.id}", stockStatus(p.stock, p.product.lowStockLevel) != StockStatus.NORMAL, isLowStock(p))
        }
        assertEquals(listOf(1L, 2L, 3L, 6L), all.filter(::isLowStock).map { it.product.id })
    }

    @Test
    fun withTheChipOffTheListIsUnchanged() {
        assertEquals(all, filterLowStock(all, onlyLowStock = false))
    }

    @Test
    fun withTheChipOnOnlyTheLowProductsRemainInTheSameOrder() {
        assertEquals(listOf(1L, 2L, 3L, 6L), filterLowStock(all, onlyLowStock = true).map { it.product.id })
    }

    @Test
    fun anEmptyListStaysEmpty() {
        assertTrue(filterLowStock(emptyList(), true).isEmpty())
        assertTrue(filterLowStock(listOf(item(4, 6_000, 5_000)), true).isEmpty())
    }

    @Test
    fun theFilterWorksOnTopOfTheSearchAndCategoryResult() {
        // The database has already cut the list by search and category; the chip only narrows that result.
        val searchedAndCategorised = listOf(item(2, 2_000, 5_000), item(4, 6_000, 5_000))
        assertEquals(listOf(2L), filterLowStock(searchedAndCategorised, true).map { it.product.id })
    }

    @Test
    fun theEmptyMessageNamesTheMostSpecificReason() {
        assertEquals(ProductEmptyKind.NO_MATCH, productEmptyKind(searching = true, categoryChosen = false, onlyLowStock = true, showArchived = false))
        assertEquals(ProductEmptyKind.NO_MATCH, productEmptyKind(searching = false, categoryChosen = true, onlyLowStock = false, showArchived = false))
        assertEquals(ProductEmptyKind.LOW_STOCK_NONE, productEmptyKind(false, false, onlyLowStock = true, showArchived = false))
        assertEquals(ProductEmptyKind.LOW_STOCK_NONE, productEmptyKind(false, false, onlyLowStock = true, showArchived = true))
        assertEquals(ProductEmptyKind.NO_ARCHIVED, productEmptyKind(false, false, onlyLowStock = false, showArchived = true))
        assertEquals(ProductEmptyKind.NO_PRODUCTS, productEmptyKind(false, false, onlyLowStock = false, showArchived = false))
        assertFalse(ProductEmptyKind.entries.isEmpty())
    }
}

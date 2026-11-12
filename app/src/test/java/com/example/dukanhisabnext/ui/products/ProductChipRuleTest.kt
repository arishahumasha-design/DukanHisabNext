package com.example.dukanhisabnext.ui.products

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The product list's "সব" chip: selected only when no filter is on. */
class ProductChipRuleTest {

    @Test
    fun allIsSelectedOnlyWithNoFilter() {
        assertTrue(isAllChipSelected(selectedCategoryId = null, onlyLowStock = false))
    }

    @Test
    fun allIsNotSelectedWhileLowStockIsOn() {
        assertFalse(isAllChipSelected(null, onlyLowStock = true))
    }

    @Test
    fun allIsNotSelectedWhileACategoryIsChosen() {
        assertFalse(isAllChipSelected(selectedCategoryId = 3, onlyLowStock = false))
        assertFalse(isAllChipSelected(3, true))
    }

    @Test
    fun afterTappingAllExactlyOneChipLooksSelected() {
        // Tapping "সব" sets the category to none and "কম স্টক" off.
        val category: Long? = null
        val low = false
        val selected = listOf(isAllChipSelected(category, low), low, category != null)
        assertEquals(1, selected.count { it })
    }
}

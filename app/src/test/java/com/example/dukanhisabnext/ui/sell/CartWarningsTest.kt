package com.example.dukanhisabnext.ui.sell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cart's "price below the buying price" warning (it never stops the sale). */
class CartWarningsTest {

    @Test
    fun aPriceBelowTheCostIsWarned() {
        assertTrue(isBelowCost(unitPrice = 7_999, costPrice = 8_000))
        assertTrue(isBelowCost(unitPrice = 0, costPrice = 1))
    }

    @Test
    fun aPriceEqualToOrAboveTheCostIsFine() {
        assertFalse(isBelowCost(8_000, 8_000))
        assertFalse(isBelowCost(10_000, 8_000))
    }

    @Test
    fun noWarningWhileAnythingIsUnknown() {
        assertFalse("price box empty or unreadable", isBelowCost(null, 8_000))
        assertFalse("the product has no buying price", isBelowCost(5_000, null))
        assertFalse(isBelowCost(null, null))
    }
}

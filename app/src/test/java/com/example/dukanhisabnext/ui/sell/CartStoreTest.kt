package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cart store holds the sale in progress: the cart, the customer, the problem marks and the draft id. */
class CartStoreTest {

    private val rice = Product(id = 1, name = "চাল", unit = ProductUnit.KG, sellPrice = 12_000, createdAt = 0, updatedAt = 0)
    private val oil = Product(id = 2, name = "তেল", unit = ProductUnit.LITRE, sellPrice = 20_000, createdAt = 0, updatedAt = 0)

    /** A store whose draft ids are draft-1, draft-2, ... so the tests can see when a new one is made. */
    private fun storeWithCountingIds(): CartStore {
        var next = 0
        return CartStore { "draft-${++next}" }
    }

    // ---- The draft id: ONE per cart ----

    @Test
    fun aNewStoreHasOneDraftId() {
        val store = storeWithCountingIds()
        assertEquals("draft-1", store.draftId)
        assertEquals("draft-1", store.draftId) // reading it does not change it
    }

    @Test
    fun theDraftIdIsNotChangedByEditingTheCart() {
        val store = storeWithCountingIds()
        val id = store.draftId

        store.addProduct(rice)
        store.addProduct(rice)        // another tap
        store.addProduct(oil)
        store.setQuantityText(1, "3")
        store.setUnitPriceText(1, "115")
        store.setDiscountOn(true)
        store.setDiscountUnit(DiscountUnit.PERCENT)
        store.setDiscountText("10")
        store.setDeliveryOn(true)
        store.setDeliveryText("20")
        store.setCustomer(5)
        store.removeLine(2)
        store.markUnavailable(listOf(1))

        assertEquals(id, store.draftId)
    }

    @Test
    fun clearingTheCartMakesANewDraftIdEachTime() {
        val store = storeWithCountingIds()
        val first = store.draftId
        store.addProduct(rice)

        store.clear()
        val second = store.draftId
        store.clear()
        val third = store.draftId

        assertEquals("draft-1", first)
        assertEquals("draft-2", second)
        assertEquals("draft-3", third)
        assertNotEquals(first, second)
    }

    @Test
    fun theRealDraftIdsAreUnique() {
        val store = CartStore() // uses random ids
        val ids = mutableSetOf(store.draftId)
        repeat(50) {
            store.clear()
            ids += store.draftId
        }
        assertEquals(51, ids.size)
        assertTrue(ids.all { it.isNotBlank() })
    }

    // ---- The customer ----

    @Test
    fun theCustomerIsChosenChangedAndRemoved() {
        val store = storeWithCountingIds()
        assertNull(store.customerId.value)

        store.setCustomer(7)
        assertEquals(7L, store.customerId.value)

        store.setCustomer(9)
        assertEquals(9L, store.customerId.value)

        store.setCustomer(null)
        assertNull(store.customerId.value)
    }

    @Test
    fun theCustomerSurvivesCartEdits() {
        val store = storeWithCountingIds()
        store.setCustomer(7)
        store.addProduct(rice)
        store.setQuantityText(1, "2")
        assertEquals(7L, store.customerId.value)
    }

    // ---- Lines the sale could not accept ----

    @Test
    fun markedLinesStayMarkedUntilTheyAreRemoved() {
        val store = storeWithCountingIds()
        store.addProduct(rice)
        store.addProduct(oil)

        store.markUnavailable(listOf(1))
        assertEquals(setOf(1L), store.unavailableProductIds.value)

        store.markUnavailable(listOf(2, 1)) // marking again adds, never duplicates
        assertEquals(setOf(1L, 2L), store.unavailableProductIds.value)

        store.setQuantityText(1, "5") // editing does not clear the mark
        assertEquals(setOf(1L, 2L), store.unavailableProductIds.value)

        store.removeLine(1) // removing the line clears ITS mark only
        assertEquals(setOf(2L), store.unavailableProductIds.value)

        store.removeLine(2)
        assertTrue(store.unavailableProductIds.value.isEmpty())
    }

    // ---- A new cart starts completely fresh ----

    @Test
    fun clearEmptiesEverything() {
        val store = storeWithCountingIds()
        store.addProduct(rice)
        store.setDiscountOn(true)
        store.setDiscountText("10")
        store.setDeliveryOn(true)
        store.setCustomer(7)
        store.markUnavailable(listOf(1))

        store.clear()

        assertEquals(CartState(), store.state.value)
        assertNull(store.customerId.value)
        assertTrue(store.unavailableProductIds.value.isEmpty())
    }
}

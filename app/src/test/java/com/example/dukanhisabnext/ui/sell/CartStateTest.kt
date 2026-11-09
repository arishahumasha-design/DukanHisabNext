package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CartStateTest {

    private val rice = Product(id = 1, name = "চাল", unit = ProductUnit.KG, sellPrice = 12_000, createdAt = 0, updatedAt = 0)
    private val oil = Product(id = 2, name = "তেল", unit = ProductUnit.LITRE, sellPrice = 20_050, createdAt = 0, updatedAt = 0)

    private fun CartState.add(p: Product) = withProductAdded(p.id, p.name, p.unit, p.sellPrice)

    // ---- Tapping products ----

    @Test
    fun firstTapAddsALineWithQuantityOneAndTheProductPrice() {
        val cart = CartState().add(rice)
        val line = cart.lines.single()
        assertEquals(1L, line.productId)
        assertEquals("চাল", line.name)
        assertEquals(ProductUnit.KG, line.unit)
        assertEquals("1", line.quantityText)
        assertEquals("120", line.unitPriceText) // ৳120 as typed text
    }

    @Test
    fun priceWithPaisaIsKeptExactly() {
        assertEquals("200.5", CartState().add(oil).lines.single().unitPriceText)
    }

    @Test
    fun tappingAgainAddsOneMoreOnTheSameLine() {
        val cart = CartState().add(rice).add(rice).add(rice)
        assertEquals(1, cart.lines.size) // same product = one line
        assertEquals("3", cart.lines.single().quantityText)
    }

    @Test
    fun differentProductsAreDifferentLinesInTheOrderAdded() {
        val cart = CartState().add(rice).add(oil).add(rice)
        assertEquals(listOf(1L, 2L), cart.lines.map { it.productId })
        assertEquals(listOf("2", "1"), cart.lines.map { it.quantityText })
    }

    @Test
    fun tappingAddsOneWholeUnitToAFractionalQuantity() {
        val cart = CartState().add(rice).withQuantityText(1, "0.5").add(rice)
        assertEquals("1.5", cart.lines.single().quantityText)
    }

    @Test
    fun tappingWorksWithBengaliDigitsAlreadyConverted() {
        // The box stores normal digits, so "2.250" + 1 = "3.25".
        val cart = CartState().add(rice).withQuantityText(1, "2.250").add(rice)
        assertEquals("3.25", cart.lines.single().quantityText)
    }

    @Test
    fun tappingWhileTheQuantityBoxIsEmptyOrBrokenGivesOne() {
        assertEquals("1", CartState().add(rice).withQuantityText(1, "").add(rice).lines.single().quantityText)
        assertEquals("1", CartState().add(rice).withQuantityText(1, ".").add(rice).lines.single().quantityText)
    }

    @Test
    fun tappingAgainNeverOverwritesAnEditedPrice() {
        val cart = CartState().add(rice).withUnitPriceText(1, "115").add(rice)
        assertEquals("115", cart.lines.single().unitPriceText)
        assertEquals("2", cart.lines.single().quantityText)
    }

    @Test
    fun aSillyHugeQuantityDoesNotOverflowWhenTapped() {
        val huge = "9223372036854775" // about Long.MAX_VALUE / 1000
        val cart = CartState().add(rice).withQuantityText(1, huge).add(rice)
        assertTrue(cart.lines.single().quantityText.isNotEmpty()) // no crash
    }

    // ---- Editing ----

    @Test
    fun editingOneLineLeavesTheOthersAlone() {
        val cart = CartState().add(rice).add(oil)
            .withQuantityText(2, "3")
            .withUnitPriceText(2, "190")
        assertEquals("1", cart.lines[0].quantityText)
        assertEquals("120", cart.lines[0].unitPriceText)
        assertEquals("3", cart.lines[1].quantityText)
        assertEquals("190", cart.lines[1].unitPriceText)
    }

    @Test
    fun removingALine() {
        val cart = CartState().add(rice).add(oil).withoutLine(1)
        assertEquals(listOf(2L), cart.lines.map { it.productId })
    }

    @Test
    fun removedProductCanBeAddedAgainFresh() {
        val cart = CartState().add(rice).withQuantityText(1, "5").withoutLine(1).add(rice)
        assertEquals("1", cart.lines.single().quantityText)
    }

    @Test
    fun discountAndDeliveryAreKeptAsTyped() {
        val cart = CartState().withDiscountText("5.5").withDeliveryText("20")
        assertEquals("5.5", cart.discountText)
        assertEquals("20", cart.deliveryText)
    }

    @Test
    fun switchesAndUnitAreStoredAndTheTextIsKeptWhileOff() {
        val cart = CartState()
            .withDiscountOn(true).withDiscountUnit(DiscountUnit.PERCENT).withDiscountText("10")
            .withDeliveryOn(true).withDeliveryText("20")
        assertTrue(cart.discountOn)
        assertEquals(DiscountUnit.PERCENT, cart.discountUnit)
        assertTrue(cart.deliveryOn)

        // Switching off keeps what was typed, so switching on again brings it back.
        val off = cart.withDiscountOn(false).withDeliveryOn(false)
        assertEquals("10", off.discountText)
        assertEquals("20", off.deliveryText)
        assertEquals("10", off.withDiscountOn(true).discountText)
    }

    @Test
    fun aNewCartStartsWithBothSwitchesOffAndTakaSelected() {
        val cart = CartState()
        assertEquals(false, cart.discountOn)
        assertEquals(false, cart.deliveryOn)
        assertEquals(DiscountUnit.TAKA, cart.discountUnit)
    }

    @Test
    fun storeKeepsTheSwitchesAndTheyAreClearedWithTheCart() {
        val store = CartStore()
        store.setDiscountOn(true)
        store.setDiscountUnit(DiscountUnit.PERCENT)
        store.setDiscountText("5")
        store.setDeliveryOn(true)
        store.setDeliveryText("30")
        assertEquals(CartState(emptyList(), true, DiscountUnit.PERCENT, "5", true, "30"), store.state.value)

        store.clear()
        assertEquals(CartState(), store.state.value)
    }

    // ---- The shared cart store ----

    @Test
    fun storeKeepsTheCartAndClearEmptiesIt() {
        val store = CartStore()
        store.addProduct(rice)
        store.addProduct(rice)
        store.addProduct(oil)
        store.setDiscountText("10")
        store.setQuantityText(2, "4")

        val state = store.state.value
        assertEquals(listOf("2", "4"), state.lines.map { it.quantityText })
        assertEquals("10", state.discountText)

        store.removeLine(1)
        assertEquals(1, store.state.value.lines.size)

        store.clear()
        assertEquals(CartState(), store.state.value)
    }

    @Test
    fun theSameStoreGivesTheSameCartToEveryScreen() {
        // Rotation and tab switching create new screens/ViewModels, but the store stays.
        val store = CartStore()
        store.addProduct(rice)
        val seenByAnotherScreen = store.state.value
        assertEquals("1", seenByAnotherScreen.lines.single().quantityText)
    }
}

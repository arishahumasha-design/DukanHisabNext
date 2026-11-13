package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.WarrantyUnit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cart's sale date and line warranties (version 5, part 2): a saved future day going back to today,
 * the 30-day question, and the warranty copy from the product. (The cart FILE of versions 1 and 2 is
 * tested on the phone, in CartFileVersionTest: org.json only exists there.)
 */
class CartSaleDateTest {

    private val today = LocalDate.of(2026, 11, 13)

    private val phone = Product(
        id = 1, name = "ফোন", unit = ProductUnit.PIECE, sellPrice = 150_000, createdAt = 0, updatedAt = 0,
        warrantyValue = 6, warrantyUnit = WarrantyUnit.MONTH
    )
    private val cable = Product(id = 2, name = "কেবল", unit = ProductUnit.PIECE, sellPrice = 5_000, createdAt = 0, updatedAt = 0)

    private fun cartWith(vararg lines: CartLineInput, saleDay: LocalDate? = null, confirmed: LocalDate? = null) =
        CartState(lines = lines.toList(), saleDay = saleDay, oldDateConfirmedDay = confirmed)

    private fun line(id: Long, value: Int? = null, unit: WarrantyUnit? = null) = CartLineInput(
        productId = id, name = "পণ্য $id", unit = ProductUnit.PIECE, quantityText = "1", unitPriceText = "100",
        warrantyValue = value, warrantyUnit = unit
    )

    // =====================================================================================
    // A saved day that is not allowed any more goes back to today
    // =====================================================================================

    @Test
    fun aFutureDayGoesBackToToday() {
        val (fixed, reset) = cartWith(line(1), saleDay = today.plusDays(1), confirmed = today.plusDays(1)).withValidSaleDay(today)
        assertNull(fixed.saleDay)
        assertNull(fixed.oldDateConfirmedDay)
        assertTrue(reset)
    }

    @Test
    fun aDayBefore2020GoesBackToToday() {
        val (fixed, reset) = cartWith(line(1), saleDay = LocalDate.of(2019, 12, 31)).withValidSaleDay(today)
        assertNull(fixed.saleDay)
        assertTrue(reset)
    }

    @Test
    fun anAllowedDayIsKept() {
        for (day in listOf<LocalDate?>(null, today, today.minusDays(5), LocalDate.of(2020, 1, 1))) {
            val (fixed, reset) = cartWith(line(1), saleDay = day).withValidSaleDay(today)
            assertEquals(day, fixed.saleDay)
            assertFalse(reset)
        }
    }

    /** A storage that already holds a cart, like the file after a restart. */
    private class HoldsCart(private val cart: SavedCart) : CartStorage {
        var saved: SavedCart? = null
        override fun load(): SavedCart? = cart
        override fun save(cart: SavedCart) { saved = cart }
        override fun delete() {}
    }

    @Test
    fun theStoreResetsAFutureDayOnStartAndSaysSo() {
        val storage = HoldsCart(SavedCart(cartWith(line(1), saleDay = today.plusDays(2)), null, "d"))
        val store = CartStore(storage = storage, today = { today })
        assertNull(store.state.value.saleDay)
        assertTrue(store.dateReset.value)
        store.dismissDateReset()
        assertFalse(store.dateReset.value)
    }

    @Test
    fun theStoreKeepsAnOldDayWithoutANote() {
        val old = today.minusDays(40)
        val storage = HoldsCart(SavedCart(cartWith(line(1), saleDay = old, confirmed = old), null, "d"))
        val store = CartStore(storage = storage, today = { today })
        assertEquals(old, store.state.value.saleDay)
        assertEquals(old, store.state.value.oldDateConfirmedDay)
        assertFalse(store.dateReset.value)
    }

    @Test
    fun aClearedCartForgetsTheDay() {
        val storage = HoldsCart(SavedCart(cartWith(line(1), saleDay = today.minusDays(3)), null, "d"))
        val store = CartStore(storage = storage, today = { today })
        store.clear()
        assertNull(store.state.value.saleDay)
        assertNull(store.state.value.oldDateConfirmedDay)
    }

    // =====================================================================================
    // Picking the day, and the 30-day question
    // =====================================================================================

    @Test
    fun pickingTodayStoresNoDayAndAnEarlierDayStoresIt() {
        val state = CartState()
        assertNull(state.withSaleDay(today, today).saleDay)
        assertEquals(today.minusDays(2), state.withSaleDay(today.minusDays(2), today).saleDay)
        assertEquals(today, state.saleDayOrToday(today))
        assertEquals(today.minusDays(2), state.withSaleDay(today.minusDays(2), today).saleDayOrToday(today))
    }

    @Test
    fun theCartIsBackDatedOnlyForAnEarlierDay() {
        assertFalse(CartState().isBackDated(today))
        assertFalse(CartState(saleDay = today).isBackDated(today))
        assertTrue(CartState(saleDay = today.minusDays(1)).isBackDated(today))
    }

    @Test
    fun thirtyDaysAskNothingAndThirtyOneAsk() {
        assertFalse(CartState(saleDay = today.minusDays(30)).needsOldDateQuestion(today))
        assertTrue(CartState(saleDay = today.minusDays(31)).needsOldDateQuestion(today))
        assertFalse(CartState().needsOldDateQuestion(today)) // today
        assertFalse(CartState(saleDay = today.minusDays(5)).needsOldDateQuestion(today))
    }

    @Test
    fun aConfirmedOldDayDoesNotAskAgainButAnotherDayDoes() {
        val day = today.minusDays(45)
        val confirmed = CartState(saleDay = day).withOldDateConfirmed(day)
        assertFalse(confirmed.needsOldDateQuestion(today))
        assertFalse(confirmed.needsOldDateQuestion(today.plusDays(3))) // the day grows older: still confirmed
        // A different old day was not confirmed.
        assertTrue(confirmed.copy(saleDay = day.minusDays(1)).needsOldDateQuestion(today))
    }

    @Test
    fun aDayThatCrossesThirtyWhileTheCartWaitsAsksThen() {
        val day = today.minusDays(30)
        val state = CartState(saleDay = day)
        assertFalse(state.needsOldDateQuestion(today))
        assertTrue(state.needsOldDateQuestion(today.plusDays(1))) // the next morning it is 31 days old
    }

    // =====================================================================================
    // The line warranty
    // =====================================================================================

    @Test
    fun aNewLineCopiesTheProductsDefaultWarranty() {
        val state = CartState().withProductAdded(phone.id, phone.name, phone.unit, phone.sellPrice, phone.warrantyValue, phone.warrantyUnit)
        assertEquals(6, state.lines.single().warrantyValue)
        assertEquals(WarrantyUnit.MONTH, state.lines.single().warrantyUnit)
    }

    @Test
    fun aProductWithoutWarrantyGivesALineWithoutWarranty() {
        val state = CartState().withProductAdded(cable.id, cable.name, cable.unit, cable.sellPrice, cable.warrantyValue, cable.warrantyUnit)
        assertNull(state.lines.single().warrantyValue)
        assertNull(state.lines.single().warrantyUnit)
    }

    @Test
    fun theStoreCopiesTheDefaultWhenAProductIsAdded() {
        val store = CartStore(today = { today })
        store.addProduct(phone)
        store.addProduct(cable)
        assertEquals(6, store.state.value.lines.first { it.productId == 1L }.warrantyValue)
        assertNull(store.state.value.lines.first { it.productId == 2L }.warrantyValue)
    }

    @Test
    fun tappingTheSameProductAgainLeavesTheLinesWarrantyAlone() {
        val store = CartStore(today = { today })
        store.addProduct(phone)
        store.setLineWarranty(1, 2, WarrantyUnit.YEAR) // changed for this sale
        store.addProduct(phone)                         // +1 piece
        val l = store.state.value.lines.single()
        assertEquals("2", l.quantityText)
        assertEquals(2, l.warrantyValue)
        assertEquals(WarrantyUnit.YEAR, l.warrantyUnit)
    }

    @Test
    fun aLineWarrantyCanBeChangedRemovedOrAddedForThisSaleOnly() {
        val store = CartStore(today = { today })
        store.addProduct(phone)
        store.addProduct(cable)
        store.setLineWarranty(1, 10, WarrantyUnit.DAY)  // change
        store.setLineWarranty(2, 1, WarrantyUnit.YEAR)  // add
        assertEquals(10, store.state.value.lines.first { it.productId == 1L }.warrantyValue)
        assertEquals(WarrantyUnit.YEAR, store.state.value.lines.first { it.productId == 2L }.warrantyUnit)
        store.setLineWarranty(1, null, null)             // remove
        assertNull(store.state.value.lines.first { it.productId == 1L }.warrantyValue)
        // The product itself is never touched.
        assertEquals(6, phone.warrantyValue)
        assertNull(cable.warrantyValue)
    }

    // =====================================================================================
    // The draft carries the day and the warranties
    // =====================================================================================

    @Test
    fun theSaleDraftCarriesTheSaleDayAndEachLinesWarranty() {
        val state = CartState().withProductAdded(1, "ফোন", ProductUnit.PIECE, 10_000, 6, WarrantyUnit.MONTH)
            .withProductAdded(2, "কেবল", ProductUnit.PIECE, 5_000)
        val draft = buildDraft(summarize(state), 15_000, com.example.dukanhisabnext.data.entity.PaymentMethod.CASH, null, "d", saleDay = today.minusDays(3))
        assertEquals(today.minusDays(3), draft.saleDay)
        assertEquals(6, draft.lines[0].warrantyValue)
        assertEquals(WarrantyUnit.MONTH, draft.lines[0].warrantyUnit)
        assertNull(draft.lines[1].warrantyValue)
        // Without a day (today) the draft has none.
        assertNull(buildDraft(summarize(state), 15_000, com.example.dukanhisabnext.data.entity.PaymentMethod.CASH, null, "d").saleDay)
    }
}

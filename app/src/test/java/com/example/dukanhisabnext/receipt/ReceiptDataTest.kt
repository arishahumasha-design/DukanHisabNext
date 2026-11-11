package com.example.dukanhisabnext.receipt

import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.data.entity.ShopProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptDataTest {

    private fun build(
        sale: Sale = aSale(),
        customer: Customer? = null,
        shop: ShopProfile? = shop(),
        balance: Long? = null,
        change: Long = 0
    ) = buildReceiptData(sale, savedItems, units, customer, shop, balance, change)

    // ---- The change (ফেরত) decision: shown only when above 0 ----

    @Test
    fun changeIsShownOnlyWhenAboveZero() {
        assertTrue(shouldShowChange(1))
        assertTrue(shouldShowChange(30_000))
        assertTrue(shouldShowChange(Long.MAX_VALUE))
        assertFalse(shouldShowChange(0))
        assertFalse(shouldShowChange(-1))
        assertFalse(shouldShowChange(Long.MIN_VALUE))
    }

    @Test
    fun aReceiptRightAfterASaveWithChangeShowsTheChangeLine() {
        val data = build(change = 30_000)
        assertEquals(30_000L, data.change)
        assertTrue(data.showChange)
    }

    @Test
    fun aReceiptRightAfterASaveWithoutChangeHasNoChangeLine() {
        val data = build(change = 0)
        assertFalse(data.showChange)
    }

    @Test
    fun aReceiptOpenedLaterHasNoChangeLine() {
        // From the sales list the route has no change value, so the default 0 is used.
        val data = build() // change defaults to 0
        assertEquals(0L, data.change)
        assertFalse(data.showChange)
    }

    @Test
    fun aNegativeChangeFromTheRouteIsTreatedAsZero() {
        val data = build(change = -500)
        assertEquals(0L, data.change)
        assertFalse(data.showChange)
    }

    // ---- The customer ----

    @Test
    fun aSaleWithoutACustomerHasNoCustomerLines() {
        val data = build(customer = null, balance = 99_000)
        assertNull(data.customerName)
        assertNull(data.customerPhone)
        assertNull(data.customerTotalDue) // no customer: never a total due
    }

    @Test
    fun aSaleWithACustomerShowsNameAndMobile() {
        val data = build(sale = aSale(customerId = 3), customer = customer())
        assertEquals("রহিম", data.customerName)
        assertEquals("01811000000", data.customerPhone)
    }

    @Test
    fun aCustomerWithoutAMobileHasNoMobileLine() {
        val data = build(sale = aSale(customerId = 3), customer = customer(phone = null))
        assertEquals("রহিম", data.customerName)
        assertNull(data.customerPhone)
    }

    @Test
    fun theCustomersCurrentTotalDueIsShownOnlyWhileTheyOweMoney() {
        val withCustomer = aSale(customerId = 3)
        assertEquals(15_500L, build(sale = withCustomer, customer = customer(), balance = 15_500).customerTotalDue)
        assertNull(build(sale = withCustomer, customer = customer(), balance = 0).customerTotalDue)
        assertNull(build(sale = withCustomer, customer = customer(), balance = -2_000).customerTotalDue) // paid ahead
        assertNull(build(sale = withCustomer, customer = customer(), balance = null).customerTotalDue)
    }

    // ---- The shop ----

    @Test
    fun theShopDetailsAreTaken() {
        val data = build(shop = shop())
        assertEquals("করিম স্টোর", data.shopName)
        assertEquals("বাজার রোড, ঢাকা", data.shopAddress)
        assertEquals("01711000000", data.shopPhone)
    }

    @Test
    fun noShopProfileGivesNullsNotACrash() {
        val data = build(shop = null)
        assertNull(data.shopName)
        assertNull(data.shopAddress)
        assertNull(data.shopPhone)
    }

    @Test
    fun blankShopFieldsCountAsNotFilledIn() {
        val data = build(shop = ShopProfile(name = "   ", ownerName = null, phone = "", address = "  "))
        assertNull(data.shopName)
        assertNull(data.shopAddress)
        assertNull(data.shopPhone)
    }

    // ---- Lines and amounts ----

    @Test
    fun linesUseTheSnapshotNameTheProductsUnitAndTheSoldPrices() {
        val data = build()
        assertEquals(listOf(riceLine, soapLine), data.lines)
        assertEquals(ProductUnit.KG, data.lines[0].unit)
        assertEquals(ProductUnit.PIECE, data.lines[1].unit)
    }

    @Test
    fun aMissingProductUnitIsNullNotACrash() {
        val data = buildReceiptData(aSale(), savedItems, emptyMap(), null, null, null, 0)
        assertNull(data.lines[0].unit)
    }

    @Test
    fun amountsAreCopiedFromTheSale() {
        val data = build(
            sale = aSale(
                subtotal = 25_000, discount = 2_500, deliveryCharge = 3_000, total = 25_500,
                paid = 20_000, method = PaymentMethod.BKASH, due = 5_500, note = "পরে দেবে"
            )
        )
        assertEquals(25_000L, data.subtotal)
        assertEquals(2_500L, data.discount)
        assertEquals(3_000L, data.deliveryCharge)
        assertEquals(25_500L, data.total)
        assertEquals(20_000L, data.paid)
        assertEquals(PaymentMethod.BKASH, data.paymentMethod)
        assertEquals(5_500L, data.saleDue)
        assertEquals("পরে দেবে", data.note)
        assertEquals("261110-001", data.receiptNo)
        assertEquals(SALE_TIME, data.createdAt)
    }

    @Test
    fun aBlankNoteIsNoNote() {
        assertNull(build(sale = aSale(note = "  ")).note)
    }

    // ---- Which lines show ----

    @Test
    fun discountDeliveryAndDueShowOnlyWhenAboveZero() {
        val none = build()
        assertFalse(none.showDiscount)
        assertFalse(none.showDelivery)
        assertFalse(none.showSaleDue)

        val all = build(sale = aSale(discount = 1, deliveryCharge = 1, due = 1))
        assertTrue(all.showDiscount)
        assertTrue(all.showDelivery)
        assertTrue(all.showSaleDue)
    }

    @Test
    fun theMethodMattersOnlyWhenSomethingWasPaid() {
        assertTrue(build(sale = aSale(paid = 1)).showMethod)
        assertFalse(build(sale = aSale(paid = 0, total = 25_000, due = 25_000)).showMethod)
    }
}

package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.ShopProfile
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests that a receipt is built from SAVED data (never the cart), and the shop profile row.
 * These only need compiling for now; run them from Android Studio when you are ready.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptAndShopRepositoryTest : DatabaseTestBase() {

    private fun sales() = SaleRepository(db, { now })
    private fun receipts() = ReceiptRepository(db)

    private var draftNo = 0

    private suspend fun product(name: String, price: Long, cost: Long?, unit: ProductUnit = ProductUnit.KG): Long {
        val id = db.productDao().insertProduct(
            Product(name = name, unit = unit, sellPrice = price, costPrice = cost, createdAt = now, updatedAt = now)
        )
        db.stockMovementDao().insert(
            StockMovement(productId = id, type = StockMovementType.OPENING, quantity = 100_000, createdAt = now)
        )
        return id
    }

    /** Saves a real sale of [quantity] thousandths of [productId], like the payment screen does. */
    private suspend fun sell(
        productId: Long,
        quantity: Long,
        price: Long,
        received: Long,
        customerId: Long? = null,
        discount: Long = 0,
        delivery: Long = 0,
        method: PaymentMethod = PaymentMethod.CASH,
        note: String? = null
    ): Long {
        val draft = SaleDraft(
            draftId = "receipt-test-${draftNo++}",
            lines = listOf(SaleDraftLine(productId, quantity, price)),
            discount = discount, deliveryCharge = delivery, received = received,
            paymentMethod = method, customerId = customerId, note = note
        )
        return (sales().completeSale(draft) as SaleResult.Saved).saleId
    }

    // ---- A saved sale becomes a receipt ----

    @Test
    fun unknownSaleIdGivesNull() = runBlocking {
        assertNull(receipts().load(saleId = 999, change = 0))
    }

    @Test
    fun aReceiptIsLoadedFromTheSavedSale() = runBlocking {
        val rice = product("চাল", price = 4_000, cost = 3_500)
        val saleId = sell(rice, quantity = 2_500, price = 4_000, received = 12_000, method = PaymentMethod.BKASH, note = "নোট")

        val data = receipts().load(saleId, change = 2_000)!!

        assertEquals("261110-001".length, data.receiptNo.length) // YYMMDD-NNN
        assertEquals(now, data.createdAt)
        assertEquals(1, data.lines.size)
        assertEquals("চাল", data.lines[0].name)
        assertEquals(2_500L, data.lines[0].quantity)
        assertEquals(ProductUnit.KG, data.lines[0].unit)
        assertEquals(4_000L, data.lines[0].unitPrice)
        assertEquals(10_000L, data.lines[0].lineTotal)
        assertEquals(10_000L, data.subtotal)
        assertEquals(10_000L, data.total)
        assertEquals(10_000L, data.paid) // never more than the total
        assertEquals(PaymentMethod.BKASH, data.paymentMethod)
        assertEquals(0L, data.saleDue)
        assertEquals("নোট", data.note)
        assertEquals(2_000L, data.change) // the change from the route
        assertTrue(data.showChange)
    }

    @Test
    fun aReceiptOpenedLaterHasNoChange() = runBlocking {
        val rice = product("চাল", 4_000, 3_500)
        val saleId = sell(rice, 1_000, 4_000, received = 10_000)

        val data = receipts().load(saleId, change = 0)!!

        assertEquals(0L, data.change)
        assertFalse(data.showChange)
    }

    @Test
    fun discountAndDeliveryAreTakenFromTheSale() = runBlocking {
        val rice = product("চাল", 4_000, null)
        val saleId = sell(rice, 5_000, 4_000, received = 20_500, discount = 2_000, delivery = 2_500)

        val data = receipts().load(saleId, 0)!!

        assertEquals(20_000L, data.subtotal)
        assertEquals(2_000L, data.discount)
        assertEquals(2_500L, data.deliveryCharge)
        assertEquals(20_500L, data.total)
    }

    @Test
    fun theReceiptKeepsTheNameAndPriceFromTheMomentOfSale() = runBlocking {
        val rice = product("চাল", 4_000, 3_500)
        val saleId = sell(rice, 1_000, 4_000, received = 4_000)

        // Later the product is renamed and re-priced.
        val p = db.productDao().getProduct(rice)!!
        db.productDao().updateProduct(p.copy(name = "মিনিকেট চাল", sellPrice = 9_000))

        val data = receipts().load(saleId, 0)!!
        assertEquals("চাল", data.lines[0].name)
        assertEquals(4_000L, data.lines[0].unitPrice)
    }

    // ---- The customer ----

    @Test
    fun aSaleWithoutACustomerHasNoCustomerData() = runBlocking {
        val rice = product("চাল", 4_000, null)
        val saleId = sell(rice, 1_000, 4_000, received = 4_000)

        val data = receipts().load(saleId, 0)!!

        assertNull(data.customerName)
        assertNull(data.customerPhone)
        assertNull(data.customerTotalDue)
    }

    @Test
    fun theCustomersCurrentTotalDueComesFromTheLedger() = runBlocking {
        val rice = product("চাল", 4_000, null)
        val karim = addCustomer("করিম", phone = "01711000000")
        db.ledgerDao().insert(
            LedgerEntry(customerId = karim, type = LedgerEntryType.OPENING_DUE, amount = 10_000, entryDate = now, createdAt = now)
        )
        // This sale leaves a due of ৳30 (paid 10 of 40).
        val saleId = sell(rice, 1_000, 4_000, received = 1_000, customerId = karim)

        val data = receipts().load(saleId, 0)!!

        assertEquals("করিম", data.customerName)
        assertEquals("01711000000", data.customerPhone)
        assertEquals(3_000L, data.saleDue)
        assertEquals(13_000L, data.customerTotalDue) // the old ৳100 plus this sale's ৳30

        // Later the customer pays everything: opening the same receipt again shows the CURRENT due.
        db.ledgerDao().insert(
            LedgerEntry(customerId = karim, type = LedgerEntryType.PAYMENT_RECEIVED, amount = 13_000, entryDate = now, createdAt = now)
        )
        assertNull(receipts().load(saleId, 0)!!.customerTotalDue)
    }

    @Test
    fun aCustomerWhoDoesNotOweHasNoTotalDueLine() = runBlocking {
        val rice = product("চাল", 4_000, null)
        val karim = addCustomer("করিম")
        val saleId = sell(rice, 1_000, 4_000, received = 4_000, customerId = karim)

        assertNull(receipts().load(saleId, 0)!!.customerTotalDue)
    }

    // ---- The shop profile ----

    @Test
    fun noShopProfileMeansNoShopHeaderAndNoCrash() = runBlocking {
        val rice = product("চাল", 4_000, null)
        val saleId = sell(rice, 1_000, 4_000, received = 4_000)

        val data = receipts().load(saleId, 0)!!

        assertNull(data.shopName)
        assertNull(data.shopAddress)
        assertNull(data.shopPhone)
    }

    @Test
    fun theSavedShopProfileIsUsedOnTheReceipt() = runBlocking {
        val rice = product("চাল", 4_000, null)
        ShopRepository(db).save(ShopProfile(name = "করিম স্টোর", ownerName = "করিম", phone = "01711000000", address = "বাজার রোড"))
        val saleId = sell(rice, 1_000, 4_000, received = 4_000)

        val data = receipts().load(saleId, 0)!!

        assertEquals("করিম স্টোর", data.shopName)
        assertEquals("বাজার রোড", data.shopAddress)
        assertEquals("01711000000", data.shopPhone)
    }

    @Test
    fun theShopProfileIsASingleRowThatIsUpdated() = runBlocking {
        val shop = ShopRepository(db)
        assertNull(shop.get())

        shop.save(ShopProfile(name = "প্রথম নাম"))
        shop.save(ShopProfile(name = "করিম স্টোর", address = "বাজার"))

        assertEquals(1, countRows("shop_profile"))
        assertEquals("করিম স্টোর", shop.get()!!.name)
        assertEquals("বাজার", shop.get()!!.address)
    }

    @Test
    fun savingAProfileWithAnotherIdStillUsesTheSingleRow() = runBlocking {
        val shop = ShopRepository(db)
        shop.save(ShopProfile(id = 5, name = "করিম স্টোর"))
        assertEquals(1, countRows("shop_profile"))
        assertEquals(ShopProfile.SINGLE_ROW_ID, shop.get()!!.id)
    }
}

package com.example.dukanhisabnext.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.ui.sell.CART_FILE_VERSION
import com.example.dukanhisabnext.ui.sell.CartState
import com.example.dukanhisabnext.ui.sell.CartStore
import com.example.dukanhisabnext.ui.sell.DiscountUnit
import com.example.dukanhisabnext.ui.sell.FileCartStorage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M9: the cart survives Android closing the app ("process death"), and a cart that comes back can
 * never be saved twice. A "restart" here = new CartStore / SaleRepository objects reading the same
 * files, exactly what a new app process does. Uses its own files in an isolated context, never the app's real cart.
 */
@RunWith(AndroidJUnit4::class)
class CartPersistenceTest : DatabaseTestBase() {

    // The cart and the journal files of these tests live in the isolated context (DatabaseTestBase.testContext),
    // never in the app's real files folder.
    private val dir get() = File(testContext.cacheDir, "cart-persistence-test")
    private val cartFile get() = File(dir, "cart.json")
    private val journalFile get() = File(dir, "sale-attempts.txt")
    private var ids = 0

    @Before
    fun cleanBefore() {
        dir.deleteRecursively()
        dir.mkdirs()
    }

    @After
    fun cleanAfter() {
        dir.deleteRecursively()
    }

    private fun store() = CartStore(newDraftId = { "draft-${++ids}" }, storage = FileCartStorage(cartFile))

    private fun product(id: Long, name: String, unit: ProductUnit = ProductUnit.KG, price: Long = 7_500) =
        Product(id = id, name = name, unit = unit, sellPrice = price, createdAt = now, updatedAt = now)

    private suspend fun insertProduct(name: String, price: Long = 7_500, active: Boolean = true): Product {
        val id = db.productDao().insertProduct(product(0, name, price = price).copy(isActive = active))
        return db.productDao().getProduct(id)!!
    }

    // ---- the cart file ----

    @Test
    fun everyPartOfTheCartComesBackAfterARestart() {
        val first = store()
        first.addProduct(product(1, "চাল"))
        first.addProduct(product(2, "সাবান", ProductUnit.PIECE, 4_500))
        first.addProduct(product(2, "সাবান", ProductUnit.PIECE, 4_500)) // +1 → 2 pieces
        first.setQuantityText(1, "1.5")
        first.setUnitPriceText(1, "72.50")
        first.setDiscountOn(true)
        first.setDiscountUnit(DiscountUnit.PERCENT)
        first.setDiscountText("5")
        first.setDeliveryOn(true)
        first.setDeliveryText("30")
        first.setCustomer(7)

        // Android closes the app; a new process starts and reads the file.
        val second = store()
        assertEquals(first.state.value, second.state.value)
        assertEquals(7L, second.customerId.value)
        assertEquals(first.draftId, second.draftId) // the SAME cart id: a retry cannot save twice
        assertEquals(listOf("1.5", "2"), second.state.value.lines.map { it.quantityText })
        assertEquals("72.50", second.state.value.lines.first().unitPriceText)
        assertEquals(ProductUnit.PIECE, second.state.value.lines[1].unit)
    }

    @Test
    fun anEmptiedCartComesBackEmptyWithItsNewId() {
        val first = store()
        first.addProduct(product(1, "চাল"))
        val oldId = first.draftId
        first.clear() // after a completed sale
        val second = store()
        assertEquals(CartState(), second.state.value)
        assertNull(second.customerId.value)
        assertEquals(first.draftId, second.draftId)
        assertNotEquals(oldId, second.draftId)
    }

    @Test
    fun aBrokenOrNewerFileGivesAnEmptyCartAndNoCrash() {
        for (text in listOf("", "not json", "{\"version\":${CART_FILE_VERSION + 1},\"draftId\":\"x\",\"lines\":[]}", "{\"version\":1}",
            "{\"version\":1,\"draftId\":\"x\",\"customerId\":null,\"discountOn\":false,\"discountUnit\":\"KILO\",\"discountText\":\"\",\"deliveryOn\":false,\"deliveryText\":\"\",\"lines\":[]}")) {
            cartFile.writeText(text)
            val s = store()
            assertEquals(text, CartState(), s.state.value)
            assertTrue(text, s.draftId.startsWith("draft-"))
        }
    }

    // ---- what can no longer be sold ----

    @Test
    fun linesOfArchivedOrMissingProductsAreTakenOutWithTheirNames() = runBlocking {
        val rice = insertProduct("চাল")
        val soap = insertProduct("সাবান")
        val oil = insertProduct("তেল")
        val first = store()
        first.addProduct(rice)
        first.addProduct(soap)
        first.addProduct(oil)
        first.addProduct(product(999, "পুরনো পণ্য")) // no such product in the database
        val customer = addCustomer("করিম")
        first.setCustomer(customer + 100) // a customer who does not exist

        db.openHelper.writableDatabase.execSQL("UPDATE products SET isActive = 0 WHERE id = ${soap.id}") // archived meanwhile

        val second = store()
        second.dropWhatCannotBeSold(
            sellableIds = { ids -> db.productDao().getProducts(ids).filter { it.isActive }.map { it.id }.toSet() },
            customerExists = { id -> db.customerDao().getById(id) != null }
        )
        assertEquals(listOf(rice.id, oil.id), second.state.value.lines.map { it.productId })
        assertEquals(listOf("সাবান", "পুরনো পণ্য"), second.removedOnRestart.value)
        assertNull(second.customerId.value)
        // The cleaned cart is what the file holds now.
        assertEquals(second.state.value, store().state.value)
        second.dismissRemovedNote()
        assertTrue(second.removedOnRestart.value.isEmpty())
    }

    @Test
    fun aCartWithNothingWrongIsLeftAlone() = runBlocking {
        val rice = insertProduct("চাল")
        val s = store()
        s.addProduct(rice)
        val before = s.state.value
        s.dropWhatCannotBeSold({ ids -> ids.toSet() }, { true })
        assertEquals(before, s.state.value)
        assertTrue(s.removedOnRestart.value.isEmpty())
    }

    // ---- the same cart is never saved twice ----

    private fun repo(failAt: SaveStep? = null) = SaleRepository(
        db, clock = { now }, journal = FileSaleDraftJournal(journalFile),
        afterStep = { if (it == failAt) throw IllegalStateException("crash at $it") }
    )

    private fun countSales() = countRows("sales")

    @Test
    fun theAppClosedAfterTheSaleButBeforeTheCartWasEmptiedSavesOnce() = runBlocking {
        val rice = insertProduct("চাল", price = 10_000)
        val cart = store()
        cart.addProduct(rice)
        val draft = SaleDraft(cart.draftId, listOf(SaleDraftLine(rice.id, 1_000, 10_000)), 0, 0, 10_000, PaymentMethod.CASH, null)

        val first = repo().completeSale(draft)
        assertTrue("$first", first is SaleResult.Saved)
        // ... and here Android closed the app: the cart was NOT emptied.

        val cartAgain = store()
        assertEquals(draft.draftId, cartAgain.draftId)
        val retry = repo().completeSale(draft.copy(draftId = cartAgain.draftId)) // a NEW repository: its memory is empty
        assertEquals(first, retry) // the first sale, not a new one
        assertEquals(1, countSales())
    }

    @Test
    fun aSaveThatCrashedHalfWaySavesNothingAndTheRetrySavesOnce() = runBlocking {
        val rice = insertProduct("চাল", price = 10_000)
        val draft = SaleDraft("cart-1", listOf(SaleDraftLine(rice.id, 1_000, 10_000)), 0, 0, 10_000, PaymentMethod.CASH, null)
        try {
            repo(failAt = SaveStep.ITEMS_SAVED).completeSale(draft)
            fail("the crash did not happen")
        } catch (expected: IllegalStateException) {
        }
        assertEquals(0, countSales()) // the transaction undid everything

        val retry = repo().completeSale(draft)
        assertTrue("$retry", retry is SaleResult.Saved)
        assertEquals(1, countSales())
        assertEquals(retry, repo().completeSale(draft)) // and once more after another "restart"
        assertEquals(1, countSales())
    }

    @Test
    fun aDifferentCartIsAlwaysANewSale() = runBlocking {
        val rice = insertProduct("চাল", price = 10_000)
        val line = listOf(SaleDraftLine(rice.id, 1_000, 10_000))
        repo().completeSale(SaleDraft("cart-a", line, 0, 0, 10_000, PaymentMethod.CASH, null))
        repo().completeSale(SaleDraft("cart-b", line, 0, 0, 10_000, PaymentMethod.CASH, null))
        assertEquals(2, countSales())
    }
}

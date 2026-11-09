package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.Category
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.StockMovementType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the repository that M2's screens use: the multi-step writes must be all-or-nothing. */
@RunWith(AndroidJUnit4::class)
class ProductRepositoryTest : DatabaseTestBase() {

    // The repository gets a fixed clock so the test never depends on the real time.
    private fun repo() = ProductRepository(db) { now }

    private fun newProduct(name: String = "চাল", costPrice: Long? = null, categoryId: Long? = null) = Product(
        name = name, categoryId = categoryId, unit = ProductUnit.KG, sellPrice = 12_000,
        costPrice = costPrice, createdAt = 0, updatedAt = 0
    )

    // ---- Creating a product ----

    @Test
    fun openingStockAndCostPriceAreSavedTogether() = runBlocking {
        val id = repo().createProduct(newProduct(costPrice = 10_000), openingStock = 7_500)

        val saved = db.productDao().getProduct(id)!!
        assertEquals(now, saved.createdAt)       // times come from the repository clock
        assertEquals(10_000L, saved.costPrice)

        val movement = db.stockMovementDao().observeForProduct(id).first().single()
        assertEquals(StockMovementType.OPENING, movement.type)
        assertEquals(7_500L, movement.quantity)
        assertEquals(10_000L, movement.unitCost) // cost price copied onto the OPENING movement
        assertEquals(7_500L, db.stockMovementDao().getStock(id))
    }

    @Test
    fun noOpeningStockMeansNoMovement() = runBlocking {
        val id = repo().createProduct(newProduct(), openingStock = 0)
        assertTrue(db.stockMovementDao().observeForProduct(id).first().isEmpty())
        assertEquals(0L, db.stockMovementDao().getStock(id))
    }

    @Test
    fun openingMovementWithoutCostPriceHasNoUnitCost() = runBlocking {
        val id = repo().createProduct(newProduct(costPrice = null), openingStock = 1_000)
        assertNull(db.stockMovementDao().observeForProduct(id).first().single().unitCost)
    }

    @Test
    fun failedCreateSavesNothing() = runBlocking {
        // Category 999 does not exist, so the database refuses the product.
        assertConstraintFails { repo().createProduct(newProduct(categoryId = 999), openingStock = 5_000) }

        assertTrue(db.productDao().observeProducts(true, "", null).first().isEmpty())
    }

    // ---- Purchases ----

    @Test
    fun purchaseWithCostUpdateChangesProductCostPrice() = runBlocking {
        val r = repo()
        val id = r.createProduct(newProduct(costPrice = 10_000), openingStock = 1_000)

        r.addPurchase(id, quantity = 5_000, unitCost = 11_000, updateCostPrice = true, note = "বাজার")

        assertEquals(11_000L, db.productDao().getProduct(id)!!.costPrice)
        val purchase = db.stockMovementDao().observeForProduct(id).first().first { it.type == StockMovementType.PURCHASE }
        assertEquals(11_000L, purchase.unitCost)
        assertEquals("বাজার", purchase.note)
        assertEquals(6_000L, db.stockMovementDao().getStock(id))
    }

    @Test
    fun purchaseWithCostUpdateOffKeepsCostPriceButStillSavesUnitCost() = runBlocking {
        val r = repo()
        val id = r.createProduct(newProduct(costPrice = 10_000), openingStock = 0)

        r.addPurchase(id, quantity = 2_000, unitCost = 11_000, updateCostPrice = false, note = null)

        assertEquals(10_000L, db.productDao().getProduct(id)!!.costPrice)
        assertEquals(11_000L, db.stockMovementDao().observeForProduct(id).first().single().unitCost)
    }

    @Test
    fun purchaseWithoutPriceNeverTouchesCostPrice() = runBlocking {
        val r = repo()
        val id = r.createProduct(newProduct(costPrice = 10_000), openingStock = 0)

        // Even with the checkbox ON, no price entered means nothing to update.
        r.addPurchase(id, quantity = 2_000, unitCost = null, updateCostPrice = true, note = null)

        assertEquals(10_000L, db.productDao().getProduct(id)!!.costPrice)
        assertNull(db.stockMovementDao().observeForProduct(id).first().single().unitCost)
    }

    @Test
    fun failedPurchaseSavesNothing() = runBlocking {
        assertConstraintFails {
            repo().addPurchase(productId = 999, quantity = 1_000, unitCost = 5_000, updateCostPrice = true, note = null)
        }
        assertEquals(0L, db.stockMovementDao().getStock(999))
    }

    // ---- Stock correction ----

    @Test
    fun correctionSavesOneAdjustmentWithTheDifference() = runBlocking {
        val r = repo()
        val id = r.createProduct(newProduct(), openingStock = 12_000)

        assertTrue(r.correctStock(id, countedStock = 10_000, note = null))

        assertEquals(10_000L, db.stockMovementDao().getStock(id))
        val adjustment = db.stockMovementDao().observeForProduct(id).first().first { it.type == StockMovementType.ADJUSTMENT }
        assertEquals(-2_000L, adjustment.quantity)
    }

    @Test
    fun correctionThatChangesNothingSavesNothing() = runBlocking {
        val r = repo()
        val id = r.createProduct(newProduct(), openingStock = 5_000)

        assertFalse(r.correctStock(id, countedStock = 5_000, note = null))

        assertEquals(1, db.stockMovementDao().observeForProduct(id).first().size) // only the opening one
    }

    // ---- Archive, categories, names ----

    @Test
    fun archivedProductsMoveToTheArchivedList() = runBlocking {
        val r = repo()
        val id = r.createProduct(newProduct(), openingStock = 0)

        r.setArchived(id, archived = true)
        assertTrue(r.observeProducts(isActive = true, query = "", categoryId = null).first().isEmpty())
        assertEquals(1, r.observeProducts(isActive = false, query = "", categoryId = null).first().size)

        r.setArchived(id, archived = false)
        assertEquals(1, r.observeProducts(isActive = true, query = "", categoryId = null).first().size)
    }

    @Test
    fun duplicateCategoryNamesAreRefused() = runBlocking {
        val r = repo()
        assertTrue(r.addCategory("মুদি")!! > 0)
        assertNull(r.addCategory("মুদি"))
        assertNull(r.addCategory("  মুদি  ")) // spaces around the name are ignored
        assertEquals(1, db.productDao().observeCategories().first().size)
    }

    @Test
    fun categoryNameCheckIgnoresUpperAndLowerCase() = runBlocking {
        val r = repo()
        r.addCategory("Snacks")
        assertNull(r.addCategory("snacks"))
    }

    @Test
    fun sameNameCheckIgnoresTheProductItselfAndArchivedProducts() = runBlocking {
        val r = repo()
        val id = r.createProduct(newProduct(name = "চাল"), openingStock = 0)

        assertTrue(r.productNameExists("চাল", excludeId = null))
        assertFalse(r.productNameExists("চাল", excludeId = id)) // editing the product itself is fine

        r.setArchived(id, archived = true)
        assertFalse(r.productNameExists("চাল", excludeId = null)) // archived ones do not count
    }

    @Test
    fun categoriesAreUsableOnProducts() = runBlocking {
        val r = repo()
        val grocery = r.addCategory("মুদি")!!
        r.createProduct(newProduct(categoryId = grocery), openingStock = 0)

        assertEquals(1, r.observeProducts(true, "", grocery).first().size)
        assertEquals(grocery, db.productDao().observeCategories().first().single().id)
        assertEquals(Category(id = grocery, name = "মুদি"), db.productDao().observeCategories().first().single())
    }
}

package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.Category
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductStockDaoTest : DatabaseTestBase() {

    private fun movement(productId: Long, type: StockMovementType, quantity: Long) =
        StockMovement(productId = productId, type = type, quantity = quantity, createdAt = now)

    @Test
    fun stockIsTheSumOfMovements() = runBlocking {
        val rice = addProduct("চাল")
        val stock = db.stockMovementDao()

        assertEquals(0L, stock.getStock(rice)) // no movements yet

        stock.insert(movement(rice, StockMovementType.OPENING, 10_000))   // +10
        stock.insert(movement(rice, StockMovementType.PURCHASE, 5_500))   // +5.5
        stock.insert(movement(rice, StockMovementType.SALE, -2_250))      // -2.25
        stock.insert(movement(rice, StockMovementType.ADJUSTMENT, -250))  // -0.25

        assertEquals(13_000L, stock.getStock(rice))
    }

    @Test
    fun stockMayGoNegative() = runBlocking {
        val soap = addProduct("সাবান")
        db.stockMovementDao().insert(movement(soap, StockMovementType.SALE, -3_000))
        assertEquals(-3_000L, db.stockMovementDao().getStock(soap))
    }

    @Test
    fun productListShowsStockAndHidesArchivedProducts() = runBlocking {
        val rice = addProduct("চাল")
        val oil = addProduct("তেল")
        db.stockMovementDao().insert(movement(rice, StockMovementType.OPENING, 7_000))
        db.productDao().setActive(oil, isActive = false, updatedAt = now)

        val list = db.productDao().observeProducts(isActive = true, query = "", categoryId = null).first()

        assertEquals(1, list.size)
        assertEquals("চাল", list[0].product.name)
        assertEquals(7_000L, list[0].stock)
    }

    @Test
    fun productSearchAndCategoryFilter() = runBlocking {
        val grocery = db.productDao().insertCategory(Category(name = "মুদি"))
        addProduct("মিনিকেট চাল", grocery)
        addProduct("নাজিরশাইল চাল", grocery)
        addProduct("চালের গুঁড়া")          // no category
        addProduct("ডাল", grocery)

        val search = db.productDao().observeProducts(isActive = true, query = "চাল", categoryId = null).first()
        assertEquals(3, search.size)

        val searchInCategory = db.productDao().observeProducts(isActive = true, query = "চাল", categoryId = grocery).first()
        assertEquals(2, searchInCategory.size)
    }

    @Test
    fun allStocksAreSummedPerProduct() = runBlocking {
        val rice = addProduct("চাল")
        val oil = addProduct("তেল")
        addProduct("সাবান") // no movements at all: no row
        val stock = db.stockMovementDao()
        stock.insert(movement(rice, StockMovementType.OPENING, 10_000))
        stock.insert(movement(rice, StockMovementType.SALE, -2_500))
        stock.insert(movement(oil, StockMovementType.SALE, -1_000)) // negative stock

        val rows = stock.observeAllStocks().first().associate { it.productId to it.stock }

        assertEquals(mapOf(rice to 7_500L, oil to -1_000L), rows)
    }

    @Test
    fun repositoryStockMapMatchesTheMovements() = runBlocking {
        val rice = addProduct("চাল")
        db.stockMovementDao().insert(movement(rice, StockMovementType.OPENING, 4_000))

        val map = com.example.dukanhisabnext.data.ProductRepository(db) { now }.observeStocks().first()

        assertEquals(4_000L, map[rice])
        assertEquals(null, map[999L]) // unknown product: not in the map (the screen treats it as 0)
    }

    @Test
    fun movementForUnknownProductIsRejected() = runBlocking {
        assertConstraintFails {
            db.stockMovementDao().insert(movement(productId = 999, StockMovementType.PURCHASE, 1_000))
        }
    }
}

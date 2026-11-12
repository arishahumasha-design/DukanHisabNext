package com.example.dukanhisabnext.data

import androidx.room.withTransaction
import com.example.dukanhisabnext.data.dao.ProductWithStock
import com.example.dukanhisabnext.data.entity.Category
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType
import com.example.dukanhisabnext.util.StockMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The one place that changes products and stock. Anything that writes to more
 * than one table runs inside db.withTransaction { }: either every step is saved,
 * or none is (SPEC.md section 4).
 *
 * [clock] gives "now" in UTC millis. Tests pass a fixed clock.
 */
class ProductRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val productDao = db.productDao()
    private val stockDao = db.stockMovementDao()

    // ---- Reading (live Flows: the screen updates itself) ----

    fun observeProducts(isActive: Boolean, query: String, categoryId: Long?): Flow<List<ProductWithStock>> =
        productDao.observeProducts(isActive, query, categoryId)

    fun observeCategories(): Flow<List<Category>> = productDao.observeCategories()

    fun observeStock(productId: Long): Flow<Long> = stockDao.observeStock(productId)

    /** Current stock per product id (thousandths). A product missing from the map has stock 0. */
    fun observeStocks(): Flow<Map<Long, Long>> =
        stockDao.observeAllStocks().map { rows -> rows.associate { it.productId to it.stock } }

    /** The buying price (paisa) of each of these products that has one. Products without a cost price are left out. */
    suspend fun costPricesOf(ids: List<Long>): Map<Long, Long> =
        if (ids.isEmpty()) emptyMap()
        else productDao.getProducts(ids).mapNotNull { p -> p.costPrice?.let { p.id to it } }.toMap()

    fun observeMovements(productId: Long): Flow<List<StockMovement>> = stockDao.observeForProduct(productId)

    suspend fun getProduct(id: Long): Product? = productDao.getProduct(id)

    suspend fun productNameExists(name: String, excludeId: Long?): Boolean =
        productDao.countActiveProductsNamed(name.trim(), excludeId ?: -1L) > 0

    // ---- Categories ----

    /** Returns the new category id, or null if a category with this name already exists. */
    suspend fun addCategory(name: String): Long? {
        val clean = name.trim()
        if (productDao.countCategoriesNamed(clean) > 0) return null
        return productDao.insertCategory(Category(name = clean))
    }

    // ---- Products ----

    /**
     * Saves a new product. If [openingStock] > 0 it also saves one OPENING stock movement,
     * carrying the product's cost price as unitCost. Both writes happen in one transaction.
     * The id, createdAt and updatedAt inside [product] are ignored and set here.
     */
    suspend fun createProduct(product: Product, openingStock: Long): Long = db.withTransaction {
        val now = clock()
        val id = productDao.insertProduct(product.copy(id = 0, createdAt = now, updatedAt = now))
        if (openingStock > 0) {
            stockDao.insert(
                StockMovement(
                    productId = id,
                    type = StockMovementType.OPENING,
                    quantity = openingStock,
                    unitCost = product.costPrice,
                    createdAt = now
                )
            )
        }
        id
    }

    suspend fun updateProduct(product: Product) {
        productDao.updateProduct(product.copy(updatedAt = clock()))
    }

    /** Archive hides a product from selling; restoring brings it back. Old sales stay intact. */
    suspend fun setArchived(id: Long, archived: Boolean) {
        productDao.setActive(id, isActive = !archived, updatedAt = clock())
    }

    // ---- Stock ----

    /**
     * Stock bought. Always saves [unitCost] (if given) on the PURCHASE movement.
     * If [updateCostPrice] is true and [unitCost] is given, the product's cost price is
     * updated in the same transaction.
     */
    suspend fun addPurchase(
        productId: Long,
        quantity: Long,
        unitCost: Long?,
        updateCostPrice: Boolean,
        note: String?
    ) {
        require(quantity > 0) { "Purchase quantity must be greater than 0" }
        db.withTransaction {
            val now = clock()
            stockDao.insert(
                StockMovement(
                    productId = productId,
                    type = StockMovementType.PURCHASE,
                    quantity = quantity,
                    unitCost = unitCost,
                    note = note,
                    createdAt = now
                )
            )
            if (updateCostPrice && unitCost != null) {
                productDao.setCostPrice(productId, unitCost, now)
            }
        }
    }

    /**
     * Fixes stock after counting: saves ONE ADJUSTMENT movement with the difference between
     * [countedStock] and the current stock. Returns false (and saves nothing) when they
     * already match.
     */
    suspend fun correctStock(productId: Long, countedStock: Long, note: String?): Boolean =
        db.withTransaction {
            // Read the current stock INSIDE the transaction so the difference is exact.
            val difference = StockMath.correctionDifference(countedStock, stockDao.getStock(productId))
            if (difference == 0L) {
                false
            } else {
                stockDao.insert(
                    StockMovement(
                        productId = productId,
                        type = StockMovementType.ADJUSTMENT,
                        quantity = difference,
                        note = note,
                        createdAt = clock()
                    )
                )
                true
            }
        }
}

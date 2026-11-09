package com.example.dukanhisabnext.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.dukanhisabnext.data.entity.Category
import com.example.dukanhisabnext.data.entity.Product
import kotlinx.coroutines.flow.Flow

/** A product plus its current stock (thousandths), computed from stock movements. */
data class ProductWithStock(
    @Embedded val product: Product, // @Embedded = "all the Product columns go here"
    val stock: Long
)

@Dao
interface ProductDao {
    // ---- Categories ----

    @Insert
    suspend fun insertCategory(category: Category): Long // returns the new id

    @Update
    suspend fun updateCategory(category: Category)

    /** How many categories have this name (ignoring upper/lower case). Used to block duplicates. */
    @Query("SELECT COUNT(*) FROM categories WHERE name = :name COLLATE NOCASE")
    suspend fun countCategoriesNamed(name: String): Int

    @Query("SELECT * FROM categories ORDER BY name")
    fun observeCategories(): Flow<List<Category>>

    // ---- Products ----

    @Insert
    suspend fun insertProduct(product: Product): Long

    @Update
    suspend fun updateProduct(product: Product)

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProduct(id: Long): Product?

    /** Archive (false) or un-archive (true). Old sales keep pointing at the product. */
    @Query("UPDATE products SET isActive = :isActive, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setActive(id: Long, isActive: Boolean, updatedAt: Long)

    /** Only changes the cost price (used when a purchase also updates the cost price). */
    @Query("UPDATE products SET costPrice = :costPrice, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCostPrice(id: Long, costPrice: Long, updatedAt: Long)

    /**
     * How many ACTIVE products share this name (ignoring case), not counting [excludeId].
     * Used for a gentle "same name exists" warning.
     */
    @Query("SELECT COUNT(*) FROM products WHERE name = :name COLLATE NOCASE AND isActive = 1 AND id != :excludeId")
    suspend fun countActiveProductsNamed(name: String, excludeId: Long): Int

    /**
     * Products with current stock, for the product list and the sell screen.
     * [isActive] = true gives normal products, false gives archived ones.
     * [query] = "" means no search. [categoryId] = null means all categories.
     */
    @Query(
        """
        SELECT p.*,
               COALESCE((SELECT SUM(m.quantity) FROM stock_movements m WHERE m.productId = p.id), 0)
                   AS stock
        FROM products p
        WHERE p.isActive = :isActive
          AND p.name LIKE '%' || :query || '%'
          AND (:categoryId IS NULL OR p.categoryId = :categoryId)
        ORDER BY p.name
        """
    )
    fun observeProducts(isActive: Boolean, query: String, categoryId: Long?): Flow<List<ProductWithStock>>
}

package com.example.dukanhisabnext.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.dukanhisabnext.data.entity.StockMovement
import kotlinx.coroutines.flow.Flow

/** One product's current stock (thousandths). Products with no movements have no row (stock 0). */
data class ProductStockRow(val productId: Long, val stock: Long)

// On purpose there is NO update and NO delete here: stock movements are never
// changed (SPEC.md section 5). A mistake is fixed by inserting an ADJUSTMENT.
@Dao
interface StockMovementDao {
    @Insert
    suspend fun insert(movement: StockMovement): Long

    @Insert
    suspend fun insertAll(movements: List<StockMovement>)

    /** Current stock in thousandths = sum of all movements (0 if there are none). */
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM stock_movements WHERE productId = :productId")
    suspend fun getStock(productId: Long): Long

    /** Live version of [getStock]: re-emits whenever a movement is added. */
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM stock_movements WHERE productId = :productId")
    fun observeStock(productId: Long): Flow<Long>

    /**
     * Current stock of every product that has movements, live. The sell screen uses it for the
     * "more than in stock" warning on cart lines (the cart can hold products the search hides).
     */
    @Query("SELECT productId, SUM(quantity) AS stock FROM stock_movements GROUP BY productId")
    fun observeAllStocks(): Flow<List<ProductStockRow>>

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY createdAt DESC, id DESC")
    fun observeForProduct(productId: Long): Flow<List<StockMovement>>
}

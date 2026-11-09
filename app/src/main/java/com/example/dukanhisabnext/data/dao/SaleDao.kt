package com.example.dukanhisabnext.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.data.entity.SaleItem
import kotlinx.coroutines.flow.Flow

// On purpose there is NO update and NO delete here: a completed sale is never changed.
// The "complete sale" transaction (sale + items + stock + ledger together) comes in M4.
@Dao
interface SaleDao {
    @Insert
    suspend fun insertSale(sale: Sale): Long

    @Insert
    suspend fun insertItems(items: List<SaleItem>)

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getSale(id: Long): Sale?

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY id")
    suspend fun getItems(saleId: Long): List<SaleItem>

    /** Sales in a time window (start included, end excluded), newest first. */
    @Query("SELECT * FROM sales WHERE createdAt >= :start AND createdAt < :end ORDER BY createdAt DESC")
    fun observeInRange(start: Long, end: Long): Flow<List<Sale>>
}

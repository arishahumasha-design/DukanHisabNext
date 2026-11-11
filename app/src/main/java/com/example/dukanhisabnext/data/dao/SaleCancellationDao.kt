package com.example.dukanhisabnext.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.dukanhisabnext.data.entity.SaleCancellation
import kotlinx.coroutines.flow.Flow

// On purpose there is NO update and NO delete here: a cancellation is history and is never
// changed. Insert uses the normal "abort" rule: a second cancellation of the same sale breaks the
// unique index and throws, so it can never be saved twice.
@Dao
interface SaleCancellationDao {
    @Insert
    suspend fun insert(cancellation: SaleCancellation): Long

    /** The cancellation of a sale, or null if the sale is not cancelled. */
    @Query("SELECT * FROM sale_cancellations WHERE saleId = :saleId")
    suspend fun getForSale(saleId: Long): SaleCancellation?

    /** Live version of [getForSale]: the receipt shows the banner as soon as the sale is cancelled. */
    @Query("SELECT * FROM sale_cancellations WHERE saleId = :saleId")
    fun observeForSale(saleId: Long): Flow<SaleCancellation?>
}

package com.example.dukanhisabnext.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.dukanhisabnext.data.entity.Customer
import kotlinx.coroutines.flow.Flow

/** A customer plus their current due balance (paisa), computed from the ledger. */
data class CustomerWithBalance(
    @Embedded val customer: Customer,
    val balance: Long
)

@Dao
interface CustomerDao {
    @Insert
    suspend fun insert(customer: Customer): Long

    @Update
    suspend fun update(customer: Customer)

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: Long): Customer?

    /** Customer list with balances. Searches name and mobile. [query] = "" means all. */
    @Query(
        """
        SELECT c.*,
               (SELECT $BALANCE_SUM_SQL FROM ledger_entries l WHERE l.customerId = c.id) AS balance
        FROM customers c
        WHERE c.name LIKE '%' || :query || '%'
           OR c.phone LIKE '%' || :query || '%'
        ORDER BY c.name
        """
    )
    fun observeWithBalance(query: String): Flow<List<CustomerWithBalance>>
}

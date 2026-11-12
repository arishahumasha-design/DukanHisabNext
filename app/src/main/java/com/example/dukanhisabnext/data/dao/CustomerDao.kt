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

    /** Live version of [getById]: the detail screen updates itself after an edit. */
    @Query("SELECT * FROM customers WHERE id = :id")
    fun observeById(id: Long): Flow<Customer?>

    /** How many customers have this mobile number, not counting [excludeId]. For a gentle warning. */
    @Query("SELECT COUNT(*) FROM customers WHERE phone = :phone AND id != :excludeId")
    suspend fun countWithPhone(phone: String, excludeId: Long): Int

    /**
     * Plain customer list for choosing a customer, sorted by name A to Z. Same search as the
     * Khata list: name or mobile. [query] = "" means all.
     */
    @Query(
        """
        SELECT * FROM customers
        WHERE name LIKE '%' || :query || '%'
           OR phone LIKE '%' || :query || '%'
        ORDER BY name
        """
    )
    fun observeSearchedByName(query: String): Flow<List<Customer>>

    /**
     * Customer list with balances. Searches name and mobile. [query] = "" means all.
     * Order: the biggest due first, then by name A to Z. (Customers who owe nothing come
     * after those who owe; customers who are ahead on payments come last.)
     */
    @Query(
        """
        SELECT c.*,
               (SELECT $BALANCE_SUM_SQL FROM ledger_entries l WHERE l.customerId = c.id) AS balance
        FROM customers c
        WHERE c.name LIKE '%' || :query || '%'
           OR c.phone LIKE '%' || :query || '%'
        ORDER BY balance DESC, c.name
        """
    )
    fun observeWithBalance(query: String): Flow<List<CustomerWithBalance>>

    /**
     * Every customer (also those with no ledger entry yet) with the balance and the entry date of
     * the newest ledger entry, in ONE query (no query per customer). The balance uses the shared
     * BALANCE_SUM_SQL; a customer with no entries gets 0 and lastEntryDate = null.
     * No order here: the Khata screen sorts with sortKhataCustomers() (KhataRules.kt).
     */
    @Query(
        """
        SELECT c.*,
               $BALANCE_SUM_SQL AS balance,
               MAX(l.entryDate) AS lastEntryDate
        FROM customers c
        LEFT JOIN ledger_entries l ON l.customerId = c.id
        GROUP BY c.id
        """
    )
    fun observeKhataCustomers(): Flow<List<KhataCustomerRow>>
}

/** One row of the "সব" list: the customer, the balance, and the date of their newest ledger entry. */
data class KhataCustomerRow(
    @Embedded val customer: Customer,
    val balance: Long,
    /** entryDate (UTC millis) of the newest ledger entry; null when the customer has none. */
    val lastEntryDate: Long?
)

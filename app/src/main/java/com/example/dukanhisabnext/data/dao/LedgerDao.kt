package com.example.dukanhisabnext.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.dukanhisabnext.data.entity.LedgerEntry
import kotlinx.coroutines.flow.Flow

/**
 * The balance formula from SPEC.md, written once as SQL and reused in queries:
 * (OPENING_DUE + SALE_DUE + MANUAL_DUE + CORRECTION_PLUS) - (PAYMENT_RECEIVED + CORRECTION_MINUS).
 * Amounts are always positive, so "minus" types are subtracted here.
 */
internal const val BALANCE_SUM_SQL = """
    COALESCE(SUM(CASE
        WHEN type IN ('OPENING_DUE', 'SALE_DUE', 'MANUAL_DUE', 'CORRECTION_PLUS') THEN amount
        ELSE -amount
    END), 0)
"""

// On purpose there is NO update and NO delete here: ledger entries are never
// changed. A mistake is fixed by inserting a correction entry.
@Dao
interface LedgerDao {
    @Insert
    suspend fun insert(entry: LedgerEntry): Long

    /** A customer's history, newest first (by the date the user picked). */
    @Query("SELECT * FROM ledger_entries WHERE customerId = :customerId ORDER BY entryDate DESC, id DESC")
    fun observeForCustomer(customerId: Long): Flow<List<LedgerEntry>>

    /** How much this customer owes (paisa). Negative = shop owes the customer (advance). */
    @Query("SELECT $BALANCE_SUM_SQL FROM ledger_entries WHERE customerId = :customerId")
    suspend fun getBalance(customerId: Long): Long

    /**
     * Total receivable (pabo): the sum of every customer's balance, counting only
     * customers who owe money. A customer with an advance does not reduce it.
     */
    @Query(
        """
        SELECT COALESCE(SUM(balance), 0) FROM (
            SELECT $BALANCE_SUM_SQL AS balance FROM ledger_entries GROUP BY customerId
        ) WHERE balance > 0
        """
    )
    fun observeTotalReceivable(): Flow<Long>

    /** Used to allow only one opening due per customer. */
    @Query("SELECT COUNT(*) FROM ledger_entries WHERE customerId = :customerId AND type = 'OPENING_DUE'")
    suspend fun countOpeningDue(customerId: Long): Int
}

package com.example.dukanhisabnext.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.example.dukanhisabnext.data.entity.LedgerEntry
import kotlinx.coroutines.flow.Flow

/**
 * The balance formula from SPEC.md, written once as SQL and reused in every balance query:
 * (OPENING_DUE + SALE_DUE + MANUAL_DUE + CORRECTION_PLUS + ADVANCE_RETURNED)
 *   - (PAYMENT_RECEIVED + CORRECTION_MINUS).
 * Amounts are always positive, so the "minus" types are subtracted here. BOTH lists are written
 * out: a type that is in neither list counts 0 (it can never be counted the wrong way by
 * accident). The Kotlin twin is signedAmount() in LedgerRules.kt; a phone test checks they agree.
 */
internal const val BALANCE_SUM_SQL = """
    COALESCE(SUM(CASE
        WHEN type IN ('OPENING_DUE', 'SALE_DUE', 'MANUAL_DUE', 'CORRECTION_PLUS', 'ADVANCE_RETURNED') THEN amount
        WHEN type IN ('PAYMENT_RECEIVED', 'CORRECTION_MINUS') THEN -amount
        ELSE 0
    END), 0)
"""

/**
 * One row of a customer's history: the entry, plus (for entries linked to a sale) the sale's
 * receipt number and whether that sale was cancelled later.
 */
data class LedgerHistoryRow(
    @Embedded val entry: LedgerEntry,
    val receiptNo: String?,
    val saleCancelled: Boolean
)

/** One row of a Khata time chip: a ledger entry plus who it belongs to (and its sale, if any). */
data class KhataEntryRow(
    @Embedded val entry: LedgerEntry,
    val customerName: String,
    val customerPhone: String?,
    val receiptNo: String?,
    val saleCancelled: Boolean
) {
    /** The same shape as a customer-history row, so the same labels and rules apply. */
    fun asHistoryRow(): LedgerHistoryRow = LedgerHistoryRow(entry, receiptNo, saleCancelled)
}

// On purpose there is NO update and NO delete here: ledger entries are never
// changed. A mistake is fixed by inserting a correction entry.
@Dao
interface LedgerDao {
    @Insert
    suspend fun insert(entry: LedgerEntry): Long

    /** A customer's history, newest first (by the date the user picked). */
    @Query("SELECT * FROM ledger_entries WHERE customerId = :customerId ORDER BY entryDate DESC, id DESC")
    fun observeForCustomer(customerId: Long): Flow<List<LedgerEntry>>

    /**
     * The history for the customer detail screen, newest first: by the date the user picked, then
     * by the real save time, then by id (so two entries at the same moment keep a fixed order).
     */
    @Query(
        """
        SELECT l.*, s.receiptNo AS receiptNo, (sc.id IS NOT NULL) AS saleCancelled
        FROM ledger_entries l
        LEFT JOIN sales s ON s.id = l.saleId
        LEFT JOIN sale_cancellations sc ON sc.saleId = l.saleId
        WHERE l.customerId = :customerId
        ORDER BY l.entryDate DESC, l.createdAt DESC, l.id DESC
        """
    )
    fun observeHistory(customerId: Long): Flow<List<LedgerHistoryRow>>

    /**
     * Every ledger entry of ALL customers in a time window (start included, end excluded), newest
     * first, with the customer's name and mobile, and (for entries linked to a sale) the receipt
     * number and whether that sale was cancelled. Used by the Khata time chips.
     */
    @Query(
        """
        SELECT l.*, c.name AS customerName, c.phone AS customerPhone,
               s.receiptNo AS receiptNo, (sc.id IS NOT NULL) AS saleCancelled
        FROM ledger_entries l
        JOIN customers c ON c.id = l.customerId
        LEFT JOIN sales s ON s.id = l.saleId
        LEFT JOIN sale_cancellations sc ON sc.saleId = l.saleId
        WHERE l.entryDate >= :start AND l.entryDate < :end
        ORDER BY l.entryDate DESC, l.createdAt DESC, l.id DESC
        """
    )
    fun observeEntriesInRange(start: Long, end: Long): Flow<List<KhataEntryRow>>

    /** How much this customer owes (paisa). Negative = shop owes the customer (advance). */
    @Query("SELECT $BALANCE_SUM_SQL FROM ledger_entries WHERE customerId = :customerId")
    suspend fun getBalance(customerId: Long): Long

    /** Live version of [getBalance]: re-emits whenever a ledger entry is added. */
    @Query("SELECT $BALANCE_SUM_SQL FROM ledger_entries WHERE customerId = :customerId")
    fun observeBalance(customerId: Long): Flow<Long>

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

    /** Live version of [countOpeningDue], so the screen can hide the "পুরনো বাকি" button. */
    @Query("SELECT COUNT(*) FROM ledger_entries WHERE customerId = :customerId AND type = 'OPENING_DUE'")
    fun observeOpeningDueCount(customerId: Long): Flow<Int>
}

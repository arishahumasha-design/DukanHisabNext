package com.example.dukanhisabnext.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.data.entity.SaleItem
import kotlinx.coroutines.flow.Flow

/**
 * THE shared "this sale is not cancelled" condition. Every query that counts or adds up sales
 * (the sales list totals now, every report in M7) must use it, so a cancelled sale can never be
 * counted by mistake. It needs the sales table to be called `s` in the query:
 *     SELECT ... FROM sales s WHERE ... AND $NOT_CANCELLED_SQL
 * Written once here, like the customer balance formula in LedgerDao.
 */
internal const val NOT_CANCELLED_SQL =
    "NOT EXISTS (SELECT 1 FROM sale_cancellations nc WHERE nc.saleId = s.id)"

/**
 * One line of the sales list: a saved sale with its customer's name and mobile (null when the
 * sale has no customer). Only what the list shows, so thousands of rows stay light.
 */
data class SaleListRow(
    val id: Long,
    val receiptNo: String,
    val createdAt: Long,       // UTC millis; the list shows it in Asia/Dhaka time
    val total: Long,           // paisa
    val dueAmount: Long,       // paisa
    val customerName: String?,
    val customerPhone: String?,
    /** When the sale was cancelled (UTC millis), or null if it is not cancelled. */
    val cancelledAt: Long? = null
) {
    val isCancelled: Boolean get() = cancelledAt != null
}

/** How many sales and their total (paisa): used by reports, always without cancelled sales. */
data class SalesTotalsRow(val count: Int, val total: Long)

/**
 * The search of the sales list, ready for SQL (built by salesSearchParams() in SalesListLogic.kt).
 * All four are "" when nothing is searched. [query] and [compactQuery] have %, _ and \ escaped, so
 * they are matched literally (like Kotlin's contains()).
 */
data class SalesSearchParams(
    val query: String,
    val compactQuery: String,     // the query without dashes and spaces (receipt numbers)
    val digits: String,           // only the digits of the query (mobile numbers)
    val digitsWithout88: String   // the digits without a leading "88" from +88, or ""
)

/** The line above the sales list, for the WHOLE window and search (not only the rows shown). */
data class SalesListSummaryRow(val count: Int, val total: Long, val cancelledExcluded: Int)

/**
 * The sales list search as SQL: the twin of matchesSalesQuery() in SalesListLogic.kt (a phone test
 * checks they find the same sales). Needs `s` = sales and `c` = customers (LEFT JOIN) and the four
 * parameters of [SalesSearchParams].
 */
internal const val SALES_SEARCH_SQL = """
    (:query = ''
     OR (:compactQuery != '' AND REPLACE(REPLACE(s.receiptNo, '-', ''), ' ', '') LIKE '%' || :compactQuery || '%' ESCAPE '\')
     OR c.name LIKE '%' || :query || '%' ESCAPE '\'
     OR (:digits != '' AND c.phone LIKE '%' || :digits || '%')
     OR (:digitsWithout88 != '' AND c.phone LIKE '%' || :digitsWithout88 || '%'))
"""

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

    /**
     * Every receipt number of one day, for example all that start with "261109-".
     * The highest counter is then worked out as a NUMBER in Kotlin (ReceiptNumber.next),
     * never by sorting this text.
     */
    @Query("SELECT receiptNo FROM sales WHERE receiptNo LIKE :prefix || '-%'")
    suspend fun receiptNumbersOfDay(prefix: String): List<String>

    @Query("SELECT COUNT(*) FROM sales WHERE receiptNo = :receiptNo")
    suspend fun countWithReceiptNo(receiptNo: String): Int

    /**
     * The sale saved at exactly [createdAt] with this [total], or null. Used only after a restart,
     * to tell whether a cart's last save attempt (SaleDraftJournal) really saved a sale.
     */
    @Query("SELECT * FROM sales WHERE createdAt = :createdAt AND total = :total ORDER BY id DESC LIMIT 1")
    suspend fun findSaleAt(createdAt: Long, total: Long): Sale?

    /**
     * The sales list: saved sales in a time window (start included, end excluded), newest first,
     * each with its customer's name and mobile (LEFT JOIN: a sale without a customer still shows).
     * Cancelled sales are INCLUDED (the history stays honest) and carry their cancel time, so the
     * list can tag them and leave them out of its totals.
     * A Flow, so the list updates by itself when a sale is saved or cancelled. Read-only.
     */
    @Query(
        """
        SELECT s.id AS id, s.receiptNo AS receiptNo, s.createdAt AS createdAt,
               s.total AS total, s.dueAmount AS dueAmount,
               c.name AS customerName, c.phone AS customerPhone,
               sc.createdAt AS cancelledAt
        FROM sales s
        LEFT JOIN customers c ON c.id = s.customerId
        LEFT JOIN sale_cancellations sc ON sc.saleId = s.id
        WHERE s.createdAt >= :start AND s.createdAt < :end
        ORDER BY s.createdAt DESC, s.id DESC
        """
    )
    fun observeList(start: Long, end: Long): Flow<List<SaleListRow>>

    /**
     * One page of the sales list screen: like [observeList], with the search done here and only
     * the newest [limit] rows ("আরও দেখান" asks for more). The count and total line must NOT be
     * worked out from these rows; it comes from [observeListSummary].
     */
    @Query(
        """
        SELECT s.id AS id, s.receiptNo AS receiptNo, s.createdAt AS createdAt,
               s.total AS total, s.dueAmount AS dueAmount,
               c.name AS customerName, c.phone AS customerPhone,
               sc.createdAt AS cancelledAt
        FROM sales s
        LEFT JOIN customers c ON c.id = s.customerId
        LEFT JOIN sale_cancellations sc ON sc.saleId = s.id
        WHERE s.createdAt >= :start AND s.createdAt < :end
          AND $SALES_SEARCH_SQL
        ORDER BY s.createdAt DESC, s.id DESC
        LIMIT :limit
        """
    )
    fun observeListPage(
        start: Long,
        end: Long,
        query: String,
        compactQuery: String,
        digits: String,
        digitsWithout88: String,
        limit: Int
    ): Flow<List<SaleListRow>>

    /**
     * The count and total line of the sales list for the WHOLE window and search, however many
     * rows are shown: cancelled sales are left out of count and total by the shared
     * NOT_CANCELLED_SQL and counted in [SalesListSummaryRow.cancelledExcluded].
     */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN $NOT_CANCELLED_SQL THEN 1 ELSE 0 END), 0) AS count,
               COALESCE(SUM(CASE WHEN $NOT_CANCELLED_SQL THEN s.total ELSE 0 END), 0) AS total,
               COALESCE(SUM(CASE WHEN $NOT_CANCELLED_SQL THEN 0 ELSE 1 END), 0) AS cancelledExcluded
        FROM sales s
        LEFT JOIN customers c ON c.id = s.customerId
        WHERE s.createdAt >= :start AND s.createdAt < :end
          AND $SALES_SEARCH_SQL
        """
    )
    fun observeListSummary(
        start: Long,
        end: Long,
        query: String,
        compactQuery: String,
        digits: String,
        digitsWithout88: String
    ): Flow<SalesListSummaryRow>

    /**
     * Count and total of the sales in a time window, WITHOUT cancelled sales (the shared
     * condition). Reports (M7) build on this.
     */
    @Query(
        """
        SELECT COUNT(*) AS count, COALESCE(SUM(s.total), 0) AS total
        FROM sales s
        WHERE s.createdAt >= :start AND s.createdAt < :end
          AND $NOT_CANCELLED_SQL
        """
    )
    fun observeActiveTotals(start: Long, end: Long): Flow<SalesTotalsRow>

    /** How many sales exist at all (to tell "no sales yet" from "nothing matches"). */
    @Query("SELECT COUNT(*) FROM sales")
    fun observeCount(): Flow<Int>

    /** Sales in a time window (start included, end excluded), newest first. */
    @Query("SELECT * FROM sales WHERE createdAt >= :start AND createdAt < :end ORDER BY createdAt DESC")
    fun observeInRange(start: Long, end: Long): Flow<List<Sale>>
}

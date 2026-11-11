package com.example.dukanhisabnext.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.data.entity.SaleItem
import kotlinx.coroutines.flow.Flow

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
    val customerPhone: String?
)

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
     * The sales list: saved sales in a time window (start included, end excluded), newest first,
     * each with its customer's name and mobile (LEFT JOIN: a sale without a customer still shows).
     * A Flow, so the list updates by itself when a sale is saved. Read-only.
     */
    @Query(
        """
        SELECT s.id AS id, s.receiptNo AS receiptNo, s.createdAt AS createdAt,
               s.total AS total, s.dueAmount AS dueAmount,
               c.name AS customerName, c.phone AS customerPhone
        FROM sales s
        LEFT JOIN customers c ON c.id = s.customerId
        WHERE s.createdAt >= :start AND s.createdAt < :end
        ORDER BY s.createdAt DESC, s.id DESC
        """
    )
    fun observeList(start: Long, end: Long): Flow<List<SaleListRow>>

    /** How many sales exist at all (to tell "no sales yet" from "nothing matches"). */
    @Query("SELECT COUNT(*) FROM sales")
    fun observeCount(): Flow<Int>

    /** Sales in a time window (start included, end excluded), newest first. */
    @Query("SELECT * FROM sales WHERE createdAt >= :start AND createdAt < :end ORDER BY createdAt DESC")
    fun observeInRange(start: Long, end: Long): Flow<List<Sale>>
}

package com.example.dukanhisabnext.data.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The cost of one sale line in paisa, rounded half-up ONCE per line, written as SQL. It must give
 * exactly SaleCalculator.lineTotal(quantity, costPriceSnapshot): quantity is thousandths, so
 * cost = (quantity x costPrice + 500) / 1000 with integer division (all numbers are >= 0).
 * A phone test (ReportQueriesTest) checks it against the Kotlin function on tricky values.
 * Needs the sale_items table to be called `si`.
 */
internal const val LINE_COST_SQL = "(si.quantity * si.costPriceSnapshot + 500) / 1000"

/**
 * THE low-stock rule as SQL, the same as stockStatus() in ProductLabels.kt (the product tags):
 * stock below 0, or an alert level is set and the stock is at or below it.
 * Needs the columns `stock` and `lowStockLevel`.
 */
internal const val LOW_STOCK_SQL = "(stock < 0 OR (lowStockLevel IS NOT NULL AND stock <= lowStockLevel))"

/**
 * The sales of a window in numbers. Every amount leaves cancelled sales out (NOT_CANCELLED_SQL);
 * [cancelledCount] is how many cancelled sales were left out (for the "(বাতিল N টি বাদ)" note).
 */
data class SalesReportRow(
    val count: Int,
    val subtotal: Long,
    val discount: Long,
    val deliveryCharge: Long,
    val total: Long,
    val paid: Long,
    val due: Long,
    val cancelledCount: Int
)

/**
 * The cost of goods sold in a window (cancelled sales left out):
 *  - [cost]: the sum of each line's cost (LINE_COST_SQL) for the lines WITH a cost price;
 *  - [lineCount]: all sold lines; [missingCostLines]: lines with no cost price;
 *  - [missingCostSales]: the sale value (lineTotal) of those lines (how much of the sales had no cost).
 */
data class CostOfGoodsRow(
    val cost: Long,
    val lineCount: Int,
    val missingCostLines: Int,
    val missingCostSales: Long
)

/** Read-only queries for the reports and the Home dashboard. Nothing here writes. */
@Dao
interface ReportDao {

    /**
     * The sales report of a window (start included, end excluded), by the sale's createdAt.
     * The inner query marks each sale active or cancelled once (NOT_CANCELLED_SQL); the outer
     * query adds up only the active ones and counts the cancelled ones.
     */
    @Query(
        """
        SELECT COALESCE(SUM(active), 0) AS count,
               COALESCE(SUM(CASE WHEN active THEN subtotal ELSE 0 END), 0) AS subtotal,
               COALESCE(SUM(CASE WHEN active THEN discount ELSE 0 END), 0) AS discount,
               COALESCE(SUM(CASE WHEN active THEN deliveryCharge ELSE 0 END), 0) AS deliveryCharge,
               COALESCE(SUM(CASE WHEN active THEN total ELSE 0 END), 0) AS total,
               COALESCE(SUM(CASE WHEN active THEN paidAmount ELSE 0 END), 0) AS paid,
               COALESCE(SUM(CASE WHEN active THEN dueAmount ELSE 0 END), 0) AS due,
               COALESCE(SUM(CASE WHEN active THEN 0 ELSE 1 END), 0) AS cancelledCount
        FROM (
            SELECT s.subtotal, s.discount, s.deliveryCharge, s.total, s.paidAmount, s.dueAmount,
                   ($NOT_CANCELLED_SQL) AS active
            FROM sales s
            WHERE s.createdAt >= :start AND s.createdAt < :end
        )
        """
    )
    fun observeSalesReport(start: Long, end: Long): Flow<SalesReportRow>

    /** The cost of goods sold of a window, with how many lines (and how much sale value) had no cost price. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN si.costPriceSnapshot IS NOT NULL THEN $LINE_COST_SQL ELSE 0 END), 0) AS cost,
               COUNT(si.id) AS lineCount,
               COALESCE(SUM(CASE WHEN si.costPriceSnapshot IS NULL THEN 1 ELSE 0 END), 0) AS missingCostLines,
               COALESCE(SUM(CASE WHEN si.costPriceSnapshot IS NULL THEN si.lineTotal ELSE 0 END), 0) AS missingCostSales
        FROM sale_items si
        JOIN sales s ON s.id = si.saleId
        WHERE s.createdAt >= :start AND s.createdAt < :end AND $NOT_CANCELLED_SQL
        """
    )
    fun observeCostOfGoods(start: Long, end: Long): Flow<CostOfGoodsRow>

    /**
     * How many active (not archived) products need attention: negative stock, or stock at or below
     * their alert level. Stock is the sum of the product's movements (0 when it has none).
     */
    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT p.lowStockLevel AS lowStockLevel,
                   COALESCE((SELECT SUM(m.quantity) FROM stock_movements m WHERE m.productId = p.id), 0) AS stock
            FROM products p
            WHERE p.isActive = 1
        )
        WHERE $LOW_STOCK_SQL
        """
    )
    fun observeLowStockCount(): Flow<Int>
}

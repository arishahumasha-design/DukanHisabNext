package com.example.dukanhisabnext.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.dukanhisabnext.data.entity.Expense
import com.example.dukanhisabnext.data.entity.ExpenseCategory
import kotlinx.coroutines.flow.Flow

/**
 * THE condition for "this expense counts": it is not deleted. Every query that lists or adds up
 * expenses uses it (with the alias `e` for expenses), so no list or report can forget it.
 */
internal const val ACTIVE_EXPENSE_SQL = "e.isDeleted = 0"

/**
 * THE condition for "this is a cost of running the shop": not deleted, and its category is not
 * personal (পরিবার and other personal categories are left out). Needs the aliases `e` (expenses)
 * and `c` (expense_categories, joined on e.expenseCategoryId). The profit report (M7) uses this.
 */
internal const val SHOP_EXPENSE_SQL = "$ACTIVE_EXPENSE_SQL AND c.isPersonal = 0"

/** One expense with its category's name and whether that category is personal. */
data class ExpenseRow(
    @Embedded val expense: Expense,
    val categoryName: String,
    val categoryIsPersonal: Boolean
)

// On purpose there is NO delete for categories (old expenses must never lose their category) and
// NO delete for expenses (a "delete" only sets isDeleted, see softDelete).
@Dao
interface ExpenseDao {
    // ---- Expense categories ----

    @Insert
    suspend fun insertCategory(category: ExpenseCategory): Long

    @Update
    suspend fun updateCategory(category: ExpenseCategory)

    /** Every category, also the hidden ones, by name. */
    @Query("SELECT * FROM expense_categories ORDER BY name")
    fun observeCategories(): Flow<List<ExpenseCategory>>

    @Query("SELECT * FROM expense_categories ORDER BY name")
    suspend fun getCategories(): List<ExpenseCategory>

    @Query("SELECT * FROM expense_categories WHERE id = :id")
    suspend fun getCategory(id: Long): ExpenseCategory?

    @Query("SELECT * FROM expense_categories WHERE systemKey = :systemKey")
    suspend fun getCategoryBySystemKey(systemKey: String): ExpenseCategory?

    // ---- Expenses ----

    @Insert
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpense(id: Long): Expense?

    /** Soft delete: the row stays, but it is hidden from lists and totals. */
    @Query("UPDATE expenses SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

    /** The undo of a soft delete ("ফিরিয়ে আনুন"). */
    @Query("UPDATE expenses SET isDeleted = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long)

    /** Expenses in a window by the date the user picked, newest first. Deleted ones are hidden. */
    @Query(
        """
        SELECT e.* FROM expenses e
        WHERE $ACTIVE_EXPENSE_SQL AND e.expenseDate >= :start AND e.expenseDate < :end
        ORDER BY e.expenseDate DESC, e.id DESC
        """
    )
    fun observeInRange(start: Long, end: Long): Flow<List<Expense>>

    /** The total of every (not deleted) expense in a window, personal ones included. */
    @Query(
        """
        SELECT COALESCE(SUM(e.amount), 0) FROM expenses e
        WHERE $ACTIVE_EXPENSE_SQL AND e.expenseDate >= :start AND e.expenseDate < :end
        """
    )
    fun observeTotalInRange(start: Long, end: Long): Flow<Long>

    /**
     * The expense list of a window, with each expense's category name and personal flag, newest
     * first (by the date the user picked, then the real save time, then id). Deleted ones are
     * hidden. One query per window; the screen filters and adds up in Kotlin (ExpenseRules.kt).
     */
    @Query(
        """
        SELECT e.*, c.name AS categoryName, c.isPersonal AS categoryIsPersonal
        FROM expenses e
        JOIN expense_categories c ON c.id = e.expenseCategoryId
        WHERE $ACTIVE_EXPENSE_SQL AND e.expenseDate >= :start AND e.expenseDate < :end
        ORDER BY e.expenseDate DESC, e.createdAt DESC, e.id DESC
        """
    )
    fun observeRowsInRange(start: Long, end: Long): Flow<List<ExpenseRow>>

    /** The total of the SHOP expenses of a window (personal categories left out). For M7. */
    @Query(
        """
        SELECT COALESCE(SUM(e.amount), 0)
        FROM expenses e
        JOIN expense_categories c ON c.id = e.expenseCategoryId
        WHERE $SHOP_EXPENSE_SQL AND e.expenseDate >= :start AND e.expenseDate < :end
        """
    )
    fun observeShopTotalInRange(start: Long, end: Long): Flow<Long>
}

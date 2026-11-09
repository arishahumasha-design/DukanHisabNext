package com.example.dukanhisabnext.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.dukanhisabnext.data.entity.Expense
import com.example.dukanhisabnext.data.entity.ExpenseCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    // ---- Expense categories ----

    @Insert
    suspend fun insertCategory(category: ExpenseCategory): Long

    @Query("SELECT * FROM expense_categories ORDER BY name")
    fun observeCategories(): Flow<List<ExpenseCategory>>

    // ---- Expenses ----

    @Insert
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    /** Soft delete: the row stays, but it is hidden from lists and totals. */
    @Query("UPDATE expenses SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

    /** Expenses in a window by the date the user picked, newest first. Deleted ones are hidden. */
    @Query(
        """
        SELECT * FROM expenses
        WHERE isDeleted = 0 AND expenseDate >= :start AND expenseDate < :end
        ORDER BY expenseDate DESC, id DESC
        """
    )
    fun observeInRange(start: Long, end: Long): Flow<List<Expense>>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM expenses
        WHERE isDeleted = 0 AND expenseDate >= :start AND expenseDate < :end
        """
    )
    fun observeTotalInRange(start: Long, end: Long): Flow<Long>
}

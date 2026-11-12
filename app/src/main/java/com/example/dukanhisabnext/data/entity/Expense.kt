package com.example.dukanhisabnext.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An expense category (বেতন, ভাড়া, ...). Categories are never deleted, so old expenses never
 * lose their category: a category that is not needed any more is hidden ([isActive] = false).
 *
 * Added in database version 3 (see Migrations.kt):
 *  - [isPersonal]: true for spending that is NOT a cost of running the shop (for example পরিবার).
 *    The profit report (M7) leaves these out and shows them on their own line.
 *  - [isActive]: false = hidden from the picker for new expenses (old expenses keep it).
 *  - [systemKey]: set only on the default categories ("SALARY", "FAMILY", ...), so they are
 *    created exactly once, even after a rename. Unique; null for categories the user adds.
 */
@Entity(
    tableName = "expense_categories",
    indices = [Index(value = ["systemKey"], unique = true)]
)
data class ExpenseCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "0") val isPersonal: Boolean = false,
    @ColumnInfo(defaultValue = "1") val isActive: Boolean = true,
    val systemKey: String? = null
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseCategory::class,
            parentColumns = ["id"],
            childColumns = ["expenseCategoryId"]
        )
    ],
    // expenseDate is indexed (version 3) because every list and report cuts by it.
    indices = [Index("expenseCategoryId"), Index("expenseDate")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseCategoryId: Long,
    val amount: Long,               // paisa
    val reason: String? = null,
    val expenseDate: Long,          // the date the user picked. Used for lists, filters, reports.
    val isDeleted: Boolean = false, // "delete" only sets this to true (soft delete)
    val createdAt: Long,            // the real moment the row was saved
    val updatedAt: Long
)

package com.example.dukanhisabnext.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "expense_categories")
data class ExpenseCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
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
    indices = [Index("expenseCategoryId")]
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

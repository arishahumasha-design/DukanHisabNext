package com.example.dukanhisabnext.data

import androidx.room.withTransaction
import com.example.dukanhisabnext.data.dao.ExpenseRow
import com.example.dukanhisabnext.data.entity.Expense
import com.example.dukanhisabnext.data.entity.ExpenseCategory
import com.example.dukanhisabnext.expense.CategoryError
import com.example.dukanhisabnext.expense.DefaultCategory
import com.example.dukanhisabnext.expense.ExpenseError
import com.example.dukanhisabnext.expense.ExpenseInput
import com.example.dukanhisabnext.expense.Seed
import com.example.dukanhisabnext.expense.applyEdit
import com.example.dukanhisabnext.expense.buildExpense
import com.example.dukanhisabnext.expense.canHide
import com.example.dukanhisabnext.expense.checkCategoryName
import com.example.dukanhisabnext.expense.checkExpenseInput
import com.example.dukanhisabnext.expense.cleanCategoryName
import com.example.dukanhisabnext.expense.seedAction
import com.example.dukanhisabnext.util.DhakaTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ExpenseSaveResult {
    data class Saved(val expense: Expense) : ExpenseSaveResult
    /** Nothing was saved. [errors] says why. */
    data class Invalid(val errors: Set<ExpenseError>) : ExpenseSaveResult
    /** An edit of an expense that does not exist, or was deleted. Nothing was saved. */
    data object NotFound : ExpenseSaveResult
}

enum class ExpenseDeleteResult { DELETED, ALREADY_DELETED, NOT_FOUND }

enum class ExpenseRestoreResult { RESTORED, NOT_DELETED, NOT_FOUND }

sealed interface CategorySaveResult {
    data class Saved(val category: ExpenseCategory) : CategorySaveResult
    data class Invalid(val errors: Set<CategoryError>) : CategorySaveResult
    data object NotFound : CategorySaveResult
}

/**
 * The one place that writes expenses and expense categories.
 *
 * Every write: one lock (one write at a time), one transaction (all or nothing), and the rules of
 * ExpenseRules.kt checked inside the transaction. Adding or editing an expense, and adding a
 * category, carry a form id: saving the same form again (double tap) returns the first result and
 * saves nothing new. Nothing is ever really deleted: an expense is soft-deleted (and can be put back
 * with [restoreExpense]); a category is only hidden.
 *
 * [clock] gives "now" in UTC millis; [afterWrite] is only for crash tests.
 */
class ExpenseRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val afterWrite: () -> Unit = {}
) {
    private val dao = db.expenseDao()
    private val lock = Mutex()

    /** Forms already saved, by form id (expense add/edit and category add). Small and in memory. */
    private val savedForms = object : LinkedHashMap<String, Any>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>?) = size > 100
    }

    // =====================================================================================
    // Reading
    // =====================================================================================

    fun observeCategories(): Flow<List<ExpenseCategory>> = dao.observeCategories()

    fun observeRowsInRange(range: DhakaTime.TimeRange): Flow<List<ExpenseRow>> =
        dao.observeRowsInRange(range.startMillis, range.endMillisExclusive)

    /** Shop costs only (personal categories left out). For the profit report. */
    fun observeShopTotalInRange(range: DhakaTime.TimeRange): Flow<Long> =
        dao.observeShopTotalInRange(range.startMillis, range.endMillisExclusive)

    suspend fun getExpense(id: Long): Expense? = dao.getExpense(id)

    // =====================================================================================
    // The default categories
    // =====================================================================================

    /**
     * Makes sure every default category exists, exactly once (by its systemKey). Safe to call any
     * number of times, on a new install or after an upgrade: a renamed or hidden default is left
     * alone, and a category the user already made with the same name is adopted (it gets the
     * systemKey, and পরিবার is marked personal) instead of being made twice.
     * [nameOf] gives each default's Bengali name (from strings.xml).
     */
    suspend fun ensureDefaultCategories(nameOf: (DefaultCategory) -> String) = lock.withLock {
        db.withTransaction {
            for (default in DefaultCategory.entries) {
                val name = cleanCategoryName(nameOf(default))
                when (val action = seedAction(default, name, dao.getCategories())) {
                    Seed.Exists -> Unit
                    is Seed.Adopt -> dao.updateCategory(
                        action.category.copy(
                            systemKey = default.name,
                            isPersonal = action.category.isPersonal || default.isPersonal
                        )
                    )
                    Seed.Create -> dao.insertCategory(
                        ExpenseCategory(name = name, isPersonal = default.isPersonal, isActive = true, systemKey = default.name)
                    )
                }
            }
            afterWrite()
        }
    }

    // =====================================================================================
    // Expenses
    // =====================================================================================

    /** Adds an expense. [formId] is made when the form opens; the same form twice saves once. */
    suspend fun addExpense(formId: String, input: ExpenseInput): ExpenseSaveResult = lock.withLock {
        (savedForms[formId] as? ExpenseSaveResult.Saved)?.let { return@withLock it }
        val result = db.withTransaction {
            val now = clock()
            val category = input.categoryId?.let { dao.getCategory(it) }
            val errors = checkExpenseInput(input, DhakaTime.localDate(now), category)
            if (errors.isNotEmpty()) return@withTransaction ExpenseSaveResult.Invalid(errors)
            val expense = buildExpense(input, now)
            val id = dao.insert(expense)
            afterWrite()
            ExpenseSaveResult.Saved(expense.copy(id = id))
        }
        if (result is ExpenseSaveResult.Saved) savedForms[formId] = result
        result
    }

    /**
     * Edits an expense: category, amount, date and reason. createdAt stays; updatedAt becomes now.
     * A deleted expense cannot be edited. A hidden category may stay on the expense, but an expense
     * cannot be moved into a hidden one.
     */
    suspend fun editExpense(formId: String, expenseId: Long, input: ExpenseInput): ExpenseSaveResult = lock.withLock {
        (savedForms[formId] as? ExpenseSaveResult.Saved)?.let { return@withLock it }
        val result = db.withTransaction {
            val old = dao.getExpense(expenseId)
            if (old == null || old.isDeleted) return@withTransaction ExpenseSaveResult.NotFound
            val now = clock()
            val category = input.categoryId?.let { dao.getCategory(it) }
            val errors = checkExpenseInput(input, DhakaTime.localDate(now), category, currentCategoryId = old.expenseCategoryId)
            if (errors.isNotEmpty()) return@withTransaction ExpenseSaveResult.Invalid(errors)
            val edited = applyEdit(old, input, now)
            dao.update(edited)
            afterWrite()
            ExpenseSaveResult.Saved(edited)
        }
        if (result is ExpenseSaveResult.Saved) savedForms[formId] = result
        result
    }

    /** "Delete": marks the expense as deleted (hidden from lists and totals). Twice changes nothing. */
    suspend fun deleteExpense(expenseId: Long): ExpenseDeleteResult = lock.withLock {
        db.withTransaction {
            val old = dao.getExpense(expenseId) ?: return@withTransaction ExpenseDeleteResult.NOT_FOUND
            if (old.isDeleted) return@withTransaction ExpenseDeleteResult.ALREADY_DELETED
            dao.softDelete(expenseId, clock())
            afterWrite()
            ExpenseDeleteResult.DELETED
        }
    }

    /** The undo of a delete ("ফিরিয়ে আনুন"). The screen offers it for a few seconds after a delete. */
    suspend fun restoreExpense(expenseId: Long): ExpenseRestoreResult = lock.withLock {
        db.withTransaction {
            val old = dao.getExpense(expenseId) ?: return@withTransaction ExpenseRestoreResult.NOT_FOUND
            if (!old.isDeleted) return@withTransaction ExpenseRestoreResult.NOT_DELETED
            dao.restore(expenseId, clock())
            afterWrite()
            ExpenseRestoreResult.RESTORED
        }
    }

    // =====================================================================================
    // Categories
    // =====================================================================================

    /** Adds a category (shown). [isPersonal] = not a cost of running the shop. Names are unique. */
    suspend fun addCategory(formId: String, name: String, isPersonal: Boolean): CategorySaveResult = lock.withLock {
        (savedForms[formId] as? CategorySaveResult.Saved)?.let { return@withLock it }
        val result = db.withTransaction {
            val errors = checkCategoryName(name, dao.getCategories(), selfId = null)
            if (errors.isNotEmpty()) return@withTransaction CategorySaveResult.Invalid(errors)
            val category = ExpenseCategory(name = cleanCategoryName(name), isPersonal = isPersonal, isActive = true)
            val id = dao.insertCategory(category)
            afterWrite()
            CategorySaveResult.Saved(category.copy(id = id))
        }
        if (result is CategorySaveResult.Saved) savedForms[formId] = result
        result
    }

    /**
     * Renames a category and/or changes its personal flag. Old expenses then show the new name
     * (it is meant for typos). The systemKey of a default category stays.
     */
    suspend fun updateCategory(id: Long, name: String, isPersonal: Boolean): CategorySaveResult = lock.withLock {
        db.withTransaction {
            val old = dao.getCategory(id) ?: return@withTransaction CategorySaveResult.NotFound
            val errors = checkCategoryName(name, dao.getCategories(), selfId = id)
            if (errors.isNotEmpty()) return@withTransaction CategorySaveResult.Invalid(errors)
            val updated = old.copy(name = cleanCategoryName(name), isPersonal = isPersonal)
            dao.updateCategory(updated)
            afterWrite()
            CategorySaveResult.Saved(updated)
        }
    }

    /**
     * Hides a category ([visible] = false) or shows it again. A hidden category is not offered for
     * new expenses; old expenses keep it. The last shown category cannot be hidden.
     */
    suspend fun setCategoryVisible(id: Long, visible: Boolean): CategorySaveResult = lock.withLock {
        db.withTransaction {
            val old = dao.getCategory(id) ?: return@withTransaction CategorySaveResult.NotFound
            if (!visible && !canHide(old, dao.getCategories())) {
                return@withTransaction CategorySaveResult.Invalid(setOf(CategoryError.LAST_VISIBLE))
            }
            val updated = old.copy(isActive = visible)
            dao.updateCategory(updated)
            afterWrite()
            CategorySaveResult.Saved(updated)
        }
    }
}

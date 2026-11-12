package com.example.dukanhisabnext.expense

import com.example.dukanhisabnext.data.dao.ExpenseRow
import com.example.dukanhisabnext.data.entity.Expense
import com.example.dukanhisabnext.data.entity.ExpenseCategory
import com.example.dukanhisabnext.ledger.MAX_LEDGER_AMOUNT
import com.example.dukanhisabnext.ledger.entryDateFor
import com.example.dukanhisabnext.util.DhakaTime
import com.example.dukanhisabnext.util.toLatinDigits
import java.time.LocalDate

// The rules of expenses (খরচ) and their categories, as plain Kotlin (no Android) so they can be
// unit-tested on the PC. ExpenseRepository runs them inside one database transaction.
// Money is Long paisa, always positive. Dates are stored as UTC millis and picked as Dhaka days.

/** ৳1 crore per expense, the same limit as a ledger entry (catches typing mistakes). */
const val MAX_EXPENSE_AMOUNT: Long = MAX_LEDGER_AMOUNT

/** The reason may be at most this many characters (an emoji counts as one). */
const val MAX_EXPENSE_REASON_LENGTH = 200

/** A category name may be at most this many characters. */
const val MAX_CATEGORY_NAME_LENGTH = 40

private fun lengthOf(text: String): Int = text.codePointCount(0, text.length)

// =========================================================================================
// The default categories
// =========================================================================================

/**
 * The categories every shop starts with. [name] of the enum is the systemKey stored in the
 * database, so each one is created exactly once, even after the user renames it. The Bengali
 * names come from strings.xml (given by the caller, in this order).
 * পরিবার (FAMILY) is personal: not a cost of running the shop.
 */
enum class DefaultCategory(val isPersonal: Boolean = false) {
    SALARY,          // বেতন
    RENT,            // ভাড়া
    BILL,            // বিল
    TEA_SNACKS,      // চা-নাস্তা
    TRANSPORT,       // যাতায়াত
    FAMILY(true),    // পরিবার
    OTHER            // অন্যান্য
}

/**
 * The Bengali names of the defaults from the strings.xml array "expense_default_categories"
 * (in the order of [DefaultCategory]). The array must have exactly one name per default.
 */
fun defaultNamesFrom(names: List<String>): (DefaultCategory) -> String {
    require(names.size == DefaultCategory.entries.size) { "expense_default_categories needs ${DefaultCategory.entries.size} names" }
    return { names[it.ordinal] }
}

/** A category name made comparable: trimmed, inner spaces squeezed to one, upper/lower case ignored. */
fun normalizeCategoryName(name: String): String = cleanCategoryName(name).lowercase()

/** The name as it is saved: trimmed, and several spaces in a row become one. */
fun cleanCategoryName(name: String): String = name.trim().replace(Regex("\\s+"), " ")

/**
 * What ensureDefaultCategories must do for one default category:
 *  - [Seed.Exists]: a category with this systemKey exists: nothing (even if renamed or hidden);
 *  - [Seed.Adopt]: a category with the same name exists but has no systemKey (made by the user,
 *    or before version 3): give it the systemKey instead of making a second one;
 *  - [Seed.Create]: make it.
 */
sealed interface Seed {
    data object Exists : Seed
    data class Adopt(val category: ExpenseCategory) : Seed
    data object Create : Seed
}

fun seedAction(default: DefaultCategory, name: String, all: List<ExpenseCategory>): Seed {
    if (all.any { it.systemKey == default.name }) return Seed.Exists
    val sameName = all.firstOrNull { it.systemKey == null && normalizeCategoryName(it.name) == normalizeCategoryName(name) }
    return if (sameName != null) Seed.Adopt(sameName) else Seed.Create
}

// =========================================================================================
// Categories: add, rename, hide
// =========================================================================================

enum class CategoryError {
    NAME_MISSING,   // empty after trimming
    NAME_TOO_LONG,  // more than 40 characters
    NAME_DUPLICATE, // another category (shown or hidden) already has this name
    LAST_VISIBLE    // the last shown category cannot be hidden
}

/**
 * Checks a category name. [all] are every category (shown and hidden); [selfId] is the category
 * being renamed (it may keep its own name), or null for a new one.
 */
fun checkCategoryName(name: String, all: List<ExpenseCategory>, selfId: Long?): Set<CategoryError> = buildSet {
    val clean = cleanCategoryName(name)
    if (clean.isEmpty()) {
        add(CategoryError.NAME_MISSING)
        return@buildSet
    }
    if (lengthOf(clean) > MAX_CATEGORY_NAME_LENGTH) add(CategoryError.NAME_TOO_LONG)
    val key = normalizeCategoryName(clean)
    if (all.any { it.id != selfId && normalizeCategoryName(it.name) == key }) add(CategoryError.NAME_DUPLICATE)
}

/** A category can be hidden unless it is the last one still shown. Showing again is always fine. */
fun canHide(category: ExpenseCategory, all: List<ExpenseCategory>): Boolean =
    !category.isActive || all.count { it.isActive && it.id != category.id } > 0

/** The categories offered in the form: the shown ones, plus [currentId] (an edited expense's own, even if hidden). */
fun pickableCategories(all: List<ExpenseCategory>, currentId: Long?): List<ExpenseCategory> =
    all.filter { it.isActive || it.id == currentId }

// =========================================================================================
// An expense: check, build, edit
// =========================================================================================

/** What the expense form sends. [amount] is paisa (null = nothing typed); [date] is the Dhaka day picked. */
data class ExpenseInput(
    val categoryId: Long?,
    val amount: Long?,
    val date: LocalDate,
    val reason: String? = null
)

enum class ExpenseError {
    CATEGORY_MISSING,  // no category chosen, or it does not exist
    CATEGORY_HIDDEN,   // a hidden category for a new expense (or changed to one in an edit)
    AMOUNT_MISSING,    // empty or 0
    AMOUNT_TOO_LARGE,  // above ৳1 crore
    DATE_IN_FUTURE,    // after today (Dhaka)
    REASON_TOO_LONG    // more than 200 characters
}

/** The reason as it is saved: trimmed; empty means no reason. */
fun cleanReason(reason: String?): String? = reason?.trim()?.ifEmpty { null }

/**
 * Every problem with [input] (empty = it can be saved). [category] is the chosen category as the
 * database has it (null when missing). [currentCategoryId] is the category the expense already
 * has (when editing): a hidden category may stay, but no expense may be moved INTO a hidden one.
 */
fun checkExpenseInput(
    input: ExpenseInput,
    today: LocalDate,
    category: ExpenseCategory?,
    currentCategoryId: Long? = null
): Set<ExpenseError> = buildSet {
    if (input.categoryId == null || category == null) add(ExpenseError.CATEGORY_MISSING)
    else if (!category.isActive && category.id != currentCategoryId) add(ExpenseError.CATEGORY_HIDDEN)

    val amount = input.amount
    if (amount == null || amount <= 0) add(ExpenseError.AMOUNT_MISSING)
    else if (amount > MAX_EXPENSE_AMOUNT) add(ExpenseError.AMOUNT_TOO_LARGE)

    if (input.date.isAfter(today)) add(ExpenseError.DATE_IN_FUTURE)

    val reason = cleanReason(input.reason)
    if (reason != null && lengthOf(reason) > MAX_EXPENSE_REASON_LENGTH) add(ExpenseError.REASON_TOO_LONG)
}

/**
 * The expenseDate to save for the Dhaka day [picked] (the same rule as the khata): today = right now;
 * an earlier day = that Dhaka day at the current Dhaka time of day.
 */
fun expenseDateFor(picked: LocalDate, nowMillis: Long): Long = entryDateFor(picked, nowMillis)

/** A new expense. Call only when [checkExpenseInput] found no problem. */
fun buildExpense(input: ExpenseInput, nowMillis: Long): Expense = Expense(
    expenseCategoryId = requireNotNull(input.categoryId),
    amount = requireNotNull(input.amount),
    reason = cleanReason(input.reason),
    expenseDate = expenseDateFor(input.date, nowMillis),
    isDeleted = false,
    createdAt = nowMillis,
    updatedAt = nowMillis
)

/**
 * The expense after an edit: [createdAt] and the id stay; [Expense.updatedAt] becomes now. When the
 * Dhaka day is not changed, the old expenseDate is kept exactly (the order in the list stays);
 * when the day is changed, the same rule as a new expense applies.
 */
fun applyEdit(old: Expense, input: ExpenseInput, nowMillis: Long): Expense = old.copy(
    expenseCategoryId = requireNotNull(input.categoryId),
    amount = requireNotNull(input.amount),
    reason = cleanReason(input.reason),
    expenseDate = if (DhakaTime.localDate(old.expenseDate) == input.date) old.expenseDate else expenseDateFor(input.date, nowMillis),
    updatedAt = nowMillis
)

/** Written later than its date (an old expense entered now): the list shows "লেখা হয়েছে <date>". */
fun isBackDated(expense: Expense): Boolean =
    DhakaTime.localDate(expense.expenseDate) != DhakaTime.localDate(expense.createdAt)

// =========================================================================================
// The list: search and the period summary
// =========================================================================================

/** Does an expense match the typed [query]? Blank matches all. Reason or category name; Bengali digits accepted. */
fun matchesExpenseSearch(reason: String?, categoryName: String, query: String): Boolean {
    val q = toLatinDigits(query).trim()
    if (q.isEmpty()) return true
    return toLatinDigits(categoryName).contains(q, ignoreCase = true) ||
        (reason != null && toLatinDigits(reason).contains(q, ignoreCase = true))
}

fun searchedExpenses(rows: List<ExpenseRow>, query: String): List<ExpenseRow> =
    rows.filter { matchesExpenseSearch(it.expense.reason, it.categoryName, query) }

data class CategoryTotal(
    val categoryId: Long,
    val name: String,
    val isPersonal: Boolean,
    val total: Long,
    val count: Int
)

/**
 * The summary of a period: [shop] (costs of running the shop), [personal] (পরিবার and other
 * personal categories), [total] = shop + personal, and the total of each category, biggest
 * first (ties by name, then id). Deleted expenses never reach this (the query leaves them out).
 */
data class ExpenseSummary(
    val shop: Long,
    val personal: Long,
    val byCategory: List<CategoryTotal>
) {
    val total: Long get() = shop + personal
}

fun expenseSummary(rows: List<ExpenseRow>): ExpenseSummary {
    val byCategory = rows
        .groupBy { it.expense.expenseCategoryId }
        .map { (id, list) ->
            CategoryTotal(
                categoryId = id,
                name = list.first().categoryName,
                isPersonal = list.first().categoryIsPersonal,
                total = list.sumOf { it.expense.amount },
                count = list.size
            )
        }
        .sortedWith(
            compareByDescending<CategoryTotal> { it.total }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.categoryId }
        )
    return ExpenseSummary(
        shop = rows.filter { !it.categoryIsPersonal }.sumOf { it.expense.amount },
        personal = rows.filter { it.categoryIsPersonal }.sumOf { it.expense.amount },
        byCategory = byCategory
    )
}

data class ExpenseDay(val date: LocalDate, val rows: List<ExpenseRow>)

/** Groups the expenses (already newest first) by the Dhaka day of their expenseDate, keeping the order. */
fun groupExpensesByDay(rows: List<ExpenseRow>): List<ExpenseDay> =
    rows.groupBy { DhakaTime.localDate(it.expense.expenseDate) }.map { (date, list) -> ExpenseDay(date, list) }

/** How long the "ফিরিয়ে আনুন" (undo) button stays after a delete. */
const val UNDO_SECONDS = 5

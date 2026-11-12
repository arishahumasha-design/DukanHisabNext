package com.example.dukanhisabnext.ui.expense

import com.example.dukanhisabnext.data.dao.ExpenseRow
import com.example.dukanhisabnext.data.entity.Expense
import com.example.dukanhisabnext.data.entity.ExpenseCategory
import com.example.dukanhisabnext.expense.CategoryError
import com.example.dukanhisabnext.expense.ExpenseError
import com.example.dukanhisabnext.expense.ExpenseInput
import com.example.dukanhisabnext.expense.MAX_EXPENSE_REASON_LENGTH
import com.example.dukanhisabnext.expense.pickableCategories
import com.example.dukanhisabnext.ledger.KhataPeriod
import com.example.dukanhisabnext.ledger.periodRange
import com.example.dukanhisabnext.util.DhakaTime
import com.example.dukanhisabnext.util.formatTakaPlain
import com.example.dukanhisabnext.util.parseTakaToPaisa
import java.time.LocalDate

// The logic of the expense screens (list, form, categories), as plain Kotlin (no Android) so it
// can be unit-tested on the PC. The rules of the numbers are in expense/ExpenseRules.kt.

// =========================================================================================
// What the list screen remembers (kept in the SavedStateHandle)
// =========================================================================================

/** The chips of the expense list. The first one is the default (আজ). */
enum class ExpensePeriod { TODAY, LAST_7_DAYS, THIS_MONTH, THIS_YEAR, CUSTOM }

fun expensePeriodFromName(name: String?): ExpensePeriod =
    ExpensePeriod.entries.firstOrNull { it.name == name } ?: ExpensePeriod.entries.first()

/** The same Dhaka windows as the Khata chips (one set of range rules for the whole app). */
fun ExpensePeriod.toKhataPeriod(): KhataPeriod = when (this) {
    ExpensePeriod.TODAY -> KhataPeriod.TODAY
    ExpensePeriod.LAST_7_DAYS -> KhataPeriod.LAST_7_DAYS
    ExpensePeriod.THIS_MONTH -> KhataPeriod.THIS_MONTH
    ExpensePeriod.THIS_YEAR -> KhataPeriod.THIS_YEAR
    ExpensePeriod.CUSTOM -> KhataPeriod.CUSTOM
}

/** The time window of a chip (null for "তারিখ বাছুন" until both days are picked). */
fun expenseRange(period: ExpensePeriod, nowMillis: Long, from: LocalDate?, to: LocalDate?): DhakaTime.TimeRange? =
    periodRange(period.toKhataPeriod(), nowMillis, from, to)

/** Everything the user chose on the list screen. */
data class ExpenseControls(
    val period: ExpensePeriod = ExpensePeriod.TODAY,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val query: String = ""
) {
    val searching: Boolean get() = query.isNotBlank()
}

enum class ExpenseEmpty {
    NO_EXPENSES, // the period has no expense at all
    NO_MATCH,    // there are expenses, but the search finds none
    PICK_DATES   // "তারিখ বাছুন" before a range is chosen
}

/**
 * null = there is something to show. [rangeReady] is false for "তারিখ বাছুন" until both days are
 * chosen. [windowCount] is how many expenses the whole period has (before the search).
 */
fun expenseEmptyKind(rangeReady: Boolean, searching: Boolean, windowCount: Int, shownCount: Int): ExpenseEmpty? = when {
    !rangeReady -> ExpenseEmpty.PICK_DATES
    shownCount > 0 -> null
    searching && windowCount > 0 -> ExpenseEmpty.NO_MATCH
    else -> ExpenseEmpty.NO_EXPENSES
}

/** Which lines of the summary box are shown: all three always (it is short). */
enum class ExpenseSummaryLine { SHOP, PERSONAL, TOTAL }

fun expenseSummaryLines(): List<ExpenseSummaryLine> = ExpenseSummaryLine.entries

/** "৳ ১২০": the taka sign, then the money text (formatTaka of the amount). */
fun moneyText(taka: String, formatted: String): String = "$taka $formatted"

/** A personal category gets a small "ব্যক্তিগত" mark in the by-category list and on its rows. */
fun showsPersonalMark(isPersonal: Boolean): Boolean = isPersonal

/** "লেখা হয়েছে <date>" under a back-dated row. [template] fills the date in; null for a normal row. */
fun writtenOnText(expense: Expense, template: (String) -> String): String? =
    if (com.example.dukanhisabnext.expense.isBackDated(expense)) {
        template(com.example.dukanhisabnext.util.formatDate(expense.createdAt))
    } else {
        null
    }

// =========================================================================================
// The form (add and edit)
// =========================================================================================

/** What the expense form holds while the user types. It lives in the ViewModel, so rotation keeps it. */
data class ExpenseFormState(
    val categoryId: Long? = null,
    val amountText: String = "",
    val date: LocalDate,
    val reason: String = ""
)

fun newExpenseForm(today: LocalDate): ExpenseFormState = ExpenseFormState(date = today)

/** The form filled from a saved expense (edit mode). The amount is shown as taka with Latin digits ("12.5"). */
fun formFromExpense(expense: Expense): ExpenseFormState = ExpenseFormState(
    categoryId = expense.expenseCategoryId,
    amountText = formatTakaPlain(expense.amount),
    date = DhakaTime.localDate(expense.expenseDate),
    reason = expense.reason.orEmpty()
)

/** The amount typed, in paisa; null when empty or not a number. */
fun parseExpenseAmount(text: String): Long? = if (text.isBlank()) null else parseTakaToPaisa(text.trim())

fun inputOf(form: ExpenseFormState): ExpenseInput =
    ExpenseInput(form.categoryId, parseExpenseAmount(form.amountText), form.date, form.reason)

/** The reason box never takes more than 200 characters (an emoji counts as one). */
fun limitReasonText(text: String): String =
    if (text.codePointCount(0, text.length) <= MAX_EXPENSE_REASON_LENGTH) {
        text
    } else {
        text.substring(0, text.offsetByCodePoints(0, MAX_EXPENSE_REASON_LENGTH))
    }

fun reasonLength(text: String): Int = text.codePointCount(0, text.length)

/** The box a problem is shown under. */
enum class ExpenseField { CATEGORY, AMOUNT, DATE, REASON }

fun fieldOf(error: ExpenseError): ExpenseField = when (error) {
    ExpenseError.CATEGORY_MISSING, ExpenseError.CATEGORY_HIDDEN -> ExpenseField.CATEGORY
    ExpenseError.AMOUNT_MISSING, ExpenseError.AMOUNT_TOO_LARGE -> ExpenseField.AMOUNT
    ExpenseError.DATE_IN_FUTURE -> ExpenseField.DATE
    ExpenseError.REASON_TOO_LONG -> ExpenseField.REASON
}

fun errorsFor(field: ExpenseField, errors: Set<ExpenseError>): List<ExpenseError> = errors.filter { fieldOf(it) == field }

/** Problems that are not about a typed value. */
enum class ExpenseProblem {
    NOT_FOUND, // the expense is gone or was deleted; nothing was saved
    UNEXPECTED // something unexpected went wrong; nothing was saved
}

/** The "মুছুন" button shows only when editing an expense. */
fun showsDeleteButton(isEdit: Boolean): Boolean = isEdit

/**
 * The first category to preselect in a NEW form: the first shown one. null while no category exists
 * yet (the defaults are being made).
 */
fun defaultCategoryId(all: List<ExpenseCategory>): Long? = pickableCategories(all, currentId = null).firstOrNull()?.id

// =========================================================================================
// The categories screen
// =========================================================================================

/** The box a category problem is shown under. */
enum class CategoryField { NAME, GENERAL }

fun fieldOf(error: CategoryError): CategoryField = when (error) {
    CategoryError.NAME_MISSING, CategoryError.NAME_TOO_LONG, CategoryError.NAME_DUPLICATE -> CategoryField.NAME
    CategoryError.LAST_VISIBLE -> CategoryField.GENERAL
}

fun errorsFor(field: CategoryField, errors: Set<CategoryError>): List<CategoryError> = errors.filter { fieldOf(it) == field }

/** What the add / rename dialog holds. [editingId] null = a new category. */
data class CategoryForm(
    val editingId: Long? = null,
    val name: String = "",
    val isPersonal: Boolean = false
) {
    val isNew: Boolean get() = editingId == null
}

fun categoryFormFor(category: ExpenseCategory): CategoryForm = CategoryForm(category.id, category.name, category.isPersonal)

/** The small marks on a category row, in this order. */
enum class CategoryMark { PERSONAL, HIDDEN }

fun categoryMarks(category: ExpenseCategory): List<CategoryMark> = buildList {
    if (category.isPersonal) add(CategoryMark.PERSONAL)
    if (!category.isActive) add(CategoryMark.HIDDEN)
}

/** The row button says "লুকান" for a shown category and "দেখান" for a hidden one. */
fun showsHideButton(category: ExpenseCategory): Boolean = category.isActive

/** Shown categories first, then the hidden ones; each group A to Z (the database order is kept inside). */
fun categoriesForScreen(all: List<ExpenseCategory>): List<ExpenseCategory> =
    all.sortedWith(compareBy<ExpenseCategory> { !it.isActive }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id })

/** The expense rows of a list, searched by reason or category (the summary never uses this). */
fun shownRows(rows: List<ExpenseRow>, query: String): List<ExpenseRow> =
    com.example.dukanhisabnext.expense.searchedExpenses(rows, query)

/** The category name box never takes more than 40 characters (an emoji counts as one). */
fun limitCategoryName(text: String): String =
    if (text.codePointCount(0, text.length) <= com.example.dukanhisabnext.expense.MAX_CATEGORY_NAME_LENGTH) {
        text
    } else {
        text.substring(0, text.offsetByCodePoints(0, com.example.dukanhisabnext.expense.MAX_CATEGORY_NAME_LENGTH))
    }

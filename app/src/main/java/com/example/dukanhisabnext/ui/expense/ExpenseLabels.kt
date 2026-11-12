package com.example.dukanhisabnext.ui.expense

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import com.example.dukanhisabnext.DokanHisabApp
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.expense.CategoryError
import com.example.dukanhisabnext.expense.ExpenseError
import com.example.dukanhisabnext.expense.defaultNamesFrom

// The Bengali words of the expense screens (all from strings.xml).

fun ExpenseError.messageRes(): Int = when (this) {
    ExpenseError.CATEGORY_MISSING -> R.string.err_expense_category_missing
    ExpenseError.CATEGORY_HIDDEN -> R.string.err_expense_category_hidden
    ExpenseError.AMOUNT_MISSING -> R.string.err_ledger_amount_missing
    ExpenseError.AMOUNT_TOO_LARGE -> R.string.err_ledger_amount_too_large
    ExpenseError.DATE_IN_FUTURE -> R.string.err_ledger_date_future
    ExpenseError.REASON_TOO_LONG -> R.string.err_expense_reason_long
}

fun ExpenseProblem.messageRes(): Int = when (this) {
    ExpenseProblem.NOT_FOUND -> R.string.err_expense_not_found
    ExpenseProblem.UNEXPECTED -> R.string.err_ledger_unexpected // says nothing was saved
}

fun CategoryError.messageRes(): Int = when (this) {
    CategoryError.NAME_MISSING -> R.string.err_cat_name_missing
    CategoryError.NAME_TOO_LONG -> R.string.err_cat_name_long
    CategoryError.NAME_DUPLICATE -> R.string.err_cat_name_duplicate
    CategoryError.LAST_VISIBLE -> R.string.err_cat_last_visible
}

fun CategoryMark.labelRes(): Int = when (this) {
    CategoryMark.PERSONAL -> R.string.cat_tag_personal
    CategoryMark.HIDDEN -> R.string.cat_tag_hidden
}

fun ExpensePeriod.labelRes(): Int = when (this) {
    ExpensePeriod.TODAY -> R.string.khata_chip_today
    ExpensePeriod.LAST_7_DAYS -> R.string.khata_chip_7days
    ExpensePeriod.THIS_MONTH -> R.string.khata_chip_month
    ExpensePeriod.THIS_YEAR -> R.string.khata_chip_year
    ExpensePeriod.CUSTOM -> R.string.khata_chip_custom
}

/**
 * Makes sure the default categories exist (বেতন, ভাড়া, ... ) when an expense screen opens. It is
 * safe to call from every expense screen: each default is made exactly once (by its systemKey), on a
 * new install and after an upgrade alike.
 */
@Composable
fun EnsureDefaultCategories() {
    val app = LocalContext.current.applicationContext as DokanHisabApp
    val names = stringArrayResource(R.array.expense_default_categories).toList()
    val nameOf = remember(names) { defaultNamesFrom(names) }
    LaunchedEffect(Unit) {
        app.expenseRepository.ensureDefaultCategories(nameOf)
    }
}

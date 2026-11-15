package com.example.dukanhisabnext.expense

import com.example.dukanhisabnext.data.dao.ExpenseRow
import com.example.dukanhisabnext.data.entity.Expense
import com.example.dukanhisabnext.data.entity.ExpenseCategory
import com.example.dukanhisabnext.util.AppLanguage
import com.example.dukanhisabnext.util.CurrentLanguage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** An untouched default category follows the language; a renamed default or a user's own category does not. */
class DefaultCategoryNamesTest {

    private val bn = listOf("বেতন", "ভাড়া", "বিল", "চা-নাস্তা", "যাতায়াত", "পরিবার", "অন্যান্য")
    private val en = listOf("Salary", "Rent", "Bills", "Tea & snacks", "Transport", "Family", "Other")

    @Before
    fun names() {
        DefaultCategoryNames.byLanguage = mapOf(AppLanguage.BENGALI to bn, AppLanguage.ENGLISH to en)
    }

    @After
    fun reset() {
        DefaultCategoryNames.byLanguage = emptyMap()
        CurrentLanguage.value = AppLanguage.BENGALI
    }

    @Test
    fun aDefaultCreatedInBengaliShowsInEnglishWhenEnglishIsOn() {
        assertEquals("ভাড়া", shownCategoryName("ভাড়া", "RENT"))
        CurrentLanguage.value = AppLanguage.ENGLISH
        assertEquals("Rent", shownCategoryName("ভাড়া", "RENT"))
        assertEquals("Tea & snacks", shownCategoryName("চা-নাস্তা", "TEA_SNACKS"))
    }

    @Test
    fun aDefaultCreatedInEnglishShowsInBengaliWhenBengaliIsOn() {
        assertEquals("বিল", shownCategoryName("Bills", "BILL"))
        CurrentLanguage.value = AppLanguage.ENGLISH
        assertEquals("Bills", shownCategoryName("Bills", "BILL"))
    }

    @Test
    fun aRenamedDefaultStaysExactlyAsTyped() {
        CurrentLanguage.value = AppLanguage.ENGLISH
        assertEquals("দোকান ভাড়া", shownCategoryName("দোকান ভাড়া", "RENT"))
        CurrentLanguage.value = AppLanguage.BENGALI
        assertEquals("Shop rent", shownCategoryName("Shop rent", "RENT"))
    }

    @Test
    fun aCategoryTheUserMadeStaysExactlyAsTyped() {
        CurrentLanguage.value = AppLanguage.ENGLISH
        assertEquals("ভাড়া", shownCategoryName("ভাড়া", null)) // same text as a default, but not a default
        assertEquals("মাল আনা", shownCategoryName("মাল আনা", null))
        assertEquals("Odd", shownCategoryName("Odd", "NOT_A_DEFAULT_KEY"))
    }

    @Test
    fun theStoredNameMatchesIgnoringCaseAndExtraSpaces() {
        CurrentLanguage.value = AppLanguage.BENGALI
        assertEquals("ভাড়া", shownCategoryName("  rent ", "RENT"))
    }

    @Test
    fun withoutTheNamesNothingChanges() {
        DefaultCategoryNames.byLanguage = emptyMap()
        CurrentLanguage.value = AppLanguage.ENGLISH
        assertEquals("ভাড়া", shownCategoryName("ভাড়া", "RENT"))
    }

    @Test
    fun everyDefaultHasANameInBothLanguages() {
        assertEquals(DefaultCategory.entries.size, bn.size)
        assertEquals(DefaultCategory.entries.size, en.size)
        CurrentLanguage.value = AppLanguage.ENGLISH
        DefaultCategory.entries.forEachIndexed { i, d -> assertEquals(en[i], shownCategoryName(bn[i], d.name)) }
    }

    @Test
    fun searchFindsADefaultByItsShownName() {
        CurrentLanguage.value = AppLanguage.ENGLISH
        val row = ExpenseRow(
            Expense(id = 1, expenseCategoryId = 2, amount = 100, reason = null, expenseDate = 1, createdAt = 1, updatedAt = 1),
            categoryName = "ভাড়া", categoryIsPersonal = false, categorySystemKey = "RENT"
        )
        assertEquals(1, searchedExpenses(listOf(row), "rent").size)
        assertEquals(1, searchedExpenses(listOf(row), "ভাড়া").size)
    }

    @Test
    fun aNewCategoryMayNotTakeTheShownNameOfADefault() {
        CurrentLanguage.value = AppLanguage.ENGLISH
        val all = listOf(ExpenseCategory(id = 2, name = "ভাড়া", systemKey = "RENT"))
        assertTrue(CategoryError.NAME_DUPLICATE in checkCategoryName("Rent", all, selfId = null))
        assertTrue(CategoryError.NAME_DUPLICATE !in checkCategoryName("Shop rent", all, selfId = null))
    }

    @Test
    fun theSummaryKeepsTheKeyForTheReportAndThePdf() {
        val row = ExpenseRow(
            Expense(id = 1, expenseCategoryId = 2, amount = 100, reason = null, expenseDate = 1, createdAt = 1, updatedAt = 1),
            categoryName = "ভাড়া", categoryIsPersonal = false, categorySystemKey = "RENT"
        )
        assertEquals("RENT", expenseSummary(listOf(row)).byCategory.single().systemKey)
    }
}

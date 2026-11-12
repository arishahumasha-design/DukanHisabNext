package com.example.dukanhisabnext.ui.expense

import com.example.dukanhisabnext.data.dao.ExpenseRow
import com.example.dukanhisabnext.data.entity.Expense
import com.example.dukanhisabnext.data.entity.ExpenseCategory
import com.example.dukanhisabnext.expense.CategoryError
import com.example.dukanhisabnext.expense.ExpenseError
import com.example.dukanhisabnext.util.DhakaTime
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The logic of the expense screens: chip state, empty messages, summary lines, the form and the categories. */
class ExpenseScreenLogicTest {

    private val now = Instant.parse("2026-11-12T08:30:00Z").toEpochMilli() // 14:30 in Dhaka
    private val today = LocalDate.of(2026, 11, 12)

    private fun cat(id: Long, name: String, active: Boolean = true, personal: Boolean = false) =
        ExpenseCategory(id = id, name = name, isPersonal = personal, isActive = active)

    private fun expense(amount: Long = 12_500, date: Long = now, created: Long = date, reason: String? = "বিদ্যুৎ বিল") =
        Expense(id = 7, expenseCategoryId = 3, amount = amount, reason = reason, expenseDate = date, createdAt = created, updatedAt = created)

    // =====================================================================================
    // The list: what the screen remembers
    // =====================================================================================

    @Test
    fun theListOpensOnTodayAndTheChipsAreInTheAgreedOrder() {
        assertEquals(ExpensePeriod.TODAY, ExpenseControls().period)
        assertEquals(
            listOf(ExpensePeriod.TODAY, ExpensePeriod.LAST_7_DAYS, ExpensePeriod.THIS_MONTH, ExpensePeriod.THIS_YEAR, ExpensePeriod.CUSTOM),
            ExpensePeriod.entries
        )
        assertEquals("", ExpenseControls().query)
        assertFalse(ExpenseControls().searching)
        assertTrue(ExpenseControls(query = "ভাড়া").searching)
        assertFalse(ExpenseControls(query = "   ").searching)
    }

    @Test
    fun aSavedChipIsReadBackAndAnUnknownOneFallsBackToToday() {
        for (p in ExpensePeriod.entries) assertEquals(p, expensePeriodFromName(p.name))
        assertEquals(ExpensePeriod.TODAY, expensePeriodFromName(null))
        assertEquals(ExpensePeriod.TODAY, expensePeriodFromName("nonsense"))
    }

    @Test
    fun theChipsUseTheSameDhakaWindowsAsTheKhataChips() {
        val todayRange = expenseRange(ExpensePeriod.TODAY, now, null, null)!!
        assertEquals(DhakaTime.startOfDay(today), todayRange.startMillis)
        assertEquals(DhakaTime.startOfDay(today.plusDays(1)), todayRange.endMillisExclusive)

        val week = expenseRange(ExpensePeriod.LAST_7_DAYS, now, null, null)!!
        assertEquals(DhakaTime.startOfDay(LocalDate.of(2026, 11, 6)), week.startMillis)
        assertEquals(DhakaTime.startOfDay(LocalDate.of(2026, 11, 13)), week.endMillisExclusive)

        val month = expenseRange(ExpensePeriod.THIS_MONTH, now, null, null)!!
        assertEquals(DhakaTime.startOfDay(LocalDate.of(2026, 11, 1)), month.startMillis)
        val year = expenseRange(ExpensePeriod.THIS_YEAR, now, null, null)!!
        assertEquals(DhakaTime.startOfDay(LocalDate.of(2027, 1, 1)), year.endMillisExclusive)
    }

    @Test
    fun theCustomChipNeedsBothDays() {
        assertNull(expenseRange(ExpensePeriod.CUSTOM, now, null, null))
        assertNull(expenseRange(ExpensePeriod.CUSTOM, now, today, null))
        val range = expenseRange(ExpensePeriod.CUSTOM, now, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))!!
        assertEquals(DhakaTime.startOfDay(LocalDate.of(2026, 10, 1)), range.startMillis)
        assertEquals(DhakaTime.startOfDay(LocalDate.of(2026, 11, 1)), range.endMillisExclusive)
    }

    // =====================================================================================
    // The list: empty messages and the summary box
    // =====================================================================================

    @Test
    fun theEmptyMessageDependsOnThePeriodAndTheSearch() {
        assertNull(expenseEmptyKind(rangeReady = true, searching = false, windowCount = 4, shownCount = 4))
        assertEquals(ExpenseEmpty.NO_EXPENSES, expenseEmptyKind(true, false, windowCount = 0, shownCount = 0))
        assertEquals(ExpenseEmpty.NO_EXPENSES, expenseEmptyKind(true, true, windowCount = 0, shownCount = 0))
        assertEquals(ExpenseEmpty.NO_MATCH, expenseEmptyKind(true, true, windowCount = 4, shownCount = 0))
        assertNull(expenseEmptyKind(true, true, windowCount = 4, shownCount = 1))
        assertEquals(ExpenseEmpty.PICK_DATES, expenseEmptyKind(rangeReady = false, searching = false, windowCount = 0, shownCount = 0))
        assertEquals(ExpenseEmpty.PICK_DATES, expenseEmptyKind(rangeReady = false, searching = true, windowCount = 0, shownCount = 0))
    }

    @Test
    fun theSummaryBoxAlwaysShowsShopPersonalAndTotalInThatOrder() {
        assertEquals(
            listOf(ExpenseSummaryLine.SHOP, ExpenseSummaryLine.PERSONAL, ExpenseSummaryLine.TOTAL),
            expenseSummaryLines()
        )
    }

    @Test
    fun moneyIsShownWithTheTakaSign() {
        assertEquals("৳ ১২৫", moneyText("৳", "১২৫"))
        assertEquals("৳ ০", moneyText("৳", "০"))
    }

    @Test
    fun onlyPersonalCategoriesGetTheMark() {
        assertTrue(showsPersonalMark(true))
        assertFalse(showsPersonalMark(false))
    }

    @Test
    fun aBackDatedRowShowsWhenItWasWritten() {
        val template = { date: String -> "লেখা হয়েছে $date" }
        assertNull(writtenOnText(expense(), template))
        val oldDay = Instant.parse("2026-10-01T08:30:00Z").toEpochMilli()
        assertEquals("লেখা হয়েছে ১২/১১/২০২৬", writtenOnText(expense(date = oldDay, created = now), template))
    }

    @Test
    fun theSearchOnlyFiltersRowsNotTheSummary() {
        val rows = listOf(
            ExpenseRow(expense(amount = 500_000, reason = "দোকান ভাড়া"), "ভাড়া", false),
            ExpenseRow(expense(amount = 2_000, reason = "চা"), "চা-নাস্তা", false)
        )
        assertEquals(1, shownRows(rows, "ভাড়া").size)
        assertEquals(2, shownRows(rows, "").size)
        // The summary is built from ALL rows by the ViewModel (see ExpenseRules.expenseSummary): the same list, unfiltered.
        assertEquals(502_000L, com.example.dukanhisabnext.expense.expenseSummary(rows).total)
    }

    // =====================================================================================
    // The form
    // =====================================================================================

    @Test
    fun aNewFormStartsTodayWithNothingTyped() {
        val f = newExpenseForm(today)
        assertEquals(today, f.date)
        assertNull(f.categoryId)
        assertEquals("", f.amountText)
        assertEquals("", f.reason)
    }

    @Test
    fun anEditFormIsFilledFromTheSavedExpense() {
        val f = formFromExpense(expense(amount = 12_500, reason = "বিদ্যুৎ বিল"))
        assertEquals(3L, f.categoryId)
        assertEquals("125", f.amountText)         // taka with Latin digits, trailing zeros dropped
        assertEquals(today, f.date)
        assertEquals("বিদ্যুৎ বিল", f.reason)
        assertEquals("12.5", formFromExpense(expense(amount = 1_250)).amountText)
        assertEquals("12.05", formFromExpense(expense(amount = 1_205)).amountText)
        assertEquals("0.05", formFromExpense(expense(amount = 5)).amountText)
        assertEquals("", formFromExpense(expense(reason = null)).reason)
    }

    @Test
    fun theTypedAmountComesBackAsTheSamePaisaAfterAnEditRoundTrip() {
        for (paisa in listOf(1L, 5L, 99L, 100L, 1_250L, 1_205L, 12_345L, 999_999_999L)) {
            val form = formFromExpense(expense(amount = paisa))
            assertEquals("paisa $paisa", paisa, parseExpenseAmount(form.amountText))
        }
    }

    @Test
    fun theAmountIsReadAsTakaAndKeptInPaisa() {
        assertNull(parseExpenseAmount(""))
        assertNull(parseExpenseAmount("  "))
        assertNull(parseExpenseAmount("1.2.3"))
        assertEquals(25_000L, parseExpenseAmount("250"))
        assertEquals(1_250L, parseExpenseAmount("12.5"))
        assertEquals(0L, parseExpenseAmount("0"))
    }

    @Test
    fun theFormBecomesAnExpenseInput() {
        val f = newExpenseForm(today).copy(categoryId = 4, amountText = "120", reason = " ভাড়া ")
        val input = inputOf(f)
        assertEquals(4L, input.categoryId)
        assertEquals(12_000L, input.amount)
        assertEquals(today, input.date)
        assertEquals(" ভাড়া ", input.reason) // the repository trims it
    }

    @Test
    fun theReasonBoxTakesAtMost200Characters() {
        assertEquals("কারণ", limitReasonText("কারণ"))
        assertEquals("ক".repeat(200), limitReasonText("ক".repeat(200)))
        assertEquals("ক".repeat(200), limitReasonText("ক".repeat(300)))
        assertEquals("😀".repeat(200), limitReasonText("😀".repeat(250)))
        assertEquals(3, reasonLength("কারণ".take(3)))
        assertEquals(2, reasonLength("😀😀"))
    }

    @Test
    fun everyProblemIsShownUnderTheRightBox() {
        assertEquals(ExpenseField.CATEGORY, fieldOf(ExpenseError.CATEGORY_MISSING))
        assertEquals(ExpenseField.CATEGORY, fieldOf(ExpenseError.CATEGORY_HIDDEN))
        assertEquals(ExpenseField.AMOUNT, fieldOf(ExpenseError.AMOUNT_MISSING))
        assertEquals(ExpenseField.AMOUNT, fieldOf(ExpenseError.AMOUNT_TOO_LARGE))
        assertEquals(ExpenseField.DATE, fieldOf(ExpenseError.DATE_IN_FUTURE))
        assertEquals(ExpenseField.REASON, fieldOf(ExpenseError.REASON_TOO_LONG))
        assertEquals(ExpenseError.entries.size, ExpenseError.entries.count { fieldOf(it) in ExpenseField.entries })
    }

    @Test
    fun aBoxShowsOnlyItsOwnProblems() {
        val errors = setOf(ExpenseError.AMOUNT_MISSING, ExpenseError.DATE_IN_FUTURE)
        assertEquals(listOf(ExpenseError.AMOUNT_MISSING), errorsFor(ExpenseField.AMOUNT, errors))
        assertEquals(listOf(ExpenseError.DATE_IN_FUTURE), errorsFor(ExpenseField.DATE, errors))
        assertTrue(errorsFor(ExpenseField.CATEGORY, errors).isEmpty())
        assertTrue(errorsFor(ExpenseField.REASON, errors).isEmpty())
    }

    @Test
    fun theDeleteButtonShowsOnlyWhenEditing() {
        assertTrue(showsDeleteButton(isEdit = true))
        assertFalse(showsDeleteButton(isEdit = false))
    }

    @Test
    fun aNewFormPreselectsTheFirstShownCategory() {
        assertNull(defaultCategoryId(emptyList()))
        assertEquals(2L, defaultCategoryId(listOf(cat(1, "পুরনো", active = false), cat(2, "ভাড়া"), cat(3, "বিল"))))
        assertNull(defaultCategoryId(listOf(cat(1, "পুরনো", active = false))))
    }

    @Test
    fun thereAreTwoProblemsThatAreNotAboutATypedValue() {
        assertEquals(setOf(ExpenseProblem.NOT_FOUND, ExpenseProblem.UNEXPECTED), ExpenseProblem.entries.toSet())
    }

    // =====================================================================================
    // The categories screen
    // =====================================================================================

    @Test
    fun aCategoryProblemIsShownUnderTheNameOrOnTheScreen() {
        assertEquals(CategoryField.NAME, fieldOf(CategoryError.NAME_MISSING))
        assertEquals(CategoryField.NAME, fieldOf(CategoryError.NAME_TOO_LONG))
        assertEquals(CategoryField.NAME, fieldOf(CategoryError.NAME_DUPLICATE))
        assertEquals(CategoryField.GENERAL, fieldOf(CategoryError.LAST_VISIBLE))
        assertEquals(listOf(CategoryError.NAME_DUPLICATE), errorsFor(CategoryField.NAME, setOf(CategoryError.NAME_DUPLICATE, CategoryError.LAST_VISIBLE)))
        assertEquals(listOf(CategoryError.LAST_VISIBLE), errorsFor(CategoryField.GENERAL, setOf(CategoryError.NAME_DUPLICATE, CategoryError.LAST_VISIBLE)))
    }

    @Test
    fun theDialogIsNewOrAnEditOfACategory() {
        assertTrue(CategoryForm().isNew)
        assertFalse(CategoryForm().isPersonal)
        val form = categoryFormFor(cat(5, "যাতায়াত", personal = true))
        assertFalse(form.isNew)
        assertEquals(5L, form.editingId)
        assertEquals("যাতায়াত", form.name)
        assertTrue(form.isPersonal)
    }

    @Test
    fun aRowShowsThePersonalAndHiddenMarks() {
        assertTrue(categoryMarks(cat(1, "ক")).isEmpty())
        assertEquals(listOf(CategoryMark.PERSONAL), categoryMarks(cat(1, "ক", personal = true)))
        assertEquals(listOf(CategoryMark.HIDDEN), categoryMarks(cat(1, "ক", active = false)))
        assertEquals(listOf(CategoryMark.PERSONAL, CategoryMark.HIDDEN), categoryMarks(cat(1, "ক", active = false, personal = true)))
    }

    @Test
    fun theRowButtonSaysHideForAShownCategoryAndShowForAHiddenOne() {
        assertTrue(showsHideButton(cat(1, "ক")))
        assertFalse(showsHideButton(cat(1, "ক", active = false)))
    }

    @Test
    fun shownCategoriesComeFirstThenHiddenOnesEachAToZ() {
        val all = listOf(cat(1, "ঘ", active = false), cat(2, "খ"), cat(3, "ক"), cat(4, "ক", active = false), cat(5, "Zed"))
        assertEquals(listOf(5L, 3L, 2L, 4L, 1L), categoriesForScreen(all).map { it.id })
    }

    @Test
    fun theNameBoxTakesAtMost40Characters() {
        assertEquals("ভাড়া", limitCategoryName("ভাড়া"))
        assertEquals("ক".repeat(40), limitCategoryName("ক".repeat(40)))
        assertEquals("ক".repeat(40), limitCategoryName("ক".repeat(60)))
        assertEquals("😀".repeat(40), limitCategoryName("😀".repeat(50)))
    }
}

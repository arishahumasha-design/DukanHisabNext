package com.example.dukanhisabnext.expense

import com.example.dukanhisabnext.data.dao.ExpenseRow
import com.example.dukanhisabnext.data.entity.Expense
import com.example.dukanhisabnext.data.entity.ExpenseCategory
import com.example.dukanhisabnext.util.DhakaTime
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseRulesTest {

    // 14:30 on 12 Nov 2026 in Dhaka (= 08:30 UTC).
    private val now = Instant.parse("2026-11-12T08:30:00Z").toEpochMilli()
    private val today = LocalDate.of(2026, 11, 12)

    private val defaultNames = listOf("বেতন", "ভাড়া", "বিল", "চা-নাস্তা", "যাতায়াত", "পরিবার", "অন্যান্য")

    private fun cat(id: Long, name: String, active: Boolean = true, personal: Boolean = false, key: String? = null) =
        ExpenseCategory(id = id, name = name, isPersonal = personal, isActive = active, systemKey = key)

    private val rent = cat(2, "ভাড়া", key = "RENT")
    private val oldHidden = cat(9, "পুরনো", active = false)

    private fun input(categoryId: Long? = 2, amount: Long? = 50_000, date: LocalDate = today, reason: String? = null) =
        ExpenseInput(categoryId, amount, date, reason)

    // =====================================================================================
    // Default categories
    // =====================================================================================

    @Test
    fun thereAreSevenDefaultsAndOnlyFamilyIsPersonal() {
        assertEquals(
            listOf("SALARY", "RENT", "BILL", "TEA_SNACKS", "TRANSPORT", "FAMILY", "OTHER"),
            DefaultCategory.entries.map { it.name }
        )
        assertEquals(listOf(DefaultCategory.FAMILY), DefaultCategory.entries.filter { it.isPersonal })
    }

    @Test
    fun theDefaultNamesComeFromTheListInOrder() {
        val nameOf = defaultNamesFrom(defaultNames)
        assertEquals("বেতন", nameOf(DefaultCategory.SALARY))
        assertEquals("পরিবার", nameOf(DefaultCategory.FAMILY))
        assertEquals("অন্যান্য", nameOf(DefaultCategory.OTHER))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aWrongNumberOfDefaultNamesIsRefused() {
        defaultNamesFrom(defaultNames.dropLast(1))
    }

    @Test
    fun aDefaultIsCreatedWhenNothingMatches() {
        assertEquals(Seed.Create, seedAction(DefaultCategory.SALARY, "বেতন", emptyList()))
        assertEquals(Seed.Create, seedAction(DefaultCategory.SALARY, "বেতন", listOf(rent)))
    }

    @Test
    fun aDefaultWithItsSystemKeyIsNeverMadeAgainEvenIfRenamedOrHidden() {
        val renamed = cat(1, "কর্মচারীর বেতন", key = "SALARY")
        val hidden = cat(1, "বেতন", active = false, key = "SALARY")
        assertEquals(Seed.Exists, seedAction(DefaultCategory.SALARY, "বেতন", listOf(renamed)))
        assertEquals(Seed.Exists, seedAction(DefaultCategory.SALARY, "বেতন", listOf(hidden)))
    }

    @Test
    fun aUserCategoryWithTheSameNameIsAdoptedNotDuplicated() {
        val mine = cat(5, "  পরিবার ")
        assertEquals(Seed.Adopt(mine), seedAction(DefaultCategory.FAMILY, "পরিবার", listOf(mine)))
        // A category with the same name that already belongs to ANOTHER default is not taken.
        val other = cat(6, "পরিবার", key = "OTHER")
        assertEquals(Seed.Create, seedAction(DefaultCategory.FAMILY, "পরিবার", listOf(other)))
    }

    // =====================================================================================
    // Category names, hiding, the picker
    // =====================================================================================

    @Test
    fun aCategoryNameIsTrimmedAndSpacesSqueezed() {
        assertEquals("দোকান ভাড়া", cleanCategoryName("  দোকান   ভাড়া "))
        assertEquals("cng ভাড়া", normalizeCategoryName("  CNG   ভাড়া "))
    }

    @Test
    fun aNameIsRequiredAndAtMost40Characters() {
        assertEquals(setOf(CategoryError.NAME_MISSING), checkCategoryName("   ", emptyList(), null))
        assertTrue(checkCategoryName("ক".repeat(40), emptyList(), null).isEmpty())
        assertEquals(setOf(CategoryError.NAME_TOO_LONG), checkCategoryName("ক".repeat(41), emptyList(), null))
    }

    @Test
    fun aDuplicateNameIsRefusedAlsoAgainstHiddenOnesAndCaseIgnored() {
        val all = listOf(rent, oldHidden, cat(3, "Transport"))
        assertEquals(setOf(CategoryError.NAME_DUPLICATE), checkCategoryName(" ভাড়া ", all, null))
        assertEquals(setOf(CategoryError.NAME_DUPLICATE), checkCategoryName("পুরনো", all, null)) // hidden
        assertEquals(setOf(CategoryError.NAME_DUPLICATE), checkCategoryName("TRANSPORT", all, null))
        // A category may keep its own name when it is renamed (or only its flag changes).
        assertTrue(checkCategoryName("ভাড়া", all, selfId = 2).isEmpty())
        assertEquals(setOf(CategoryError.NAME_DUPLICATE), checkCategoryName("Transport", all, selfId = 2))
    }

    @Test
    fun theLastShownCategoryCannotBeHidden() {
        val only = cat(1, "একমাত্র")
        assertFalse(canHide(only, listOf(only, oldHidden)))
        assertTrue(canHide(only, listOf(only, rent)))
        assertTrue(canHide(oldHidden, listOf(oldHidden))) // already hidden: nothing to stop
    }

    @Test
    fun thePickerOffersShownCategoriesPlusTheEditedExpensesOwn() {
        val all = listOf(rent, oldHidden)
        assertEquals(listOf(rent), pickableCategories(all, currentId = null))
        assertEquals(listOf(rent, oldHidden), pickableCategories(all, currentId = 9))
    }

    // =====================================================================================
    // An expense: checks
    // =====================================================================================

    @Test
    fun aGoodExpenseHasNoProblem() {
        assertTrue(checkExpenseInput(input(), today, rent).isEmpty())
        assertTrue(checkExpenseInput(input(date = LocalDate.of(2020, 1, 1)), today, rent).isEmpty()) // old dates are fine
    }

    @Test
    fun theCategoryMustExistAndBeShown() {
        assertEquals(setOf(ExpenseError.CATEGORY_MISSING), checkExpenseInput(input(categoryId = null), today, null))
        assertEquals(setOf(ExpenseError.CATEGORY_MISSING), checkExpenseInput(input(categoryId = 99), today, null))
        assertEquals(setOf(ExpenseError.CATEGORY_HIDDEN), checkExpenseInput(input(categoryId = 9), today, oldHidden))
    }

    @Test
    fun anEditMayKeepAHiddenCategoryButNotMoveIntoOne() {
        assertTrue(checkExpenseInput(input(categoryId = 9), today, oldHidden, currentCategoryId = 9).isEmpty())
        assertEquals(
            setOf(ExpenseError.CATEGORY_HIDDEN),
            checkExpenseInput(input(categoryId = 9), today, oldHidden, currentCategoryId = 2)
        )
    }

    @Test
    fun theAmountMustBeAboveZeroAndAtMostOneCrore() {
        assertEquals(1_000_000_000L, MAX_EXPENSE_AMOUNT)
        assertEquals(setOf(ExpenseError.AMOUNT_MISSING), checkExpenseInput(input(amount = null), today, rent))
        assertEquals(setOf(ExpenseError.AMOUNT_MISSING), checkExpenseInput(input(amount = 0), today, rent))
        assertTrue(checkExpenseInput(input(amount = 1), today, rent).isEmpty())
        assertTrue(checkExpenseInput(input(amount = MAX_EXPENSE_AMOUNT), today, rent).isEmpty())
        assertEquals(setOf(ExpenseError.AMOUNT_TOO_LARGE), checkExpenseInput(input(amount = MAX_EXPENSE_AMOUNT + 1), today, rent))
    }

    @Test
    fun aFutureDayIsRefused() {
        assertEquals(setOf(ExpenseError.DATE_IN_FUTURE), checkExpenseInput(input(date = today.plusDays(1)), today, rent))
    }

    @Test
    fun theReasonIsOptionalTrimmedAndAtMost200Characters() {
        assertNull(cleanReason("   "))
        assertEquals("দোকান ভাড়া", cleanReason("  দোকান ভাড়া "))
        assertTrue(checkExpenseInput(input(reason = "ক".repeat(200)), today, rent).isEmpty())
        assertTrue(checkExpenseInput(input(reason = "😀".repeat(200)), today, rent).isEmpty())
        assertEquals(setOf(ExpenseError.REASON_TOO_LONG), checkExpenseInput(input(reason = "ক".repeat(201)), today, rent))
    }

    @Test
    fun severalProblemsAreAllReported() {
        assertEquals(
            setOf(ExpenseError.CATEGORY_MISSING, ExpenseError.AMOUNT_MISSING, ExpenseError.DATE_IN_FUTURE),
            checkExpenseInput(ExpenseInput(null, null, today.plusDays(2)), today, null)
        )
    }

    // =====================================================================================
    // Build, edit, dates
    // =====================================================================================

    @Test
    fun aNewExpenseIsPositiveTrimmedAndDatedNowWhenToday() {
        val e = buildExpense(input(amount = 12_345, reason = "  বিদ্যুৎ বিল "), now)
        assertEquals(2L, e.expenseCategoryId)
        assertEquals(12_345L, e.amount)
        assertEquals("বিদ্যুৎ বিল", e.reason)
        assertEquals(now, e.expenseDate)
        assertEquals(now, e.createdAt)
        assertEquals(now, e.updatedAt)
        assertFalse(e.isDeleted)
        assertFalse(isBackDated(e))
    }

    @Test
    fun anEarlierDayKeepsTheDhakaTimeOfDayAndIsBackDated() {
        val e = buildExpense(input(date = LocalDate.of(2026, 10, 1)), now)
        assertEquals(Instant.parse("2026-10-01T08:30:00Z").toEpochMilli(), e.expenseDate)
        assertTrue(isBackDated(e))
    }

    @Test
    fun anEditKeepsCreatedAtAndTheExactDateWhenTheDayIsUnchanged() {
        val created = now - 3 * 24 * 60 * 60 * 1000L // three days ago, 14:30
        val old = Expense(id = 7, expenseCategoryId = 2, amount = 10_000, reason = "পুরনো", expenseDate = created, createdAt = created, updatedAt = created)
        val later = now + 60_000

        val sameDay = applyEdit(old, input(categoryId = 3, amount = 20_000, date = DhakaTime.localDate(created), reason = " নতুন "), later)
        assertEquals(7L, sameDay.id)
        assertEquals(3L, sameDay.expenseCategoryId)
        assertEquals(20_000L, sameDay.amount)
        assertEquals("নতুন", sameDay.reason)
        assertEquals(created, sameDay.expenseDate) // the same moment: the order in the list stays
        assertEquals(created, sameDay.createdAt)
        assertEquals(later, sameDay.updatedAt)

        val movedToToday = applyEdit(old, input(date = today), later)
        assertEquals(later, movedToToday.expenseDate)
        assertEquals(created, movedToToday.createdAt)
    }

    // =====================================================================================
    // Search, summary, grouping
    // =====================================================================================

    private fun row(id: Long, categoryId: Long, name: String, amount: Long, personal: Boolean = false, reason: String? = null, date: Long = now) =
        ExpenseRow(Expense(id = id, expenseCategoryId = categoryId, amount = amount, reason = reason, expenseDate = date, createdAt = date, updatedAt = date), name, personal)

    private val rows = listOf(
        row(1, 2, "ভাড়া", 500_000, reason = "নভেম্বরের দোকান ভাড়া"),
        row(2, 4, "চা-নাস্তা", 3_000),
        row(3, 6, "পরিবার", 20_000, personal = true, reason = "বাজার"),
        row(4, 4, "চা-নাস্তা", 2_000, reason = "অতিথি ২ জন"),
        row(5, 6, "পরিবার", 5_000, personal = true)
    )

    @Test
    fun searchFindsTheReasonOrTheCategoryWithBengaliOrLatinDigits() {
        assertEquals(5, searchedExpenses(rows, "").size)
        assertEquals(listOf(2L, 4L), searchedExpenses(rows, "চা-নাস্তা").map { it.expense.id })
        assertEquals(listOf(1L), searchedExpenses(rows, "দোকান").map { it.expense.id })
        assertEquals(listOf(4L), searchedExpenses(rows, "2 জন").map { it.expense.id })
        assertEquals(listOf(4L), searchedExpenses(rows, "২ জন").map { it.expense.id })
        assertTrue(searchedExpenses(rows, "zzz").isEmpty())
    }

    @Test
    fun theSummarySplitsShopAndPersonalAndAddsUp() {
        val s = expenseSummary(rows)
        assertEquals(505_000L, s.shop)
        assertEquals(25_000L, s.personal)
        assertEquals(530_000L, s.total)
        assertEquals(s.total, s.byCategory.sumOf { it.total })
        assertEquals(s.shop, s.byCategory.filter { !it.isPersonal }.sumOf { it.total })
        assertEquals(s.personal, s.byCategory.filter { it.isPersonal }.sumOf { it.total })
    }

    @Test
    fun categoriesAreBiggestFirstWithTheirCounts() {
        val byCat = expenseSummary(rows).byCategory
        assertEquals(listOf("ভাড়া", "পরিবার", "চা-নাস্তা"), byCat.map { it.name })
        assertEquals(listOf(1, 2, 2), byCat.map { it.count })
        assertEquals(listOf(500_000L, 25_000L, 5_000L), byCat.map { it.total })
        assertEquals(listOf(false, true, false), byCat.map { it.isPersonal })
    }

    @Test
    fun equalTotalsAreOrderedByNameThenId() {
        val tie = listOf(row(1, 8, "খ", 100), row(2, 7, "ক", 100), row(3, 9, "ক", 100))
        assertEquals(listOf(7L, 9L, 8L), expenseSummary(tie).byCategory.map { it.categoryId })
    }

    @Test
    fun anEmptyPeriodIsAllZero() {
        val s = expenseSummary(emptyList())
        assertEquals(0L, s.total)
        assertTrue(s.byCategory.isEmpty())
    }

    @Test
    fun theSummaryAlwaysAddsUpOnRandomData() {
        val random = java.util.Random(7)
        repeat(200) { round ->
            val list = (0 until random.nextInt(25)).map { i ->
                val categoryId = 1L + random.nextInt(6)
                row(i.toLong(), categoryId, "c$categoryId", 1L + random.nextInt(5_000_000), personal = categoryId == 6L)
            }
            val s = expenseSummary(list)
            assertEquals("round $round", list.sumOf { it.expense.amount }, s.total)
            assertEquals("round $round", s.total, s.shop + s.personal)
            assertEquals("round $round", s.total, s.byCategory.sumOf { it.total })
            assertEquals("round $round", list.size, s.byCategory.sumOf { it.count })
        }
    }

    @Test
    fun expensesAreGroupedByTheDhakaDay() {
        val day = 24L * 60 * 60 * 1000
        val list = listOf(
            row(3, 2, "ভাড়া", 1, date = now),
            // 23:50 on 11 Nov in Dhaka = 17:50 UTC: still 11 Nov.
            row(2, 2, "ভাড়া", 1, date = Instant.parse("2026-11-11T17:50:00Z").toEpochMilli()),
            row(1, 2, "ভাড়া", 1, date = now - 5 * day)
        )
        val groups = groupExpensesByDay(list)
        assertEquals(listOf(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 7)), groups.map { it.date })
    }

    @Test
    fun theUndoLastsFiveSeconds() {
        assertEquals(5, UNDO_SECONDS)
    }
}

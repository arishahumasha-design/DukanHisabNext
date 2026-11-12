package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.ExpenseCategory
import com.example.dukanhisabnext.expense.CategoryError
import com.example.dukanhisabnext.expense.DefaultCategory
import com.example.dukanhisabnext.expense.ExpenseError
import com.example.dukanhisabnext.expense.ExpenseInput
import com.example.dukanhisabnext.expense.MAX_EXPENSE_AMOUNT
import com.example.dukanhisabnext.util.DhakaTime
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phone tests for expenses (M6): the default categories (made once by systemKey), add / edit /
 * delete / undo with one lock, one transaction and form ids, the categories (unique names, hide,
 * last shown), and the shared conditions (deleted never counts; personal not a shop cost).
 * In-memory database only.
 */
@RunWith(AndroidJUnit4::class)
class ExpenseRepositoryTest : DatabaseTestBase() {

    // 14:30 on 12 Nov 2026 in Dhaka.
    private val nov12 = Instant.parse("2026-11-12T08:30:00Z").toEpochMilli()
    private val today = LocalDate.of(2026, 11, 12)
    private var clockNow = nov12

    private val names = listOf("বেতন", "ভাড়া", "বিল", "চা-নাস্তা", "যাতায়াত", "পরিবার", "অন্যান্য")
    private fun nameOf(d: DefaultCategory) = names[d.ordinal]

    private fun repo(afterWrite: () -> Unit = {}) = ExpenseRepository(db, { clockNow }, afterWrite)

    private var nextForm = 0
    private fun form() = "form-${nextForm++}"

    private val all = DhakaTime.TimeRange(0L, Long.MAX_VALUE)

    private suspend fun categories() = db.expenseDao().getCategories()
    private suspend fun categoryId(key: String) = db.expenseDao().getCategoryBySystemKey(key)!!.id

    private suspend fun seeded(): ExpenseRepository = repo().also { it.ensureDefaultCategories(::nameOf) }

    private fun saved(result: ExpenseSaveResult): ExpenseSaveResult.Saved =
        result as? ExpenseSaveResult.Saved ?: throw AssertionError("Expected Saved, got $result")

    private suspend fun rows() = db.expenseDao().observeRowsInRange(all.startMillis, all.endMillisExclusive).first()

    // =====================================================================================
    // The default categories
    // =====================================================================================

    @Test
    fun theDefaultsAreMadeOnceWithTheirKeysAndFamilyIsPersonal() = runBlocking {
        val repo = repo()
        repo.ensureDefaultCategories(::nameOf)
        repo.ensureDefaultCategories(::nameOf)
        repo().ensureDefaultCategories(::nameOf) // also from another repository

        val cats = categories()
        assertEquals(7, cats.size)
        assertEquals(DefaultCategory.entries.map { it.name }.toSet(), cats.map { it.systemKey }.toSet())
        assertEquals(listOf("পরিবার"), cats.filter { it.isPersonal }.map { it.name })
        assertTrue(cats.all { it.isActive })
    }

    @Test
    fun aRenamedOrHiddenDefaultIsNotMadeAgain() = runBlocking {
        val repo = seeded()
        val salary = db.expenseDao().getCategoryBySystemKey("SALARY")!!
        repo.updateCategory(salary.id, "কর্মচারীর বেতন", isPersonal = false)
        repo.setCategoryVisible(categoryId("TEA_SNACKS"), visible = false)

        repo.ensureDefaultCategories(::nameOf)

        val cats = categories()
        assertEquals(7, cats.size)
        assertEquals("কর্মচারীর বেতন", cats.single { it.systemKey == "SALARY" }.name)
        assertFalse(cats.single { it.systemKey == "TEA_SNACKS" }.isActive)
    }

    @Test
    fun aCategoryTheUserAlreadyMadeIsAdoptedNotDoubled() = runBlocking {
        val mine = db.expenseDao().insertCategory(ExpenseCategory(name = "পরিবার"))
        repo().ensureDefaultCategories(::nameOf)

        val cats = categories()
        assertEquals(7, cats.size)
        val family = cats.single { it.name == "পরিবার" }
        assertEquals(mine, family.id)
        assertEquals("FAMILY", family.systemKey)
        assertTrue(family.isPersonal)
    }

    @Test
    fun aCrashWhileSeedingSavesNoCategory() = runBlocking {
        try {
            repo { throw IllegalStateException("simulated crash") }.ensureDefaultCategories(::nameOf)
            fail("Expected the simulated crash")
        } catch (expected: IllegalStateException) {
            // good
        }
        assertEquals(0, categories().size)
        seeded()
        assertEquals(7, categories().size)
    }

    @Test
    fun theDatabaseRefusesTwoCategoriesWithTheSameSystemKey() = runBlocking {
        seeded()
        assertConstraintFails { db.expenseDao().insertCategory(ExpenseCategory(name = "আবার", systemKey = "RENT")) }
    }

    // =====================================================================================
    // Add, edit, delete, undo
    // =====================================================================================

    @Test
    fun anExpenseIsSavedWithEveryFieldAndShownWithItsCategory() = runBlocking {
        val repo = seeded()
        val rent = categoryId("RENT")
        val e = saved(repo.addExpense(form(), ExpenseInput(rent, 500_000, today, "  নভেম্বরের ভাড়া "))).expense

        assertEquals(e, db.expenseDao().getExpense(e.id))
        assertEquals("নভেম্বরের ভাড়া", e.reason)
        assertEquals(nov12, e.expenseDate)
        assertEquals(nov12, e.createdAt)
        assertEquals(nov12, e.updatedAt)
        val row = rows().single()
        assertEquals("ভাড়া", row.categoryName)
        assertFalse(row.categoryIsPersonal)
    }

    @Test
    fun aBackDatedExpenseKeepsItsDhakaDay() = runBlocking {
        val repo = seeded()
        val e = saved(repo.addExpense(form(), ExpenseInput(categoryId("BILL"), 20_000, LocalDate.of(2026, 10, 1)))).expense
        assertEquals(LocalDate.of(2026, 10, 1), DhakaTime.localDate(e.expenseDate))
        assertEquals(nov12, e.createdAt)
    }

    @Test
    fun aRefusedExpenseSavesNothingAndTheSameFormCanBeFixed() = runBlocking {
        val repo = seeded()
        val formId = form()
        assertEquals(
            ExpenseSaveResult.Invalid(setOf(ExpenseError.AMOUNT_MISSING, ExpenseError.DATE_IN_FUTURE)),
            repo.addExpense(formId, ExpenseInput(categoryId("RENT"), null, today.plusDays(1)))
        )
        assertEquals(
            ExpenseSaveResult.Invalid(setOf(ExpenseError.AMOUNT_TOO_LARGE)),
            repo.addExpense(formId, ExpenseInput(categoryId("RENT"), MAX_EXPENSE_AMOUNT + 1, today))
        )
        assertEquals(
            ExpenseSaveResult.Invalid(setOf(ExpenseError.CATEGORY_MISSING)),
            repo.addExpense(formId, ExpenseInput(999, 100, today))
        )
        assertEquals(0, countRows("expenses"))
        saved(repo.addExpense(formId, ExpenseInput(categoryId("RENT"), 100, today)))
        assertEquals(1, countRows("expenses"))
    }

    @Test
    fun aDoubleTapSavesOnce() = runBlocking {
        val repo = seeded()
        val formId = form()
        val rent = categoryId("RENT")
        val results = (1..5).map {
            async(Dispatchers.IO) { repo.addExpense(formId, ExpenseInput(rent, 10_000, today)) }
        }.awaitAll()
        assertEquals(1, countRows("expenses"))
        assertEquals(1, results.map { (it as ExpenseSaveResult.Saved).expense.id }.distinct().size)
    }

    @Test
    fun aCrashAfterTheWriteSavesNothing() = runBlocking {
        seeded()
        val rent = categoryId("RENT")
        try {
            repo { throw IllegalStateException("simulated crash") }.addExpense(form(), ExpenseInput(rent, 10_000, today))
            fail("Expected the simulated crash")
        } catch (expected: IllegalStateException) {
            // good
        }
        assertEquals(0, countRows("expenses"))
    }

    @Test
    fun aHiddenCategoryCannotTakeANewExpenseButKeepsItsOldOnes() = runBlocking {
        val repo = seeded()
        val tea = categoryId("TEA_SNACKS")
        val old = saved(repo.addExpense(form(), ExpenseInput(tea, 3_000, today))).expense
        repo.setCategoryVisible(tea, visible = false)

        assertEquals(
            ExpenseSaveResult.Invalid(setOf(ExpenseError.CATEGORY_HIDDEN)),
            repo.addExpense(form(), ExpenseInput(tea, 1_000, today))
        )
        // The old expense still shows its (hidden) category, and can be edited while keeping it.
        assertEquals("চা-নাস্তা", rows().single().categoryName)
        saved(repo.editExpense(form(), old.id, ExpenseInput(tea, 4_000, today)))
        // But no other expense can be moved INTO the hidden category.
        val rent = saved(repo.addExpense(form(), ExpenseInput(categoryId("RENT"), 100, today))).expense
        assertEquals(
            ExpenseSaveResult.Invalid(setOf(ExpenseError.CATEGORY_HIDDEN)),
            repo.editExpense(form(), rent.id, ExpenseInput(tea, 100, today))
        )
    }

    @Test
    fun anEditChangesTheValuesKeepsCreatedAtAndSetsUpdatedAt() = runBlocking {
        val repo = seeded()
        val e = saved(repo.addExpense(form(), ExpenseInput(categoryId("BILL"), 10_000, today, "বিদ্যুৎ"))).expense
        clockNow += 60_000

        val edited = saved(repo.editExpense(form(), e.id, ExpenseInput(categoryId("OTHER"), 12_000, today, "পানি"))).expense

        assertEquals(e.id, edited.id)
        assertEquals(categoryId("OTHER"), edited.expenseCategoryId)
        assertEquals(12_000L, edited.amount)
        assertEquals("পানি", edited.reason)
        assertEquals(e.expenseDate, edited.expenseDate) // the day was not changed
        assertEquals(nov12, edited.createdAt)
        assertEquals(clockNow, edited.updatedAt)
        assertEquals(edited, db.expenseDao().getExpense(e.id))
        assertEquals(1, countRows("expenses"))
    }

    @Test
    fun aDeletedOrUnknownExpenseCannotBeEdited() = runBlocking {
        val repo = seeded()
        val e = saved(repo.addExpense(form(), ExpenseInput(categoryId("BILL"), 10_000, today))).expense
        repo.deleteExpense(e.id)
        assertEquals(ExpenseSaveResult.NotFound, repo.editExpense(form(), e.id, ExpenseInput(categoryId("BILL"), 1, today)))
        assertEquals(ExpenseSaveResult.NotFound, repo.editExpense(form(), 999, ExpenseInput(categoryId("BILL"), 1, today)))
    }

    @Test
    fun deleteHidesTheExpenseEverywhereAndUndoBringsItBack() = runBlocking {
        val repo = seeded()
        val e = saved(repo.addExpense(form(), ExpenseInput(categoryId("RENT"), 500_000, today))).expense
        saved(repo.addExpense(form(), ExpenseInput(categoryId("BILL"), 20_000, today)))
        clockNow += 1_000

        assertEquals(ExpenseDeleteResult.DELETED, repo.deleteExpense(e.id))
        assertEquals(ExpenseDeleteResult.ALREADY_DELETED, repo.deleteExpense(e.id))
        assertEquals(ExpenseDeleteResult.NOT_FOUND, repo.deleteExpense(999))

        // The row is still there (soft delete), but no list or total counts it.
        assertEquals(2, countRows("expenses"))
        assertEquals(1, rows().size)
        assertEquals(20_000L, db.expenseDao().observeTotalInRange(all.startMillis, all.endMillisExclusive).first())
        assertEquals(20_000L, repo.observeShopTotalInRange(all).first())
        assertEquals(1, db.expenseDao().observeInRange(all.startMillis, all.endMillisExclusive).first().size)
        assertTrue(db.expenseDao().getExpense(e.id)!!.isDeleted)
        assertEquals(clockNow, db.expenseDao().getExpense(e.id)!!.updatedAt)

        assertEquals(ExpenseRestoreResult.RESTORED, repo.restoreExpense(e.id))
        assertEquals(ExpenseRestoreResult.NOT_DELETED, repo.restoreExpense(e.id))
        assertEquals(ExpenseRestoreResult.NOT_FOUND, repo.restoreExpense(999))
        assertEquals(2, rows().size)
        assertEquals(520_000L, repo.observeShopTotalInRange(all).first())
    }

    @Test
    fun personalExpensesCountInTheTotalButNotAsShopCosts() = runBlocking {
        val repo = seeded()
        saved(repo.addExpense(form(), ExpenseInput(categoryId("RENT"), 500_000, today)))
        saved(repo.addExpense(form(), ExpenseInput(categoryId("FAMILY"), 20_000, today)))
        val deletedShop = saved(repo.addExpense(form(), ExpenseInput(categoryId("BILL"), 7_000, today))).expense
        repo.deleteExpense(deletedShop.id)

        assertEquals(520_000L, db.expenseDao().observeTotalInRange(all.startMillis, all.endMillisExclusive).first())
        assertEquals(500_000L, repo.observeShopTotalInRange(all).first())
        assertTrue(rows().single { it.categoryName == "পরিবার" }.categoryIsPersonal)
    }

    @Test
    fun theWindowIncludesItsStartAndExcludesItsEndAndIsNewestFirst() = runBlocking {
        val repo = seeded()
        val rent = categoryId("RENT")
        val range = DhakaTime.today(nov12)
        val dao = db.expenseDao()
        fun at(t: Long) = com.example.dukanhisabnext.data.entity.Expense(expenseCategoryId = rent, amount = 1, expenseDate = t, createdAt = t, updatedAt = t)
        dao.insert(at(range.startMillis - 1))
        val first = dao.insert(at(range.startMillis))
        val last = dao.insert(at(range.endMillisExclusive - 1))
        dao.insert(at(range.endMillisExclusive))

        val ids = repo.observeRowsInRange(range).first().map { it.expense.id }
        assertEquals(listOf(last, first), ids)
    }

    // =====================================================================================
    // Categories
    // =====================================================================================

    @Test
    fun aNewCategoryNeedsAUniqueNameAndADoubleTapAddsOne() = runBlocking {
        val repo = seeded()
        assertEquals(
            CategorySaveResult.Invalid(setOf(CategoryError.NAME_DUPLICATE)),
            repo.addCategory(form(), "  ভাড়া ", isPersonal = false)
        )
        assertEquals(CategorySaveResult.Invalid(setOf(CategoryError.NAME_MISSING)), repo.addCategory(form(), "  ", false))

        val formId = form()
        val results = (1..4).map { async(Dispatchers.IO) { repo.addCategory(formId, "মেরামত", isPersonal = false) } }.awaitAll()
        assertEquals(1, results.map { (it as CategorySaveResult.Saved).category.id }.distinct().size)
        assertEquals(8, categories().size)
        val repair = categories().single { it.name == "মেরামত" }
        assertNull(repair.systemKey)
        assertTrue(repair.isActive)
    }

    @Test
    fun aRenameShowsOnOldExpensesAndKeepsTheSystemKey() = runBlocking {
        val repo = seeded()
        val bill = categoryId("BILL")
        saved(repo.addExpense(form(), ExpenseInput(bill, 1_000, today)))

        val renamed = repo.updateCategory(bill, "  বিদ্যুৎ   বিল ", isPersonal = false) as CategorySaveResult.Saved
        assertEquals("বিদ্যুৎ বিল", renamed.category.name)
        assertEquals("BILL", renamed.category.systemKey)
        assertEquals("বিদ্যুৎ বিল", rows().single().categoryName)

        assertEquals(
            CategorySaveResult.Invalid(setOf(CategoryError.NAME_DUPLICATE)),
            repo.updateCategory(bill, "ভাড়া", isPersonal = false)
        )
        assertEquals(CategorySaveResult.NotFound, repo.updateCategory(999, "কিছু", false))
    }

    @Test
    fun thePersonalFlagCanBeChangedAndMovesTheExpenseOutOfShopCosts() = runBlocking {
        val repo = seeded()
        val transport = categoryId("TRANSPORT")
        saved(repo.addExpense(form(), ExpenseInput(transport, 5_000, today)))
        assertEquals(5_000L, repo.observeShopTotalInRange(all).first())

        repo.updateCategory(transport, "যাতায়াত", isPersonal = true)

        assertEquals(0L, repo.observeShopTotalInRange(all).first())
        assertEquals(5_000L, db.expenseDao().observeTotalInRange(all.startMillis, all.endMillisExclusive).first())
    }

    @Test
    fun theLastShownCategoryCannotBeHiddenButAHiddenOneCanBeShownAgain() = runBlocking {
        val repo = seeded()
        val ids = categories().map { it.id }
        // Hide all but one.
        for (id in ids.drop(1)) assertTrue(repo.setCategoryVisible(id, visible = false) is CategorySaveResult.Saved)
        assertEquals(
            CategorySaveResult.Invalid(setOf(CategoryError.LAST_VISIBLE)),
            repo.setCategoryVisible(ids.first(), visible = false)
        )
        assertEquals(1, categories().count { it.isActive })

        assertTrue(repo.setCategoryVisible(ids.last(), visible = true) is CategorySaveResult.Saved)
        assertEquals(2, categories().count { it.isActive })
        assertEquals(CategorySaveResult.NotFound, repo.setCategoryVisible(999, visible = false))
        assertEquals(7, categories().size) // nothing was ever deleted
    }
}

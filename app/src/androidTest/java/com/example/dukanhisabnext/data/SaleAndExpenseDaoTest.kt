package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.Expense
import com.example.dukanhisabnext.data.entity.ExpenseCategory
import com.example.dukanhisabnext.data.entity.SaleItem
import com.example.dukanhisabnext.data.entity.ShopProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaleAndExpenseDaoTest : DatabaseTestBase() {

    // ---- Sales ----

    @Test
    fun saleAndItemsAreSavedAndReadBack() = runBlocking {
        val rice = addProduct("চাল")
        val saleId = db.saleDao().insertSale(sale("20261109-001"))
        db.saleDao().insertItems(
            listOf(
                SaleItem(
                    saleId = saleId, productId = rice, productNameSnapshot = "চাল",
                    quantity = 2_500, unitPrice = 4_000, lineTotal = 10_000
                )
            )
        )

        assertEquals("20261109-001", db.saleDao().getSale(saleId)?.receiptNo)
        assertEquals(2_500L, db.saleDao().getItems(saleId).single().quantity)
    }

    @Test
    fun duplicateReceiptNumberIsRejected() = runBlocking {
        db.saleDao().insertSale(sale("20261109-001"))
        assertConstraintFails { db.saleDao().insertSale(sale("20261109-001")) }
    }

    @Test
    fun saleForUnknownCustomerIsRejected() = runBlocking {
        assertConstraintFails { db.saleDao().insertSale(sale("20261109-002", customerId = 999)) }
    }

    @Test
    fun salesInRangeIncludeStartAndExcludeEnd() = runBlocking {
        db.saleDao().insertSale(sale("A", createdAt = 1_000))
        db.saleDao().insertSale(sale("B", createdAt = 1_999))
        db.saleDao().insertSale(sale("C", createdAt = 2_000))

        val sales = db.saleDao().observeInRange(start = 1_000, end = 2_000).first()
        assertEquals(listOf("B", "A"), sales.map { it.receiptNo }) // newest first, C excluded
    }

    // ---- Expenses ----

    @Test
    fun expensesUseExpenseDateAndHideSoftDeleted() = runBlocking {
        val rent = db.expenseDao().insertCategory(ExpenseCategory(name = "ভাড়া"))

        fun expense(amount: Long, expenseDate: Long) = Expense(
            expenseCategoryId = rent, amount = amount, expenseDate = expenseDate,
            createdAt = now, updatedAt = now // all typed in "now", but for different dates
        )

        val inRange = db.expenseDao().insert(expense(50_000, expenseDate = 1_500))
        db.expenseDao().insert(expense(20_000, expenseDate = 1_600))
        db.expenseDao().insert(expense(99_000, expenseDate = 5_000)) // outside the range

        assertEquals(70_000L, db.expenseDao().observeTotalInRange(1_000, 2_000).first())

        db.expenseDao().softDelete(inRange, updatedAt = now)

        assertEquals(20_000L, db.expenseDao().observeTotalInRange(1_000, 2_000).first())
        assertEquals(1, db.expenseDao().observeInRange(1_000, 2_000).first().size)
    }

    // ---- Shop profile ----

    @Test
    fun shopProfileIsASingleRow() = runBlocking {
        assertNull(db.shopProfileDao().observe().first())

        db.shopProfileDao().save(ShopProfile(name = "করিম স্টোর"))
        db.shopProfileDao().save(ShopProfile(name = "করিম জেনারেল স্টোর")) // replaces, not adds

        assertEquals("করিম জেনারেল স্টোর", db.shopProfileDao().observe().first()?.name)
    }
}

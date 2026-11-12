package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.Category
import com.example.dukanhisabnext.data.entity.ExpenseCategory
import com.example.dukanhisabnext.util.NAME_ORDER
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M9: names are listed alphabetically whatever their capital letters ("ORDER BY name COLLATE NOCASE,
 * name"). The real SQL must give the same order as the Kotlin twin NAME_ORDER, for products,
 * customers, product categories and expense categories. Stored names are never changed.
 */
@RunWith(AndroidJUnit4::class)
class NameOrderDaoTest : DatabaseTestBase() {

    private val names = listOf("sonet", "Kiulop", "Acmix", "apple", "Zebra", "banana", "রহিম", "করিম", "Rahim", "rahim", "elias", "Josim Mazi")
    private val expected = names.sortedWith(NAME_ORDER)

    @Test
    fun productsAreListedCaseInsensitivelyAndKeepTheirNames() = runBlocking {
        names.forEach { addProduct(it) }
        val listed = db.productDao().observeProducts(isActive = true, query = "", categoryId = null).first().map { it.product.name }
        assertEquals(expected, listed)
        assertEquals(names.sorted(), listed.sorted()) // the same names, none rewritten
    }

    @Test
    fun customersAreListedCaseInsensitively() = runBlocking {
        names.forEach { addCustomer(it) }
        assertEquals(expected, db.customerDao().observeSearchedByName("").first().map { it.name })
        assertEquals(expected, db.customerDao().observeWithBalance("").first().map { it.customer.name })
    }

    @Test
    fun productCategoriesAreListedCaseInsensitively() = runBlocking {
        names.forEach { db.productDao().insertCategory(Category(name = it)) }
        assertEquals(expected, db.productDao().observeCategories().first().map { it.name })
    }

    @Test
    fun expenseCategoriesAreListedCaseInsensitively() = runBlocking {
        names.forEach { db.expenseDao().insertCategory(ExpenseCategory(name = it)) }
        assertEquals(expected, db.expenseDao().getCategories().map { it.name })
    }

    @Test
    fun theSearchStillWorksWithTheNewOrder() = runBlocking {
        names.forEach { addProduct(it) }
        val found = db.productDao().observeProducts(true, "RAH", null).first().map { it.product.name }
        assertEquals(listOf("Rahim", "rahim"), found) // matches ignore case; the order is the name order
    }
}

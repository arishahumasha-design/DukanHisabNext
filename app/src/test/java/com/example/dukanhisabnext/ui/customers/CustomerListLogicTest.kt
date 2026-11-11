package com.example.dukanhisabnext.ui.customers

import com.example.dukanhisabnext.data.dao.CustomerWithBalance
import com.example.dukanhisabnext.data.entity.Customer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerListLogicTest {

    private fun item(id: Long, name: String, balance: Long) =
        CustomerWithBalance(Customer(id = id, name = name, createdAt = 0, updatedAt = 0), balance)

    // Already in the database order: the biggest due first, then clear, then advances.
    private val all = listOf(
        item(1, "করিম", 50_000),
        item(2, "রহিম", 10_000),
        item(3, "সালমা", 0),
        item(4, "জামাল", -7_000)
    )

    @Test
    fun withTheChipOffEveryoneIsShownInTheSameOrder() {
        assertEquals(listOf(1L, 2L, 3L, 4L), visibleCustomers(all, onlyOwing = false).map { it.customer.id })
    }

    @Test
    fun withTheChipOnOnlyCustomersWhoOweAreShownBiggestFirst() {
        assertEquals(listOf(1L, 2L), visibleCustomers(all, onlyOwing = true).map { it.customer.id })
    }

    @Test
    fun anAdvanceOrAClearBalanceIsNotOwing() {
        val result = visibleCustomers(listOf(item(3, "সালমা", 0), item(4, "জামাল", -1)), onlyOwing = true)
        assertTrue(result.isEmpty())
    }

    @Test
    fun anAmountOfOnePaisaCounts() {
        assertEquals(1, visibleCustomers(listOf(item(5, "নাসিম", 1)), onlyOwing = true).size)
    }

    @Test
    fun theEmptyMessageDependsOnTheSearchThenTheChip() {
        assertEquals(CustomerEmptyKind.NO_SEARCH_RESULT, customerEmptyKind(searching = true, onlyOwing = false))
        assertEquals(CustomerEmptyKind.NO_SEARCH_RESULT, customerEmptyKind(searching = true, onlyOwing = true))
        assertEquals(CustomerEmptyKind.NOBODY_OWES, customerEmptyKind(searching = false, onlyOwing = true))
        assertEquals(CustomerEmptyKind.NO_CUSTOMERS, customerEmptyKind(searching = false, onlyOwing = false))
    }
}

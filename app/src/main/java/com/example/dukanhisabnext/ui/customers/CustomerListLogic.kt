package com.example.dukanhisabnext.ui.customers

import com.example.dukanhisabnext.data.dao.CustomerWithBalance

// The Khata list rules as plain Kotlin (no Android), so they can be unit-tested on the PC.

/**
 * With the "যাদের বাকি আছে" chip on, only customers who owe money are shown (balance above 0).
 * Customers with nothing owed or with an advance are hidden. The order is kept (the database
 * already sorts the biggest due first).
 */
fun visibleCustomers(all: List<CustomerWithBalance>, onlyOwing: Boolean): List<CustomerWithBalance> =
    if (onlyOwing) all.filter { it.balance > 0 } else all

/** Which message to show when the list is empty. */
enum class CustomerEmptyKind { NO_CUSTOMERS, NO_SEARCH_RESULT, NOBODY_OWES }

/**
 * [searching] is true while the search box has text. A search with no result wins over the chip
 * message, because the search is what the user just typed.
 */
fun customerEmptyKind(searching: Boolean, onlyOwing: Boolean): CustomerEmptyKind = when {
    searching -> CustomerEmptyKind.NO_SEARCH_RESULT
    onlyOwing -> CustomerEmptyKind.NOBODY_OWES
    else -> CustomerEmptyKind.NO_CUSTOMERS
}

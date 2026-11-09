package com.example.dukanhisabnext.data

import com.example.dukanhisabnext.data.dao.CustomerWithBalance
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.util.toLatinDigits
import kotlinx.coroutines.flow.Flow

/**
 * The one place that changes customers. Balances are NOT stored here: they are always
 * computed from the ledger (SPEC.md principle 5). There is no delete in the MVP.
 *
 * [clock] gives "now" in UTC millis. Tests pass a fixed clock.
 */
class CustomerRepository(
    db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val customerDao = db.customerDao()
    private val ledgerDao = db.ledgerDao()

    /**
     * The customer list with balances, searched by name or mobile.
     * Mobile numbers are stored with normal digits, so Bengali digits typed in the
     * search box are converted first.
     */
    fun observeCustomers(query: String): Flow<List<CustomerWithBalance>> =
        customerDao.observeWithBalance(toLatinDigits(query.trim()))

    /** Total receivable (pabo): what customers who owe money owe in total. */
    fun observeTotalReceivable(): Flow<Long> = ledgerDao.observeTotalReceivable()

    fun observeCustomer(id: Long): Flow<Customer?> = customerDao.observeById(id)

    fun observeBalance(customerId: Long): Flow<Long> = ledgerDao.observeBalance(customerId)

    suspend fun getCustomer(id: Long): Customer? = customerDao.getById(id)

    /** True if another customer already has this (normalized) mobile number. */
    suspend fun phoneExists(phone: String, excludeId: Long?): Boolean =
        customerDao.countWithPhone(phone, excludeId ?: -1L) > 0

    /** Saves a new customer. The id, createdAt and updatedAt inside [customer] are set here. */
    suspend fun addCustomer(customer: Customer): Long {
        val now = clock()
        return customerDao.insert(customer.copy(id = 0, createdAt = now, updatedAt = now))
    }

    suspend fun updateCustomer(customer: Customer) {
        customerDao.update(customer.copy(updatedAt = clock()))
    }
}

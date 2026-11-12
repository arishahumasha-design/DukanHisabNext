package com.example.dukanhisabnext.data

import androidx.room.withTransaction
import com.example.dukanhisabnext.data.dao.KhataCustomerRow
import com.example.dukanhisabnext.data.dao.KhataEntryRow
import com.example.dukanhisabnext.data.dao.LedgerHistoryRow
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.ledger.LedgerError
import com.example.dukanhisabnext.ledger.LedgerInput
import com.example.dukanhisabnext.ledger.buildLedgerEntry
import com.example.dukanhisabnext.ledger.checkLedgerInput
import com.example.dukanhisabnext.util.DhakaTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface LedgerSaveResult {
    data class Saved(val entry: LedgerEntry) : LedgerSaveResult
    /** Nothing was saved. [errors] says why (see LedgerError). */
    data class Invalid(val errors: Set<LedgerError>) : LedgerSaveResult
    data object CustomerNotFound : LedgerSaveResult
}

/**
 * The one place that writes the baki khata from the khata screens (টাকা পেলাম, বাকি দিলাম,
 * পুরনো বাকি, সংশোধন, অগ্রিম ফেরত দিলাম). Sales and cancelled sales write their own entries in
 * SaleRepository. Entries are only ever ADDED: there is no edit and no delete.
 *
 * Every save: one lock (one save at a time), a form id (a double tap returns the first result
 * and saves nothing new), and one transaction in which the rules are checked against the
 * balance as it is at that moment, and the entry is inserted.
 *
 * [clock] gives "now" in UTC millis; [afterInsert] is only for crash tests.
 */
class LedgerRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val afterInsert: () -> Unit = {}
) {
    private val ledgerDao = db.ledgerDao()
    private val customerDao = db.customerDao()

    private val saveLock = Mutex()

    /** Forms already saved, by form id, so a double tap returns the same entry. Small and in memory. */
    private val savedForms = object : LinkedHashMap<String, LedgerSaveResult.Saved>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LedgerSaveResult.Saved>?) =
            size > 100
    }

    /** The customer's history, newest first, with the receipt number and "sale cancelled" flag. */
    fun observeHistory(customerId: Long): Flow<List<LedgerHistoryRow>> = ledgerDao.observeHistory(customerId)

    fun observeBalance(customerId: Long): Flow<Long> = ledgerDao.observeBalance(customerId)

    /** The "সব" list of the Khata tab: every customer with balance and newest entry date (one query). */
    fun observeKhataCustomers(): Flow<List<KhataCustomerRow>> = customerDao.observeKhataCustomers()

    /** The ledger entries of all customers in a time window, newest first (one query). */
    fun observeEntriesInRange(range: DhakaTime.TimeRange): Flow<List<KhataEntryRow>> =
        ledgerDao.observeEntriesInRange(range.startMillis, range.endMillisExclusive)

    fun observeHasOpeningDue(customerId: Long): Flow<Boolean> =
        ledgerDao.observeOpeningDueCount(customerId).map { it > 0 }

    suspend fun getBalance(customerId: Long): Long = ledgerDao.getBalance(customerId)

    /**
     * Saves one entry from a khata form. [formId] is made when the form opens; saving the same
     * form twice returns the first result. A refused form can be fixed and saved again.
     */
    suspend fun save(formId: String, customerId: Long, input: LedgerInput): LedgerSaveResult = saveLock.withLock {
        savedForms[formId]?.let { return@withLock it }

        val result = db.withTransaction { saveInsideTransaction(customerId, input) }
        if (result is LedgerSaveResult.Saved) savedForms[formId] = result
        result
    }

    private suspend fun saveInsideTransaction(customerId: Long, input: LedgerInput): LedgerSaveResult {
        customerDao.getById(customerId) ?: return LedgerSaveResult.CustomerNotFound

        val now = clock()
        // Checked here, inside the transaction, so the balance and "has an opening due" are exact.
        val errors = checkLedgerInput(
            input = input,
            today = DhakaTime.localDate(now),
            currentBalance = ledgerDao.getBalance(customerId),
            hasOpeningDue = ledgerDao.countOpeningDue(customerId) > 0
        )
        if (errors.isNotEmpty()) return LedgerSaveResult.Invalid(errors)

        val entry = buildLedgerEntry(customerId, input, now)
        val id = ledgerDao.insert(entry)
        afterInsert()
        return LedgerSaveResult.Saved(entry.copy(id = id))
    }
}

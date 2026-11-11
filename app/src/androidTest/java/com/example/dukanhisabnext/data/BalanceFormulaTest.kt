package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.ledger.balanceOf
import com.example.dukanhisabnext.ledger.signedAmount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phone test: the rewritten balance SQL (BALANCE_SUM_SQL, with both lists written out) gives
 * exactly the same balance as the formula used before M5 and as signedAmount() in Kotlin.
 */
@RunWith(AndroidJUnit4::class)
class BalanceFormulaTest : DatabaseTestBase() {

    /** The balance SQL exactly as it was before M5 ("plus types listed, everything else is minus"). */
    private val oldBalanceSql = """
        COALESCE(SUM(CASE
            WHEN type IN ('OPENING_DUE', 'SALE_DUE', 'MANUAL_DUE', 'CORRECTION_PLUS') THEN amount
            ELSE -amount
        END), 0)
    """

    private val oldTypes = listOf(
        LedgerEntryType.OPENING_DUE, LedgerEntryType.SALE_DUE, LedgerEntryType.MANUAL_DUE,
        LedgerEntryType.PAYMENT_RECEIVED, LedgerEntryType.CORRECTION_PLUS, LedgerEntryType.CORRECTION_MINUS
    )

    private fun oldSqlBalance(customerId: Long): Long =
        db.openHelper.readableDatabase
            .query("SELECT $oldBalanceSql FROM ledger_entries WHERE customerId = ?", arrayOf<Any>(customerId))
            .use { it.moveToFirst(); it.getLong(0) }

    private suspend fun add(customerId: Long, type: LedgerEntryType, amount: Long): LedgerEntry {
        val entry = LedgerEntry(
            customerId = customerId, type = type, amount = amount,
            saleId = null, entryDate = now, createdAt = now
        )
        db.ledgerDao().insert(entry)
        return entry
    }

    @Test
    fun eachOldTypeGivesTheSameBalanceInTheNewSqlTheOldSqlAndKotlin() = runBlocking {
        for (type in oldTypes) {
            val customer = addCustomer("কাস্টমার ${type.name}")
            add(customer, type, 12_345)

            val newSql = db.ledgerDao().getBalance(customer)
            assertEquals("$type: new SQL vs old SQL", oldSqlBalance(customer), newSql)
            assertEquals("$type: new SQL vs Kotlin", signedAmount(type, 12_345), newSql)
        }
    }

    @Test
    fun theNewTypeAdvanceReturnedAddsInTheNewSqlAndInKotlin() = runBlocking {
        val customer = addCustomer("রহিম")
        add(customer, LedgerEntryType.ADVANCE_RETURNED, 12_345)

        val newSql = db.ledgerDao().getBalance(customer)
        assertEquals(12_345L, newSql)
        assertEquals(signedAmount(LedgerEntryType.ADVANCE_RETURNED, 12_345), newSql)
        // The OLD formula would have counted it the wrong way ("everything else is minus").
        // This is why BALANCE_SUM_SQL was rewritten with both lists written out.
        assertEquals(-12_345L, oldSqlBalance(customer))
    }

    @Test
    fun aMixedHistoryOfTheOldTypesGivesTheSameBalanceEverywhere() = runBlocking {
        val customer = addCustomer("করিম")
        val entries = listOf(
            add(customer, LedgerEntryType.OPENING_DUE, 50_000),
            add(customer, LedgerEntryType.SALE_DUE, 30_000),
            add(customer, LedgerEntryType.MANUAL_DUE, 7_000),
            add(customer, LedgerEntryType.PAYMENT_RECEIVED, 40_000),
            add(customer, LedgerEntryType.CORRECTION_PLUS, 1_500),
            add(customer, LedgerEntryType.CORRECTION_MINUS, 2_500)
        )
        val expected = 50_000L + 30_000 + 7_000 - 40_000 + 1_500 - 2_500

        assertEquals(expected, db.ledgerDao().getBalance(customer))
        assertEquals(expected, oldSqlBalance(customer))
        assertEquals(expected, balanceOf(entries))
    }

    @Test
    fun aMixedHistoryWithEveryTypeMatchesKotlin() = runBlocking {
        val customer = addCustomer("সালমা")
        val entries = LedgerEntryType.entries.mapIndexed { i, type -> add(customer, type, 1_000L * (i + 1)) }
        assertEquals(balanceOf(entries), db.ledgerDao().getBalance(customer))
    }

    @Test
    fun everyBalanceQueryUsesTheSameFormula() = runBlocking {
        // The live balance, the Khata list and the total receivable all use BALANCE_SUM_SQL.
        val owes = addCustomer("করিম")
        add(owes, LedgerEntryType.SALE_DUE, 30_000)
        add(owes, LedgerEntryType.ADVANCE_RETURNED, 5_000)
        add(owes, LedgerEntryType.PAYMENT_RECEIVED, 10_000)
        val ahead = addCustomer("রহিম")
        add(ahead, LedgerEntryType.PAYMENT_RECEIVED, 20_000)

        assertEquals(25_000L, db.ledgerDao().getBalance(owes))
        assertEquals(25_000L, db.ledgerDao().observeBalance(owes).first())
        val list = db.customerDao().observeWithBalance("").first().associate { it.customer.id to it.balance }
        assertEquals(25_000L, list[owes])
        assertEquals(-20_000L, list[ahead])
        assertEquals(25_000L, db.ledgerDao().observeTotalReceivable().first()) // the advance is ignored
    }
}

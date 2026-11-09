package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.LedgerEntryType.CORRECTION_MINUS
import com.example.dukanhisabnext.data.entity.LedgerEntryType.CORRECTION_PLUS
import com.example.dukanhisabnext.data.entity.LedgerEntryType.MANUAL_DUE
import com.example.dukanhisabnext.data.entity.LedgerEntryType.OPENING_DUE
import com.example.dukanhisabnext.data.entity.LedgerEntryType.PAYMENT_RECEIVED
import com.example.dukanhisabnext.data.entity.LedgerEntryType.SALE_DUE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerDaoTest : DatabaseTestBase() {

    private suspend fun add(customerId: Long, type: LedgerEntryType, amount: Long, entryDate: Long = now) =
        db.ledgerDao().insert(
            LedgerEntry(customerId = customerId, type = type, amount = amount, entryDate = entryDate, createdAt = now)
        )

    @Test
    fun balanceFollowsTheSpecFormula() = runBlocking {
        val karim = addCustomer("করিম")
        add(karim, OPENING_DUE, 50_000)      // +500
        add(karim, SALE_DUE, 30_000)         // +300
        add(karim, MANUAL_DUE, 10_000)       // +100
        add(karim, CORRECTION_PLUS, 1_000)   // +10
        add(karim, PAYMENT_RECEIVED, 20_000) // -200
        add(karim, CORRECTION_MINUS, 5_000)  // -50

        assertEquals(66_000L, db.ledgerDao().getBalance(karim)) // 660 taka
    }

    @Test
    fun customerWithNoEntriesOwesZero() = runBlocking {
        val rahim = addCustomer("রহিম")
        assertEquals(0L, db.ledgerDao().getBalance(rahim))

        val list = db.customerDao().observeWithBalance("").first()
        assertEquals(0L, list.single().balance)
    }

    @Test
    fun totalReceivableIgnoresCustomersWithAdvance() = runBlocking {
        val karim = addCustomer("করিম")
        val rahim = addCustomer("রহিম")
        add(karim, SALE_DUE, 40_000)          // karim owes 400
        add(rahim, MANUAL_DUE, 10_000)
        add(rahim, PAYMENT_RECEIVED, 15_000)  // rahim paid 50 extra (advance)

        assertEquals(-5_000L, db.ledgerDao().getBalance(rahim))
        assertEquals(40_000L, db.ledgerDao().observeTotalReceivable().first())
    }

    @Test
    fun customerListShowsBalancesAndSearchesNameAndPhone() = runBlocking {
        val karim = addCustomer("করিম", phone = "01711000000")
        addCustomer("রহিম", phone = "01811000000")
        add(karim, SALE_DUE, 25_000)

        val all = db.customerDao().observeWithBalance("").first()
        assertEquals(2, all.size)
        assertEquals(25_000L, all.first { it.customer.id == karim }.balance)

        assertEquals("রহিম", db.customerDao().observeWithBalance("রহি").first().single().customer.name)
        assertEquals("করিম", db.customerDao().observeWithBalance("0171").first().single().customer.name)
    }

    @Test
    fun historyIsOrderedByEntryDateNewestFirst() = runBlocking {
        val karim = addCustomer("করিম")
        val oneDay = 24 * 60 * 60 * 1000L
        add(karim, SALE_DUE, 1_000, entryDate = now)
        add(karim, OPENING_DUE, 2_000, entryDate = now - 30 * oneDay) // typed in later, but older date

        val history = db.ledgerDao().observeForCustomer(karim).first()
        assertEquals(SALE_DUE, history[0].type)
        assertEquals(OPENING_DUE, history[1].type)
    }

    @Test
    fun countsOpeningDue() = runBlocking {
        val karim = addCustomer("করিম")
        assertEquals(0, db.ledgerDao().countOpeningDue(karim))
        add(karim, OPENING_DUE, 1_000)
        assertEquals(1, db.ledgerDao().countOpeningDue(karim))
    }

    @Test
    fun entryForUnknownCustomerIsRejected() = runBlocking {
        assertConstraintFails { add(customerId = 999, type = SALE_DUE, amount = 1_000) }
    }
}

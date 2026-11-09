package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the repository that M3's screens use. The ledger is where every balance comes from. */
@RunWith(AndroidJUnit4::class)
class CustomerRepositoryTest : DatabaseTestBase() {

    // The repository gets a fixed clock so the test never depends on the real time.
    private fun repo() = CustomerRepository(db) { now }

    private fun newCustomer(name: String, phone: String? = null) =
        Customer(name = name, phone = phone, createdAt = 0, updatedAt = 0)

    private suspend fun ledger(customerId: Long, type: LedgerEntryType, amount: Long) =
        db.ledgerDao().insert(
            LedgerEntry(customerId = customerId, type = type, amount = amount, entryDate = now, createdAt = now)
        )

    // ---- Add and update ----

    @Test
    fun addCustomerSetsTheTimes() = runBlocking {
        val id = repo().addCustomer(newCustomer("করিম", "01711000000").copy(address = "বাজার", note = "পুরনো"))

        val saved = repo().getCustomer(id)!!
        assertEquals("করিম", saved.name)
        assertEquals("01711000000", saved.phone)
        assertEquals("বাজার", saved.address)
        assertEquals(now, saved.createdAt)
        assertEquals(now, saved.updatedAt)
    }

    @Test
    fun updateChangesFieldsAndUpdatedAtButNotCreatedAt() = runBlocking {
        val early = CustomerRepository(db) { 1_000 }
        val id = early.addCustomer(newCustomer("করিম"))

        val later = CustomerRepository(db) { 5_000 }
        later.updateCustomer(later.getCustomer(id)!!.copy(name = "করিম মিয়া", phone = "01811000000"))

        val saved = later.getCustomer(id)!!
        assertEquals("করিম মিয়া", saved.name)
        assertEquals("01811000000", saved.phone)
        assertEquals(1_000L, saved.createdAt)
        assertEquals(5_000L, saved.updatedAt)
    }

    @Test
    fun observeCustomerFollowsAnEdit() = runBlocking {
        val r = repo()
        val id = r.addCustomer(newCustomer("করিম"))
        assertEquals("করিম", r.observeCustomer(id).first()?.name)

        r.updateCustomer(r.getCustomer(id)!!.copy(name = "রহিম"))
        assertEquals("রহিম", r.observeCustomer(id).first()?.name)
    }

    @Test
    fun unknownCustomerIsNull() = runBlocking {
        assertNull(repo().getCustomer(999))
        assertNull(repo().observeCustomer(999).first())
    }

    // ---- Balances (computed from the ledger) ----

    @Test
    fun balanceIsZeroWhenTheLedgerIsEmpty() = runBlocking {
        val r = repo()
        val id = r.addCustomer(newCustomer("করিম"))

        assertEquals(0L, r.observeBalance(id).first())
        assertEquals(0L, r.observeCustomers("").first().single().balance)
        assertEquals(0L, r.observeTotalReceivable().first())
    }

    @Test
    fun balanceFollowsLedgerEntriesLive() = runBlocking {
        val r = repo()
        val id = r.addCustomer(newCustomer("করিম"))

        ledger(id, LedgerEntryType.SALE_DUE, 30_000)
        assertEquals(30_000L, r.observeBalance(id).first())

        ledger(id, LedgerEntryType.PAYMENT_RECEIVED, 12_000)
        assertEquals(18_000L, r.observeBalance(id).first())
        assertEquals(18_000L, r.observeCustomers("").first().single().balance)
        assertEquals(18_000L, r.observeTotalReceivable().first())
    }

    // ---- The list: order and search ----

    @Test
    fun listIsBiggestDueFirstThenByName() = runBlocking {
        val r = repo()
        val anis = r.addCustomer(newCustomer("আনিস"))     // owes nothing
        val babul = r.addCustomer(newCustomer("বাবুল"))   // owes 100
        val karim = r.addCustomer(newCustomer("করিম"))    // owes 500
        val rahim = r.addCustomer(newCustomer("রহিম"))    // owes 100 as well
        val selim = r.addCustomer(newCustomer("সেলিম"))   // paid 50 extra (ahead)

        ledger(babul, LedgerEntryType.MANUAL_DUE, 10_000)
        ledger(karim, LedgerEntryType.MANUAL_DUE, 50_000)
        ledger(rahim, LedgerEntryType.MANUAL_DUE, 10_000)
        ledger(selim, LedgerEntryType.PAYMENT_RECEIVED, 5_000)

        val ids = r.observeCustomers("").first().map { it.customer.id }

        // 500 (করিম), then the two 100s by name (বাবুল before রহিম), then 0 (আনিস), then -50 (সেলিম)
        assertEquals(listOf(karim, babul, rahim, anis, selim), ids)
    }

    @Test
    fun searchByNameAndByMobile() = runBlocking {
        val r = repo()
        r.addCustomer(newCustomer("করিম", "01711000000"))
        r.addCustomer(newCustomer("রহিম", "01811000000"))

        assertEquals("রহিম", r.observeCustomers("রহি").first().single().customer.name)
        assertEquals("করিম", r.observeCustomers("0171").first().single().customer.name)
        assertEquals(2, r.observeCustomers("1100").first().size)
        assertTrue(r.observeCustomers("xyz").first().isEmpty())
    }

    @Test
    fun searchAcceptsBengaliDigitsAndSpaces() = runBlocking {
        val r = repo()
        r.addCustomer(newCustomer("করিম", "01711000000"))
        r.addCustomer(newCustomer("রহিম", "01811000000"))

        // Mobile numbers are stored with normal digits, but the box may get Bengali ones.
        assertEquals("করিম", r.observeCustomers("০১৭১").first().single().customer.name)
        assertEquals("রহিম", r.observeCustomers("  রহিম ").first().single().customer.name)
    }

    // ---- Duplicate mobile warning ----

    @Test
    fun phoneExistsIgnoresTheCustomerBeingEdited() = runBlocking {
        val r = repo()
        val karim = r.addCustomer(newCustomer("করিম", "01711000000"))

        assertTrue(r.phoneExists("01711000000", excludeId = null))      // a new customer: duplicate
        assertFalse(r.phoneExists("01711000000", excludeId = karim))    // editing karim himself: fine
        assertFalse(r.phoneExists("01911000000", excludeId = null))     // unused number
    }

    @Test
    fun twoCustomersMayShareAMobileOrAName() = runBlocking {
        // Only a warning on screen, never a database block.
        val r = repo()
        val a = r.addCustomer(newCustomer("করিম", "01711000000"))
        val b = r.addCustomer(newCustomer("করিম", "01711000000"))

        assertNotNull(r.getCustomer(a))
        assertNotNull(r.getCustomer(b))
        assertEquals(2, r.observeCustomers("").first().size)
    }
}

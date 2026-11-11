package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the read-only sales list query. These only need compiling for now; run them from Android
 * Studio when you are ready.
 */
@RunWith(AndroidJUnit4::class)
class SalesListDaoTest : DatabaseTestBase() {

    private val everything = 0L to Long.MAX_VALUE

    @Test
    fun newestSaleComesFirst() = runBlocking {
        db.saleDao().insertSale(sale("A", createdAt = 1_000))
        db.saleDao().insertSale(sale("C", createdAt = 3_000))
        db.saleDao().insertSale(sale("B", createdAt = 2_000))

        val rows = db.saleDao().observeList(everything.first, everything.second).first()

        assertEquals(listOf("C", "B", "A"), rows.map { it.receiptNo })
    }

    @Test
    fun saleAtTheSameMomentKeepsTheNewerIdFirst() = runBlocking {
        val first = db.saleDao().insertSale(sale("A", createdAt = 5_000))
        val second = db.saleDao().insertSale(sale("B", createdAt = 5_000))

        val rows = db.saleDao().observeList(everything.first, everything.second).first()

        assertEquals(listOf(second, first), rows.map { it.id })
    }

    @Test
    fun theCustomerNameAndMobileAreJoinedAndASaleWithoutACustomerStillShows() = runBlocking {
        val karim = addCustomer("করিম", phone = "01711000000")
        db.saleDao().insertSale(sale("WITH", customerId = karim, createdAt = 2_000))
        db.saleDao().insertSale(sale("WITHOUT", customerId = null, createdAt = 1_000))

        val rows = db.saleDao().observeList(everything.first, everything.second).first()

        assertEquals(2, rows.size)
        assertEquals("করিম", rows[0].customerName)
        assertEquals("01711000000", rows[0].customerPhone)
        assertNull(rows[1].customerName)
        assertNull(rows[1].customerPhone)
    }

    @Test
    fun totalAndDueAreCopiedFromTheSale() = runBlocking {
        db.saleDao().insertSale(sale("A", createdAt = 1_000).copy(total = 25_050, paidAmount = 5_050, dueAmount = 20_000))

        val row = db.saleDao().observeList(everything.first, everything.second).first().single()

        assertEquals(25_050L, row.total)
        assertEquals(20_000L, row.dueAmount)
    }

    @Test
    fun theWindowIncludesItsStartAndExcludesItsEnd() = runBlocking {
        db.saleDao().insertSale(sale("BEFORE", createdAt = 999))
        db.saleDao().insertSale(sale("START", createdAt = 1_000))
        db.saleDao().insertSale(sale("INSIDE", createdAt = 1_500))
        db.saleDao().insertSale(sale("END", createdAt = 2_000))

        val rows = db.saleDao().observeList(start = 1_000, end = 2_000).first()

        assertEquals(listOf("INSIDE", "START"), rows.map { it.receiptNo })
    }

    @Test
    fun countsAllSalesWhateverTheWindow() = runBlocking {
        assertEquals(0, db.saleDao().observeCount().first())

        db.saleDao().insertSale(sale("A", createdAt = 1_000))
        db.saleDao().insertSale(sale("B", createdAt = 9_000))

        assertEquals(2, db.saleDao().observeCount().first())
        assertTrue(db.saleDao().observeList(5_000, 6_000).first().isEmpty()) // none in this window
    }

    @Test
    fun theRepositoryPassesTheWindowThrough() = runBlocking {
        db.saleDao().insertSale(sale("A", createdAt = 1_000))
        db.saleDao().insertSale(sale("B", createdAt = 3_000))
        val repo = SaleRepository(db, { 0L })

        val rows = repo.observeSalesList(com.example.dukanhisabnext.util.DhakaTime.TimeRange(2_000, 4_000)).first()

        assertEquals(listOf("B"), rows.map { it.receiptNo })
        assertEquals(2, repo.observeSalesCount().first())
    }
}

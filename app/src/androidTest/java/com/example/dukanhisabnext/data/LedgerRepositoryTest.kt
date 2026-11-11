package com.example.dukanhisabnext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType
import com.example.dukanhisabnext.ledger.LedgerAction
import com.example.dukanhisabnext.ledger.LedgerError
import com.example.dukanhisabnext.ledger.LedgerInput
import com.example.dukanhisabnext.ledger.MAX_LEDGER_AMOUNT
import com.example.dukanhisabnext.util.DhakaTime
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phone tests for the khata writes (M5): every save is one transaction behind one lock, a double
 * tap saves once, the opening due is allowed once, and the history query carries the receipt
 * number and the "sale cancelled" flag. In-memory database only.
 */
@RunWith(AndroidJUnit4::class)
class LedgerRepositoryTest : DatabaseTestBase() {

    // 14:30 on 11 Nov 2026 in Dhaka.
    private val nov11 = Instant.parse("2026-11-11T08:30:00Z").toEpochMilli()
    private val today = LocalDate.of(2026, 11, 11)
    private var clockNow = nov11

    private fun repo(afterInsert: () -> Unit = {}) = LedgerRepository(db, { clockNow }, afterInsert)

    private var nextForm = 0
    private fun form() = "form-${nextForm++}"

    private fun input(
        action: LedgerAction,
        amount: Long? = 10_000,
        date: LocalDate = today,
        method: PaymentMethod? = if (action.needsMethod) PaymentMethod.CASH else null,
        note: String? = if (action.needsNote) "ভুল লেখা হয়েছিল" else null
    ) = LedgerInput(action, amount, date, method, note)

    private fun saved(result: LedgerSaveResult): LedgerSaveResult.Saved =
        result as? LedgerSaveResult.Saved ?: throw AssertionError("Expected Saved, got $result")

    private suspend fun balance(customerId: Long) = db.ledgerDao().getBalance(customerId)

    // =====================================================================================
    // Each action
    // =====================================================================================

    @Test
    fun aPaymentIsSavedWithEveryFieldAndLowersTheDue() = runBlocking {
        val karim = addCustomer("করিম")
        saved(repo().save(form(), karim, input(LedgerAction.MANUAL_DUE, amount = 30_000)))

        val result = saved(
            repo().save(form(), karim, input(LedgerAction.PAYMENT, amount = 12_000, method = PaymentMethod.BKASH, note = "  আংশিক  "))
        )

        val e = db.ledgerDao().observeForCustomer(karim).first().first { it.id == result.entry.id }
        assertEquals(result.entry, e)
        assertEquals(LedgerEntryType.PAYMENT_RECEIVED, e.type)
        assertEquals(12_000L, e.amount)
        assertEquals(PaymentMethod.BKASH, e.paymentMethod)
        assertEquals("আংশিক", e.note)
        assertNull(e.saleId)
        assertEquals(nov11, e.entryDate)
        assertEquals(nov11, e.createdAt)
        assertEquals(18_000L, balance(karim))
    }

    @Test
    fun payingMoreThanTheDueMakesAnAdvanceThatTheTotalIgnores() = runBlocking {
        val karim = addCustomer("করিম")
        saved(repo().save(form(), karim, input(LedgerAction.MANUAL_DUE, amount = 30_000)))
        saved(repo().save(form(), karim, input(LedgerAction.PAYMENT, amount = 50_000)))

        assertEquals(-20_000L, balance(karim))
        assertEquals(0L, db.ledgerDao().observeTotalReceivable().first())
    }

    @Test
    fun anAdvanceCanBeGivenBackUpToTheAdvance() = runBlocking {
        val karim = addCustomer("করিম")
        saved(repo().save(form(), karim, input(LedgerAction.PAYMENT, amount = 20_000))) // -৳200

        val tooMuch = repo().save(form(), karim, input(LedgerAction.ADVANCE_RETURN, amount = 20_001))
        assertEquals(LedgerSaveResult.Invalid(setOf(LedgerError.MORE_THAN_ADVANCE)), tooMuch)

        val back = saved(repo().save(form(), karim, input(LedgerAction.ADVANCE_RETURN, amount = 20_000)))
        assertEquals(LedgerEntryType.ADVANCE_RETURNED, back.entry.type)
        assertEquals(PaymentMethod.CASH, back.entry.paymentMethod)
        assertEquals(0L, balance(karim))

        // No advance left: refused, nothing saved.
        assertEquals(
            LedgerSaveResult.Invalid(setOf(LedgerError.NO_ADVANCE)),
            repo().save(form(), karim, input(LedgerAction.ADVANCE_RETURN, amount = 100))
        )
        assertEquals(2, countRows("ledger_entries"))
    }

    @Test
    fun aCorrectionNeedsANoteAndChangesTheBalanceBothWays() = runBlocking {
        val karim = addCustomer("করিম")
        assertEquals(
            LedgerSaveResult.Invalid(setOf(LedgerError.NOTE_REQUIRED)),
            repo().save(form(), karim, input(LedgerAction.CORRECTION_PLUS, note = "  "))
        )
        assertEquals(0, countRows("ledger_entries"))

        saved(repo().save(form(), karim, input(LedgerAction.CORRECTION_PLUS, amount = 5_000, note = "কম লেখা ছিল")))
        saved(repo().save(form(), karim, input(LedgerAction.CORRECTION_MINUS, amount = 2_000, note = "বেশি লেখা ছিল")))
        assertEquals(3_000L, balance(karim))
    }

    // =====================================================================================
    // Opening due: only once
    // =====================================================================================

    @Test
    fun theOpeningDueIsAllowedOnlyOnceEvenWithSalesAlready() = runBlocking {
        val karim = addCustomer("করিম")
        saved(repo().save(form(), karim, input(LedgerAction.MANUAL_DUE, amount = 1_000)))
        val repo = repo()
        assertFalse(repo.observeHasOpeningDue(karim).first())

        saved(repo.save(form(), karim, input(LedgerAction.OPENING_DUE, amount = 45_000, date = LocalDate.of(2026, 1, 1))))
        assertTrue(repo.observeHasOpeningDue(karim).first())

        assertEquals(
            LedgerSaveResult.Invalid(setOf(LedgerError.OPENING_DUE_EXISTS)),
            repo.save(form(), karim, input(LedgerAction.OPENING_DUE, amount = 1))
        )
        assertEquals(1, db.ledgerDao().countOpeningDue(karim))
        assertEquals(46_000L, balance(karim))
    }

    @Test
    fun twoScreensAddingAnOpeningDueAtOnceSaveOnlyOne() = runBlocking {
        val karim = addCustomer("করিম")
        // Separate repositories do not share the lock: the check inside the transaction protects.
        val results = (1..4).map {
            async(Dispatchers.IO) { repo().save(form(), karim, input(LedgerAction.OPENING_DUE, amount = 45_000)) }
        }.awaitAll()

        assertEquals(1, results.count { it is LedgerSaveResult.Saved })
        assertEquals(3, results.count { it == LedgerSaveResult.Invalid(setOf(LedgerError.OPENING_DUE_EXISTS)) })
        assertEquals(1, db.ledgerDao().countOpeningDue(karim))
        assertEquals(45_000L, balance(karim))
    }

    @Test
    fun anOpeningDueForAnotherCustomerIsNotBlocked() = runBlocking {
        val karim = addCustomer("করিম")
        val rahim = addCustomer("রহিম")
        saved(repo().save(form(), karim, input(LedgerAction.OPENING_DUE)))
        saved(repo().save(form(), rahim, input(LedgerAction.OPENING_DUE)))
        assertEquals(1, db.ledgerDao().countOpeningDue(rahim))
    }

    // =====================================================================================
    // Double tap and crashes
    // =====================================================================================

    @Test
    fun doubleTapSavesOnceAndReturnsTheSameEntry() = runBlocking {
        val karim = addCustomer("করিম")
        val repo = repo()
        val formId = form()

        val results = (1..5).map {
            async(Dispatchers.IO) { repo.save(formId, karim, input(LedgerAction.PAYMENT, amount = 10_000)) }
        }.awaitAll()

        assertEquals(1, countRows("ledger_entries"))
        assertTrue(results.all { it is LedgerSaveResult.Saved })
        assertEquals(1, results.map { (it as LedgerSaveResult.Saved).entry.id }.distinct().size)
        assertEquals(-10_000L, balance(karim))
    }

    @Test
    fun aLaterTapOfTheSameFormSavesNothingNew() = runBlocking {
        val karim = addCustomer("করিম")
        val repo = repo()
        val formId = form()
        val first = saved(repo.save(formId, karim, input(LedgerAction.MANUAL_DUE)))
        clockNow += 60_000
        assertEquals(first, repo.save(formId, karim, input(LedgerAction.MANUAL_DUE)))
        assertEquals(1, countRows("ledger_entries"))
    }

    @Test
    fun aRefusedFormCanBeFixedAndSaved() = runBlocking {
        val karim = addCustomer("করিম")
        val repo = repo()
        val formId = form()
        assertTrue(repo.save(formId, karim, input(LedgerAction.MANUAL_DUE, amount = null)) is LedgerSaveResult.Invalid)
        saved(repo.save(formId, karim, input(LedgerAction.MANUAL_DUE, amount = 5_000)))
        assertEquals(5_000L, balance(karim))
    }

    @Test
    fun aCrashAfterTheInsertSavesNothing() = runBlocking {
        val karim = addCustomer("করিম")
        val crashing = repo { throw IllegalStateException("simulated crash") }
        val formId = form()
        try {
            crashing.save(formId, karim, input(LedgerAction.PAYMENT))
            fail("Expected the simulated crash")
        } catch (expected: IllegalStateException) {
            // good
        }
        assertEquals(0, countRows("ledger_entries"))

        // The same form can then be saved normally (the failed try was not remembered).
        saved(repo().save(formId, karim, input(LedgerAction.PAYMENT)))
        assertEquals(1, countRows("ledger_entries"))
    }

    // =====================================================================================
    // Wrong input
    // =====================================================================================

    @Test
    fun anUnknownCustomerIsReported() = runBlocking {
        assertEquals(LedgerSaveResult.CustomerNotFound, repo().save(form(), 999, input(LedgerAction.PAYMENT)))
        assertEquals(0, countRows("ledger_entries"))
    }

    @Test
    fun tooLargeZeroAndFutureAreRefused(): Unit = runBlocking {
        val karim = addCustomer("করিম")
        assertEquals(
            LedgerSaveResult.Invalid(setOf(LedgerError.AMOUNT_TOO_LARGE)),
            repo().save(form(), karim, input(LedgerAction.MANUAL_DUE, amount = MAX_LEDGER_AMOUNT + 1))
        )
        assertEquals(
            LedgerSaveResult.Invalid(setOf(LedgerError.AMOUNT_MISSING)),
            repo().save(form(), karim, input(LedgerAction.MANUAL_DUE, amount = 0))
        )
        assertEquals(
            LedgerSaveResult.Invalid(setOf(LedgerError.DATE_IN_FUTURE)),
            repo().save(form(), karim, input(LedgerAction.MANUAL_DUE, date = today.plusDays(1)))
        )
        assertEquals(0, countRows("ledger_entries"))
        saved(repo().save(form(), karim, input(LedgerAction.MANUAL_DUE, amount = MAX_LEDGER_AMOUNT)))
    }

    @Test
    fun todayFollowsTheDhakaClock() = runBlocking {
        // 00:10 on 12 Nov in Dhaka (still 11 Nov in UTC): 12 Nov is "today", not the future.
        clockNow = Instant.parse("2026-11-11T18:10:00Z").toEpochMilli()
        val karim = addCustomer("করিম")
        val e = saved(repo().save(form(), karim, input(LedgerAction.MANUAL_DUE, date = LocalDate.of(2026, 11, 12)))).entry
        assertEquals(clockNow, e.entryDate)
    }

    // =====================================================================================
    // History
    // =====================================================================================

    @Test
    fun aBackDatedEntryKeepsItsDayAndTheHistoryIsNewestFirst() = runBlocking {
        val karim = addCustomer("করিম")
        val payment = saved(repo().save(form(), karim, input(LedgerAction.PAYMENT, amount = 5_000))).entry
        clockNow += 60_000
        val old = saved(repo().save(form(), karim, input(LedgerAction.OPENING_DUE, amount = 45_000, date = LocalDate.of(2026, 1, 15)))).entry

        assertEquals(LocalDate.of(2026, 1, 15), DhakaTime.localDate(old.entryDate))
        assertEquals(clockNow, old.createdAt) // the real save time is kept too
        val history = repo().observeHistory(karim).first()
        assertEquals(listOf(payment.id, old.id), history.map { it.entry.id }) // by entry date, not save time
    }

    @Test
    fun theHistoryKnowsTheReceiptAndWhetherTheSaleWasCancelled() = runBlocking {
        val karim = addCustomer("করিম")
        val rice = db.productDao().insertProduct(
            Product(name = "চাল", unit = ProductUnit.KG, sellPrice = 10_000, createdAt = now, updatedAt = now)
        )
        db.stockMovementDao().insert(StockMovement(productId = rice, type = StockMovementType.OPENING, quantity = 10_000, createdAt = now))
        val sales = SaleRepository(db, { clockNow })
        val sale = sales.completeSale(
            SaleDraft("d1", listOf(SaleDraftLine(rice, 2_000, 10_000)), 0, 0, 0, PaymentMethod.CASH, karim)
        ) as SaleResult.Saved

        var history = repo().observeHistory(karim).first()
        val saleDue = history.single()
        assertEquals(LedgerEntryType.SALE_DUE, saleDue.entry.type)
        assertEquals(sale.receiptNo, saleDue.receiptNo)
        assertFalse(saleDue.saleCancelled)

        clockNow += 60_000
        sales.cancelSale(sale.saleId, null)

        history = repo().observeHistory(karim).first()
        assertEquals(2, history.size)
        val reversal = history.first() // newest first
        assertEquals(LedgerEntryType.CORRECTION_MINUS, reversal.entry.type)
        assertEquals(sale.saleId, reversal.entry.saleId)
        assertEquals(sale.receiptNo, reversal.receiptNo)
        assertTrue(history.all { it.saleCancelled })
        assertEquals(0L, balance(karim))
    }

    @Test
    fun entriesWithoutASaleHaveNoReceipt() = runBlocking {
        val karim = addCustomer("করিম")
        saved(repo().save(form(), karim, input(LedgerAction.MANUAL_DUE)))
        val row = repo().observeHistory(karim).first().single()
        assertNull(row.receiptNo)
        assertFalse(row.saleCancelled)
    }

    @Test
    fun theHistoryOnlyShowsThisCustomer() = runBlocking {
        val karim = addCustomer("করিম")
        val rahim = addCustomer("রহিম")
        saved(repo().save(form(), karim, input(LedgerAction.MANUAL_DUE)))
        saved(repo().save(form(), rahim, input(LedgerAction.MANUAL_DUE)))
        assertEquals(1, repo().observeHistory(karim).first().size)
    }
}

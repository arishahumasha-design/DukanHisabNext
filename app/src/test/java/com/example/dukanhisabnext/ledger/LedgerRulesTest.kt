package com.example.dukanhisabnext.ledger

import com.example.dukanhisabnext.data.dao.LedgerHistoryRow
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.util.DhakaTime
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerRulesTest {

    // 14:30 on 11 Nov 2026 in Dhaka (= 08:30 UTC).
    private val now = Instant.parse("2026-11-11T08:30:00Z").toEpochMilli()
    private val today = LocalDate.of(2026, 11, 11)

    private fun input(
        action: LedgerAction,
        amount: Long? = 10_000,
        date: LocalDate = today,
        method: PaymentMethod? = if (action.needsMethod) PaymentMethod.CASH else null,
        note: String? = if (action.needsNote) "ভুল লেখা হয়েছিল" else null
    ) = LedgerInput(action, amount, date, method, note)

    private fun check(input: LedgerInput, balance: Long = 0, hasOpeningDue: Boolean = false) =
        checkLedgerInput(input, today, balance, hasOpeningDue)

    private fun entry(
        type: LedgerEntryType,
        amount: Long = 10_000,
        id: Long = 1,
        entryDate: Long = now,
        createdAt: Long = now,
        saleId: Long? = null,
        method: PaymentMethod? = null
    ) = LedgerEntry(id, 3, type, amount, method, saleId, null, entryDate, createdAt)

    // =====================================================================================
    // The balance formula
    // =====================================================================================

    @Test
    fun everyTypeHasTheRightSign() {
        val plus = setOf(
            LedgerEntryType.OPENING_DUE, LedgerEntryType.SALE_DUE, LedgerEntryType.MANUAL_DUE,
            LedgerEntryType.CORRECTION_PLUS, LedgerEntryType.ADVANCE_RETURNED
        )
        for (type in LedgerEntryType.entries) {
            val expected = if (type in plus) 500L else -500L
            assertEquals(type.name, expected, signedAmount(type, 500))
        }
    }

    @Test
    fun theOldSixTypesKeepTheirMeaning() {
        // The formula before M5, written out: OPENING/SALE/MANUAL/CORRECTION_PLUS add, the rest subtract.
        assertEquals(100L, signedAmount(LedgerEntryType.OPENING_DUE, 100))
        assertEquals(100L, signedAmount(LedgerEntryType.SALE_DUE, 100))
        assertEquals(100L, signedAmount(LedgerEntryType.MANUAL_DUE, 100))
        assertEquals(100L, signedAmount(LedgerEntryType.CORRECTION_PLUS, 100))
        assertEquals(-100L, signedAmount(LedgerEntryType.PAYMENT_RECEIVED, 100))
        assertEquals(-100L, signedAmount(LedgerEntryType.CORRECTION_MINUS, 100))
    }

    @Test
    fun givingAnAdvanceBackBringsTheBalanceBackToZero() {
        val history = listOf(
            entry(LedgerEntryType.SALE_DUE, 30_000),
            entry(LedgerEntryType.PAYMENT_RECEIVED, 50_000) // paid ৳200 more than the due
        )
        assertEquals(-20_000L, balanceOf(history))
        assertEquals(0L, balanceOf(history + entry(LedgerEntryType.ADVANCE_RETURNED, 20_000)))
    }

    @Test
    fun noEntriesMeansNothingOwed() {
        assertEquals(0L, balanceOf(emptyList()))
    }

    // =====================================================================================
    // Amount
    // =====================================================================================

    @Test
    fun anAmountIsRequiredAndMustBeAboveZero() {
        assertEquals(setOf(LedgerError.AMOUNT_MISSING), check(input(LedgerAction.MANUAL_DUE, amount = null)))
        assertEquals(setOf(LedgerError.AMOUNT_MISSING), check(input(LedgerAction.MANUAL_DUE, amount = 0)))
        assertEquals(setOf(LedgerError.AMOUNT_MISSING), check(input(LedgerAction.MANUAL_DUE, amount = -5)))
        assertTrue(check(input(LedgerAction.MANUAL_DUE, amount = 1)).isEmpty()) // 1 paisa is fine
    }

    @Test
    fun oneCroreIsTheMostForOneEntry() {
        assertEquals(1_000_000_000L, MAX_LEDGER_AMOUNT) // ৳1,00,00,000
        assertTrue(check(input(LedgerAction.PAYMENT, amount = MAX_LEDGER_AMOUNT)).isEmpty())
        assertEquals(setOf(LedgerError.AMOUNT_TOO_LARGE), check(input(LedgerAction.PAYMENT, amount = MAX_LEDGER_AMOUNT + 1)))
    }

    // =====================================================================================
    // Date
    // =====================================================================================

    @Test
    fun todayAndEarlierDaysAreAllowedButNotTheFuture() {
        assertTrue(check(input(LedgerAction.PAYMENT, date = today)).isEmpty())
        assertTrue(check(input(LedgerAction.OPENING_DUE, date = LocalDate.of(2019, 1, 1))).isEmpty())
        assertEquals(setOf(LedgerError.DATE_IN_FUTURE), check(input(LedgerAction.PAYMENT, date = today.plusDays(1))))
    }

    @Test
    fun todaySavesTheRealMoment() {
        assertEquals(now, entryDateFor(today, now))
    }

    @Test
    fun anEarlierDayKeepsTheDhakaTimeOfDay() {
        val saved = entryDateFor(LocalDate.of(2026, 10, 1), now)
        assertEquals(Instant.parse("2026-10-01T08:30:00Z").toEpochMilli(), saved) // 14:30 in Dhaka
        assertEquals(LocalDate.of(2026, 10, 1), DhakaTime.localDate(saved))
    }

    @Test
    fun justAfterMidnightInDhakaTheDayIsStillRight() {
        // 00:10 on 12 Nov in Dhaka is still 11 Nov in UTC.
        val lateNight = Instant.parse("2026-11-11T18:10:00Z").toEpochMilli()
        val saved = entryDateFor(LocalDate.of(2026, 11, 5), lateNight)
        assertEquals(LocalDate.of(2026, 11, 5), DhakaTime.localDate(saved))
        assertEquals(lateNight, entryDateFor(LocalDate.of(2026, 11, 12), lateNight)) // "today" in Dhaka
    }

    @Test
    fun aBackDatedEntryIsRecognised() {
        assertFalse(isBackDated(entry(LedgerEntryType.PAYMENT_RECEIVED)))
        val old = entry(LedgerEntryType.OPENING_DUE, entryDate = entryDateFor(LocalDate.of(2026, 1, 15), now))
        assertTrue(isBackDated(old))
    }

    // =====================================================================================
    // Method and note
    // =====================================================================================

    @Test
    fun aPaymentAndAnAdvanceReturnNeedAMethod() {
        assertEquals(setOf(LedgerError.METHOD_REQUIRED), check(input(LedgerAction.PAYMENT, method = null)))
        assertEquals(
            setOf(LedgerError.METHOD_REQUIRED),
            check(input(LedgerAction.ADVANCE_RETURN, method = null), balance = -20_000)
        )
        // The other actions do not ask for one.
        assertTrue(check(input(LedgerAction.MANUAL_DUE, method = null)).isEmpty())
    }

    @Test
    fun theMethodIsOnlySavedWhereMoneyChangesHands() {
        assertEquals(PaymentMethod.BKASH, buildLedgerEntry(3, input(LedgerAction.PAYMENT, method = PaymentMethod.BKASH), now).paymentMethod)
        assertNull(buildLedgerEntry(3, input(LedgerAction.MANUAL_DUE, method = PaymentMethod.BKASH), now).paymentMethod)
    }

    @Test
    fun theScreenOffersThreeMethods() {
        assertEquals(listOf(PaymentMethod.CASH, PaymentMethod.BKASH, PaymentMethod.NAGAD), LEDGER_METHODS)
    }

    @Test
    fun aCorrectionNeedsANote() {
        assertEquals(setOf(LedgerError.NOTE_REQUIRED), check(input(LedgerAction.CORRECTION_PLUS, note = null)))
        assertEquals(setOf(LedgerError.NOTE_REQUIRED), check(input(LedgerAction.CORRECTION_MINUS, note = "   ")))
        assertTrue(check(input(LedgerAction.CORRECTION_MINUS, note = "ভুল হিসাব")).isEmpty())
        // A note is optional elsewhere.
        assertTrue(check(input(LedgerAction.PAYMENT, note = null)).isEmpty())
    }

    @Test
    fun aNoteIsTrimmedAndAtMost200Characters() {
        assertEquals("নোট", buildLedgerEntry(3, input(LedgerAction.MANUAL_DUE, note = "  নোট  "), now).note)
        assertNull(buildLedgerEntry(3, input(LedgerAction.MANUAL_DUE, note = "  "), now).note)
        assertTrue(check(input(LedgerAction.MANUAL_DUE, note = "ক".repeat(200))).isEmpty())
        assertEquals(setOf(LedgerError.NOTE_TOO_LONG), check(input(LedgerAction.MANUAL_DUE, note = "ক".repeat(201))))
        assertTrue(check(input(LedgerAction.MANUAL_DUE, note = "😀".repeat(200))).isEmpty())
    }

    // =====================================================================================
    // Opening due, payment, advance return
    // =====================================================================================

    @Test
    fun theOpeningDueIsAllowedOnlyOnce() {
        assertTrue(check(input(LedgerAction.OPENING_DUE), hasOpeningDue = false).isEmpty())
        assertEquals(setOf(LedgerError.OPENING_DUE_EXISTS), check(input(LedgerAction.OPENING_DUE), hasOpeningDue = true))
        // Other entries do not care.
        assertTrue(check(input(LedgerAction.MANUAL_DUE), hasOpeningDue = true).isEmpty())
    }

    @Test
    fun theOpeningDueIsAllowedEvenWhenTheCustomerAlreadyHasSales() {
        assertTrue(check(input(LedgerAction.OPENING_DUE), balance = 45_000, hasOpeningDue = false).isEmpty())
    }

    @Test
    fun payingMoreThanTheDueIsAllowedAndBecomesAnAdvance() {
        assertTrue(check(input(LedgerAction.PAYMENT, amount = 50_000), balance = 30_000).isEmpty())
        assertEquals(-20_000L, balanceAfter(30_000, LedgerAction.PAYMENT, 50_000))
        assertEquals(20_000L, advanceCreated(30_000, LedgerAction.PAYMENT, 50_000))
    }

    @Test
    fun noAdvanceWarningWhenThePaymentFitsTheDue() {
        assertNull(advanceCreated(30_000, LedgerAction.PAYMENT, 30_000)) // exactly 0 after
        assertNull(advanceCreated(30_000, LedgerAction.PAYMENT, 10_000))
        // An entry that ADDS to the due never warns, even if the balance stays negative.
        assertNull(advanceCreated(-50_000, LedgerAction.MANUAL_DUE, 10_000))
    }

    @Test
    fun aPaymentWhenAlreadyInAdvanceWarnsWithTheWholeAdvance() {
        assertEquals(30_000L, advanceCreated(-20_000, LedgerAction.PAYMENT, 10_000))
        assertEquals(10_000L, advanceCreated(0, LedgerAction.CORRECTION_MINUS, 10_000))
    }

    @Test
    fun anAdvanceReturnNeedsAnAdvanceAndCannotBeMore() {
        assertTrue(check(input(LedgerAction.ADVANCE_RETURN, amount = 20_000), balance = -20_000).isEmpty())
        assertTrue(check(input(LedgerAction.ADVANCE_RETURN, amount = 5_000), balance = -20_000).isEmpty())
        assertEquals(setOf(LedgerError.MORE_THAN_ADVANCE), check(input(LedgerAction.ADVANCE_RETURN, amount = 20_001), balance = -20_000))
        assertEquals(setOf(LedgerError.NO_ADVANCE), check(input(LedgerAction.ADVANCE_RETURN), balance = 0))
        assertEquals(setOf(LedgerError.NO_ADVANCE), check(input(LedgerAction.ADVANCE_RETURN), balance = 5_000))
        assertEquals(0L, balanceAfter(-20_000, LedgerAction.ADVANCE_RETURN, 20_000))
    }

    @Test
    fun severalProblemsAreAllReported() {
        val errors = check(
            LedgerInput(LedgerAction.CORRECTION_MINUS, amount = null, date = today.plusDays(3), note = null)
        )
        assertEquals(setOf(LedgerError.AMOUNT_MISSING, LedgerError.DATE_IN_FUTURE, LedgerError.NOTE_REQUIRED), errors)
    }

    @Test
    fun theActionsOnOffer() {
        assertEquals(
            listOf(LedgerAction.PAYMENT, LedgerAction.MANUAL_DUE, LedgerAction.OPENING_DUE,
                LedgerAction.CORRECTION_PLUS, LedgerAction.CORRECTION_MINUS),
            availableActions(balance = 10_000, hasOpeningDue = false)
        )
        val inAdvance = availableActions(balance = -5_000, hasOpeningDue = true)
        assertFalse(LedgerAction.OPENING_DUE in inAdvance)
        assertTrue(LedgerAction.ADVANCE_RETURN in inAdvance)
    }

    @Test
    fun eachActionSavesItsOwnType() {
        assertEquals(LedgerEntryType.PAYMENT_RECEIVED, LedgerAction.PAYMENT.type)
        assertEquals(LedgerEntryType.MANUAL_DUE, LedgerAction.MANUAL_DUE.type)
        assertEquals(LedgerEntryType.OPENING_DUE, LedgerAction.OPENING_DUE.type)
        assertEquals(LedgerEntryType.CORRECTION_PLUS, LedgerAction.CORRECTION_PLUS.type)
        assertEquals(LedgerEntryType.CORRECTION_MINUS, LedgerAction.CORRECTION_MINUS.type)
        assertEquals(LedgerEntryType.ADVANCE_RETURNED, LedgerAction.ADVANCE_RETURN.type)
    }

    @Test
    fun theBuiltEntryIsPositiveUtcAndNotLinkedToASale() {
        val e = buildLedgerEntry(3, input(LedgerAction.PAYMENT, amount = 12_345), now)
        assertEquals(3L, e.customerId)
        assertEquals(12_345L, e.amount)
        assertNull(e.saleId)
        assertEquals(now, e.entryDate)
        assertEquals(now, e.createdAt)
    }

    // =====================================================================================
    // History
    // =====================================================================================

    private fun row(e: LedgerEntry, receiptNo: String? = null, cancelled: Boolean = false) =
        LedgerHistoryRow(e, receiptNo, cancelled)

    @Test
    fun eachEntryGetsItsLabelKind() {
        assertEquals(HistoryKind.OPENING_DUE, historyKind(entry(LedgerEntryType.OPENING_DUE)))
        assertEquals(HistoryKind.SALE_DUE, historyKind(entry(LedgerEntryType.SALE_DUE, saleId = 9)))
        assertEquals(HistoryKind.MANUAL_DUE, historyKind(entry(LedgerEntryType.MANUAL_DUE)))
        assertEquals(HistoryKind.PAYMENT_RECEIVED, historyKind(entry(LedgerEntryType.PAYMENT_RECEIVED)))
        assertEquals(HistoryKind.CORRECTION_PLUS, historyKind(entry(LedgerEntryType.CORRECTION_PLUS)))
        assertEquals(HistoryKind.CORRECTION_MINUS, historyKind(entry(LedgerEntryType.CORRECTION_MINUS)))
        assertEquals(HistoryKind.SALE_CANCEL_REVERSAL, historyKind(entry(LedgerEntryType.CORRECTION_MINUS, saleId = 9)))
        assertEquals(HistoryKind.ADVANCE_RETURNED, historyKind(entry(LedgerEntryType.ADVANCE_RETURNED)))
    }

    @Test
    fun saleRowsOpenTheReceiptAndOnlyCancelledSaleDuesGetTheTag() {
        val saleDue = row(entry(LedgerEntryType.SALE_DUE, saleId = 9), "261111-001", cancelled = true)
        val reversal = row(entry(LedgerEntryType.CORRECTION_MINUS, saleId = 9), "261111-001", cancelled = true)
        val payment = row(entry(LedgerEntryType.PAYMENT_RECEIVED))

        assertTrue(opensReceipt(saleDue))
        assertTrue(opensReceipt(reversal))
        assertFalse(opensReceipt(payment))

        assertTrue(showsCancelledTag(saleDue))
        assertFalse(showsCancelledTag(reversal)) // the reversal itself says "বিক্রি বাতিল"
        assertFalse(showsCancelledTag(row(entry(LedgerEntryType.SALE_DUE, saleId = 9), "261111-001", cancelled = false)))
    }

    @Test
    fun theMethodIsShownOnlyForMoneyThatChangedHands() {
        assertEquals(PaymentMethod.BKASH, shownMethod(entry(LedgerEntryType.PAYMENT_RECEIVED, method = PaymentMethod.BKASH)))
        assertEquals(PaymentMethod.CASH, shownMethod(entry(LedgerEntryType.ADVANCE_RETURNED, method = PaymentMethod.CASH)))
        assertNull(shownMethod(entry(LedgerEntryType.SALE_DUE, method = PaymentMethod.CASH)))
    }

    @Test
    fun historyIsGroupedByDhakaDayNewestFirst() {
        val day = 24L * 60 * 60 * 1000
        val a = entry(LedgerEntryType.SALE_DUE, id = 1, entryDate = now - day)            // 10 Nov
        val b = entry(LedgerEntryType.PAYMENT_RECEIVED, id = 2, entryDate = now)          // 11 Nov
        val c = entry(LedgerEntryType.MANUAL_DUE, id = 3, entryDate = now + 60_000)       // 11 Nov, later
        // 23:50 on 10 Nov in Dhaka is 17:50 UTC: still 10 Nov in Dhaka.
        val d = entry(LedgerEntryType.MANUAL_DUE, id = 4, entryDate = Instant.parse("2026-11-10T17:50:00Z").toEpochMilli())

        val groups = groupHistoryByDhakaDay(listOf(a, b, c, d).map { row(it) })

        assertEquals(listOf(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 10)), groups.map { it.date })
        assertEquals(listOf(3L, 2L), groups[0].rows.map { it.entry.id })
        assertEquals(listOf(4L, 1L), groups[1].rows.map { it.entry.id })
    }

    @Test
    fun aBackDatedEntryLandsOnItsOwnDayNotTheDayItWasWritten() {
        val old = entry(LedgerEntryType.OPENING_DUE, id = 9, entryDate = entryDateFor(LocalDate.of(2026, 1, 15), now), createdAt = now)
        val recent = entry(LedgerEntryType.PAYMENT_RECEIVED, id = 2)

        val groups = groupHistoryByDhakaDay(listOf(old, recent).map { row(it) })

        assertEquals(listOf(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 1, 15)), groups.map { it.date })
        assertEquals(9L, groups.last().rows.single().entry.id)
    }

    @Test
    fun sameEntryDateIsOrderedBySaveTimeThenId() {
        val first = entry(LedgerEntryType.MANUAL_DUE, id = 5, entryDate = now, createdAt = now)
        val writtenLater = entry(LedgerEntryType.MANUAL_DUE, id = 4, entryDate = now, createdAt = now + 1_000)
        val sameMomentHigherId = entry(LedgerEntryType.MANUAL_DUE, id = 6, entryDate = now, createdAt = now)

        val rows = groupHistoryByDhakaDay(listOf(first, writtenLater, sameMomentHigherId).map { row(it) }).single().rows

        assertEquals(listOf(4L, 6L, 5L), rows.map { it.entry.id })
    }
}

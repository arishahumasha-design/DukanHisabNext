package com.example.dukanhisabnext.ledger

import com.example.dukanhisabnext.data.dao.LedgerHistoryRow
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.PaymentMethod
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The logic of the khata screens: the entry form (state, preview, messages, date box) and the
 * history rows (labels, notes, amounts, tags), plus which buttons show. Plain Kotlin only; the
 * Bengali words are the same as in strings.xml.
 */
class LedgerScreenLogicTest {

    private val now = Instant.parse("2026-11-11T08:30:00Z").toEpochMilli() // 14:30 in Dhaka
    private val today = LocalDate.of(2026, 11, 11)

    private val labels = HistoryLabels(
        openingDue = "পুরনো বাকি (খাতা থেকে)",
        saleDue = "বিক্রির বাকি",
        manualDue = "বাকি দিলাম",
        payment = "টাকা পেলাম",
        correctionPlus = "সংশোধন (+)",
        correctionMinus = "সংশোধন (−)",
        saleCancel = "বিক্রি বাতিল",
        advanceReturned = "অগ্রিম ফেরত দিলাম",
        taka = "৳"
    )
    private val methodName = { m: PaymentMethod ->
        when (m) {
            PaymentMethod.CASH -> "নগদ"
            PaymentMethod.BKASH -> "বিকাশ"
            PaymentMethod.NAGAD -> "নগদ (মোবাইল)"
            PaymentMethod.BANK -> "ব্যাংক"
            PaymentMethod.OTHER -> "অন্যান্য"
        }
    }

    private fun entry(
        type: LedgerEntryType,
        amount: Long = 10_000,
        id: Long = 1,
        entryDate: Long = now,
        createdAt: Long = now,
        saleId: Long? = null,
        method: PaymentMethod? = null,
        note: String? = null
    ) = LedgerEntry(id, 3, type, amount, method, saleId, note, entryDate, createdAt)

    private fun row(e: LedgerEntry, receiptNo: String? = null, cancelled: Boolean = false) =
        LedgerHistoryRow(e, receiptNo, cancelled)

    private fun title(e: LedgerEntry, receiptNo: String? = null) = historyTitle(row(e, receiptNo), labels, methodName)

    // =====================================================================================
    // The form: opening, typing, sending
    // =====================================================================================

    @Test
    fun aNewFormStartsTodayWithNoAmountAndTheFirstMethodWhereOneApplies() {
        val payment = newFormState(LedgerAction.PAYMENT, today)
        assertEquals(today, payment.date)
        assertEquals("", payment.amountText)
        assertEquals(PaymentMethod.CASH, payment.method)
        assertEquals("", payment.note)

        assertNull(newFormState(LedgerAction.MANUAL_DUE, today).method)
        assertNull(newFormState(LedgerAction.CORRECTION_PLUS, today).method)
        assertEquals(PaymentMethod.CASH, newFormState(LedgerAction.ADVANCE_RETURN, today).method)
    }

    @Test
    fun theAmountIsReadAsTakaAndKeptInPaisa() {
        assertNull(parseAmountText(""))
        assertNull(parseAmountText("   "))
        assertEquals(25_000L, parseAmountText("250"))
        assertEquals(1_250L, parseAmountText("12.5"))
        assertEquals(12_345L, parseAmountText(" 123.45 "))
        assertEquals(0L, parseAmountText("0"))
    }

    @Test
    fun theFormBecomesALedgerInput() {
        val state = newFormState(LedgerAction.PAYMENT, today).copy(
            amountText = "120", method = PaymentMethod.BKASH, note = "  আংশিক  "
        )
        val input = inputOf(state)
        assertEquals(LedgerAction.PAYMENT, input.action)
        assertEquals(12_000L, input.amount)
        assertEquals(PaymentMethod.BKASH, input.method)
        assertEquals(today, input.date)
        // The repository trims the note; here it is passed as typed.
        assertEquals("  আংশিক  ", input.note)
    }

    @Test
    fun aMethodIsOnlySentWhereMoneyChangesHands() {
        val due = newFormState(LedgerAction.MANUAL_DUE, today).copy(amountText = "10", method = PaymentMethod.NAGAD)
        assertNull(inputOf(due).method)
    }

    @Test
    fun aNumberThatIsNotANumberGivesNoAmount() {
        val input = inputOf(newFormState(LedgerAction.MANUAL_DUE, today).copy(amountText = "1.2.3"))
        assertNull(input.amount)
    }

    @Test
    fun theNoteBoxTakesAtMost200Characters() {
        assertEquals("ভুল", limitNoteInput("ভুল"))
        assertEquals("ক".repeat(200), limitNoteInput("ক".repeat(200)))
        assertEquals("ক".repeat(200), limitNoteInput("ক".repeat(300)))
        assertEquals("😀".repeat(200), limitNoteInput("😀".repeat(250))) // an emoji is never cut in half
        assertEquals(3, noteLength("ভুল"))
        assertEquals(2, noteLength("😀😀"))
    }

    @Test
    fun onlyACorrectionHasAPlusOrMinusChoice() {
        assertTrue(isCorrection(LedgerAction.CORRECTION_PLUS))
        assertTrue(isCorrection(LedgerAction.CORRECTION_MINUS))
        assertFalse(isCorrection(LedgerAction.PAYMENT))
        assertEquals(LedgerAction.CORRECTION_MINUS, withCorrectionSign(LedgerAction.CORRECTION_PLUS, plus = false))
        assertEquals(LedgerAction.CORRECTION_PLUS, withCorrectionSign(LedgerAction.CORRECTION_MINUS, plus = true))
        assertEquals(LedgerAction.PAYMENT, withCorrectionSign(LedgerAction.PAYMENT, plus = false))
    }

    // =====================================================================================
    // The form: messages
    // =====================================================================================

    @Test
    fun everyProblemIsShownUnderTheRightBox() {
        assertEquals(FormField.AMOUNT, fieldOf(LedgerError.AMOUNT_MISSING))
        assertEquals(FormField.AMOUNT, fieldOf(LedgerError.AMOUNT_TOO_LARGE))
        assertEquals(FormField.AMOUNT, fieldOf(LedgerError.NO_ADVANCE))
        assertEquals(FormField.AMOUNT, fieldOf(LedgerError.MORE_THAN_ADVANCE))
        assertEquals(FormField.DATE, fieldOf(LedgerError.DATE_IN_FUTURE))
        assertEquals(FormField.METHOD, fieldOf(LedgerError.METHOD_REQUIRED))
        assertEquals(FormField.NOTE, fieldOf(LedgerError.NOTE_REQUIRED))
        assertEquals(FormField.NOTE, fieldOf(LedgerError.NOTE_TOO_LONG))
        assertEquals(FormField.GENERAL, fieldOf(LedgerError.OPENING_DUE_EXISTS))
        // No error is left without a place.
        assertEquals(LedgerError.entries.size, LedgerError.entries.count { fieldOf(it) in FormField.entries })
    }

    @Test
    fun aBoxShowsOnlyItsOwnProblems() {
        val errors = setOf(LedgerError.AMOUNT_MISSING, LedgerError.DATE_IN_FUTURE, LedgerError.NOTE_REQUIRED)
        assertEquals(listOf(LedgerError.AMOUNT_MISSING), errorsFor(FormField.AMOUNT, errors))
        assertEquals(listOf(LedgerError.DATE_IN_FUTURE), errorsFor(FormField.DATE, errors))
        assertEquals(listOf(LedgerError.NOTE_REQUIRED), errorsFor(FormField.NOTE, errors))
        assertTrue(errorsFor(FormField.METHOD, errors).isEmpty())
        assertTrue(errorsFor(FormField.GENERAL, errors).isEmpty())
    }

    @Test
    fun theTwoSaveProblemsAreNotAboutATypedValue() {
        assertEquals(2, SaveProblem.entries.size)
        assertTrue(SaveProblem.entries.containsAll(listOf(SaveProblem.CUSTOMER_NOT_FOUND, SaveProblem.UNEXPECTED)))
    }

    // =====================================================================================
    // The form: "এরপর ব্যালেন্স হবে" and the advance warning
    // =====================================================================================

    private fun form(action: LedgerAction, amount: String) =
        newFormState(action, today).copy(amountText = amount)

    @Test
    fun noPreviewUntilAValidAmountIsTyped() {
        assertNull(formPreview(30_000, form(LedgerAction.PAYMENT, "")))
        assertNull(formPreview(30_000, form(LedgerAction.PAYMENT, "0")))
        assertNull(formPreview(30_000, form(LedgerAction.PAYMENT, "1.2.3")))
        assertNull(formPreview(30_000, form(LedgerAction.PAYMENT, "10000001"))) // above ৳1 crore
        assertEquals(FormPreview(balanceAfter = 30_000 - 1_000_000_000L, advanceCreated = 1_000_000_000L - 30_000), formPreview(30_000, form(LedgerAction.PAYMENT, "10000000")))
    }

    @Test
    fun aPaymentThatFitsTheDueLowersTheBalanceWithoutAWarning() {
        assertEquals(FormPreview(balanceAfter = 20_000, advanceCreated = null), formPreview(30_000, form(LedgerAction.PAYMENT, "100")))
        assertEquals(FormPreview(balanceAfter = 0, advanceCreated = null), formPreview(30_000, form(LedgerAction.PAYMENT, "300")))
    }

    @Test
    fun aPaymentBiggerThanTheDueWarnsHowMuchAdvanceItMakes() {
        val preview = formPreview(30_000, form(LedgerAction.PAYMENT, "500"))!!
        assertEquals(-20_000L, preview.balanceAfter)
        assertEquals(20_000L, preview.advanceCreated)
    }

    @Test
    fun aDueGoingUpNeverWarns() {
        assertEquals(FormPreview(40_000, null), formPreview(30_000, form(LedgerAction.MANUAL_DUE, "100")))
        assertEquals(FormPreview(-40_000, null), formPreview(-50_000, form(LedgerAction.MANUAL_DUE, "100")))
        assertEquals(FormPreview(35_000, null), formPreview(30_000, form(LedgerAction.CORRECTION_PLUS, "50")))
    }

    @Test
    fun aMinusCorrectionCanMakeAnAdvanceToo() {
        val preview = formPreview(5_000, form(LedgerAction.CORRECTION_MINUS, "80"))!!
        assertEquals(-3_000L, preview.balanceAfter)
        assertEquals(3_000L, preview.advanceCreated)
    }

    @Test
    fun givingTheAdvanceBackBringsTheBalanceToZero() {
        val preview = formPreview(-20_000, form(LedgerAction.ADVANCE_RETURN, "200"))!!
        assertEquals(0L, preview.balanceAfter)
        assertNull(preview.advanceCreated)
    }

    @Test
    fun anAdvanceReturnShowsTheMostThatCanBeGivenBack() {
        assertEquals(20_000L, maxAdvanceReturn(-20_000, LedgerAction.ADVANCE_RETURN))
        assertNull(maxAdvanceReturn(0, LedgerAction.ADVANCE_RETURN))
        assertNull(maxAdvanceReturn(5_000, LedgerAction.ADVANCE_RETURN))
        assertNull(maxAdvanceReturn(-20_000, LedgerAction.PAYMENT))
    }

    // =====================================================================================
    // The date box
    // =====================================================================================

    @Test
    fun thePickerDateRoundTripsAndIsShownInBengaliDigits() {
        val date = LocalDate.of(2026, 11, 11)
        assertEquals(date, pickerMillisToDate(dateToPickerMillis(date)))
        assertEquals(date, pickerMillisToDate(dateToPickerMillis(date) + 12 * 3_600_000L)) // any time that day
        assertEquals("১১/১১/২০২৬", dayText(date))
        assertEquals("০১/০১/২০১৯", dayText(LocalDate.of(2019, 1, 1)))
    }

    @Test
    fun noFutureDayCanBePicked() {
        assertTrue(isSelectableDay(dateToPickerMillis(today), today))
        assertTrue(isSelectableDay(dateToPickerMillis(today.minusDays(400)), today))
        assertFalse(isSelectableDay(dateToPickerMillis(today.plusDays(1)), today))
        assertFalse(isSelectableDay(dateToPickerMillis(today.plusYears(1)), today))
    }

    // =====================================================================================
    // The history rows: labels
    // =====================================================================================

    @Test
    fun eachRowShowsItsTypeLabelWithTheRightExtra() {
        assertEquals("পুরনো বাকি (খাতা থেকে)", title(entry(LedgerEntryType.OPENING_DUE)))
        assertEquals("বিক্রির বাকি · ২৬১১১১-০০১", title(entry(LedgerEntryType.SALE_DUE, saleId = 9), "261111-001"))
        assertEquals("বাকি দিলাম", title(entry(LedgerEntryType.MANUAL_DUE)))
        assertEquals("টাকা পেলাম · বিকাশ", title(entry(LedgerEntryType.PAYMENT_RECEIVED, method = PaymentMethod.BKASH)))
        assertEquals("সংশোধন (+) · কম লেখা ছিল", title(entry(LedgerEntryType.CORRECTION_PLUS, note = "কম লেখা ছিল")))
        assertEquals("সংশোধন (−) · বেশি লেখা ছিল", title(entry(LedgerEntryType.CORRECTION_MINUS, note = "বেশি লেখা ছিল")))
        assertEquals("বিক্রি বাতিল · ২৬১১১১-০০১", title(entry(LedgerEntryType.CORRECTION_MINUS, saleId = 9), "261111-001"))
        assertEquals("অগ্রিম ফেরত দিলাম · নগদ", title(entry(LedgerEntryType.ADVANCE_RETURNED, method = PaymentMethod.CASH)))
    }

    @Test
    fun aRowWithoutItsExtraShowsJustTheLabel() {
        assertEquals("টাকা পেলাম", title(entry(LedgerEntryType.PAYMENT_RECEIVED, method = null)))
        assertEquals("সংশোধন (+)", title(entry(LedgerEntryType.CORRECTION_PLUS, note = null)))
        assertEquals("বিক্রির বাকি", title(entry(LedgerEntryType.SALE_DUE, saleId = 9), receiptNo = null))
    }

    @Test
    fun theNoteLineIsShownExceptForCorrectionsWhoseNoteIsInTheTitle() {
        assertEquals("ধার দিলাম", historyNote(row(entry(LedgerEntryType.MANUAL_DUE, note = "ধার দিলাম"))))
        assertEquals("আংশিক", historyNote(row(entry(LedgerEntryType.PAYMENT_RECEIVED, note = "আংশিক"))))
        assertNull(historyNote(row(entry(LedgerEntryType.MANUAL_DUE, note = null))))
        assertNull(historyNote(row(entry(LedgerEntryType.MANUAL_DUE, note = "  "))))
        assertNull(historyNote(row(entry(LedgerEntryType.CORRECTION_PLUS, note = "ভুল"))))
        assertNull(historyNote(row(entry(LedgerEntryType.CORRECTION_MINUS, note = "ভুল"))))
    }

    @Test
    fun theAmountHasAPlusWhenItAddsToTheDueAndAMinusWhenItTakesAway() {
        assertEquals("+ ৳ ১২০", historyAmountText(entry(LedgerEntryType.SALE_DUE), "১২০", "৳"))
        assertEquals("+ ৳ ১২০", historyAmountText(entry(LedgerEntryType.ADVANCE_RETURNED), "১২০", "৳"))
        assertEquals("− ৳ ১২০", historyAmountText(entry(LedgerEntryType.PAYMENT_RECEIVED), "১২০", "৳"))
        assertEquals("− ৳ ১২০", historyAmountText(entry(LedgerEntryType.CORRECTION_MINUS), "১২০", "৳"))
        assertTrue(addsToDue(entry(LedgerEntryType.OPENING_DUE)))
        assertFalse(addsToDue(entry(LedgerEntryType.PAYMENT_RECEIVED)))
    }

    @Test
    fun onlyTheOpeningDueIsHighlighted() {
        for (type in LedgerEntryType.entries) {
            assertEquals(type.name, type == LedgerEntryType.OPENING_DUE, isHighlighted(entry(type)))
        }
    }

    @Test
    fun aBackDatedRowShowsWhenItWasWrittenAndNoTime() {
        val template = { date: String -> "লেখা হয়েছে $date" }
        val normal = entry(LedgerEntryType.PAYMENT_RECEIVED)
        assertNull(writtenOnText(normal, template))
        assertTrue(showsTime(normal))

        // Entered on 11 Nov for 15 Jan: dated 15 Jan, written 11 Nov.
        val old = entry(LedgerEntryType.OPENING_DUE, entryDate = entryDateFor(LocalDate.of(2026, 1, 15), now), createdAt = now)
        assertEquals("লেখা হয়েছে ১১/১১/২০২৬", writtenOnText(old, template))
        assertFalse(showsTime(old))
    }

    @Test
    fun saleRowsOpenTheReceiptAndCancelledSalesGetTheTag() {
        val saleDue = row(entry(LedgerEntryType.SALE_DUE, saleId = 9), "261111-001", cancelled = true)
        assertTrue(opensReceipt(saleDue))
        assertTrue(showsCancelledTag(saleDue))
        assertTrue(opensReceipt(row(entry(LedgerEntryType.CORRECTION_MINUS, saleId = 9), "261111-001")))
        assertFalse(opensReceipt(row(entry(LedgerEntryType.MANUAL_DUE))))
        assertFalse(showsCancelledTag(row(entry(LedgerEntryType.SALE_DUE, saleId = 9), "261111-001", cancelled = false)))
    }

    // =====================================================================================
    // Which buttons show
    // =====================================================================================

    @Test
    fun aCustomerWhoOwesGetsFourButtonsWithOneCorrection() {
        assertEquals(
            listOf(LedgerAction.PAYMENT, LedgerAction.MANUAL_DUE, LedgerAction.OPENING_DUE, LedgerAction.CORRECTION_PLUS),
            buttonActions(balance = 10_000, hasOpeningDue = false)
        )
    }

    @Test
    fun theOpeningDueButtonGoesAwayOnceThereIsOne() {
        val actions = buttonActions(balance = 10_000, hasOpeningDue = true)
        assertFalse(LedgerAction.OPENING_DUE in actions)
        assertEquals(listOf(LedgerAction.PAYMENT, LedgerAction.MANUAL_DUE, LedgerAction.CORRECTION_PLUS), actions)
    }

    @Test
    fun theAdvanceReturnButtonShowsOnlyWhileThereIsAnAdvance() {
        assertTrue(LedgerAction.ADVANCE_RETURN in buttonActions(balance = -1, hasOpeningDue = false))
        assertFalse(LedgerAction.ADVANCE_RETURN in buttonActions(balance = 0, hasOpeningDue = false))
        assertFalse(LedgerAction.ADVANCE_RETURN in buttonActions(balance = 5_000, hasOpeningDue = false))
    }

    @Test
    fun aBrandNewCustomerGetsTheBasicButtons() {
        assertEquals(
            listOf(LedgerAction.PAYMENT, LedgerAction.MANUAL_DUE, LedgerAction.OPENING_DUE, LedgerAction.CORRECTION_PLUS),
            buttonActions(balance = 0, hasOpeningDue = false)
        )
    }

    @Test
    fun theRouteArgumentIsReadBackSafely() {
        for (action in LedgerAction.entries) assertEquals(action, actionFromRoute(action.name))
        assertNull(actionFromRoute("nonsense"))
        assertNull(actionFromRoute(null))
        assertNull(actionFromRoute(""))
    }
}

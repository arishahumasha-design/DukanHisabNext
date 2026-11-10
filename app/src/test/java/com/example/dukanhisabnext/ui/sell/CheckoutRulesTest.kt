package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.ui.customers.ValidatedCustomer
import com.example.dukanhisabnext.ui.products.FormError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules of the new payment design: the "নতুন বাকি" amount and which customer goes on the sale. */
class CheckoutRulesTest {

    // ---- dueView: more than 0, not more than the total; paid = total - due ----

    @Test
    fun aValidDueGivesThePaidPart() {
        val v = dueView(total = 20_000, dueText = "80")
        assertEquals(8_000L, v.due)
        assertEquals(12_000L, v.paid)
        assertNull(v.error)
    }

    @Test
    fun theWholeTotalMayBeDue() {
        val v = dueView(total = 20_000, dueText = "200")
        assertEquals(20_000L, v.due)
        assertEquals(0L, v.paid)
        assertNull(v.error)
    }

    @Test
    fun dueWithPaisaAndBengaliDigits() {
        val v = dueView(total = 33_333, dueText = "১০০.৫০")
        assertEquals(10_050L, v.due)
        assertEquals(23_283L, v.paid)
    }

    @Test
    fun dueMustBeMoreThanZero() {
        assertEquals(FormError.MUST_BE_POSITIVE, dueView(20_000, "0").error)
        assertEquals(FormError.MUST_BE_POSITIVE, dueView(20_000, "0.00").error)
    }

    @Test
    fun dueMustNotBeMoreThanTheTotal() {
        assertEquals(FormError.DUE_MORE_THAN_TOTAL, dueView(20_000, "200.01").error)
        assertEquals(FormError.DUE_MORE_THAN_TOTAL, dueView(20_000, "500").error)
    }

    @Test
    fun dueBoxMustBeFilledAndReadable() {
        assertEquals(FormError.REQUIRED, dueView(20_000, "").error)
        assertEquals(FormError.INVALID_NUMBER, dueView(20_000, "abc").error)
        assertEquals(FormError.INVALID_NUMBER, dueView(20_000, "1.234").error)
        assertNull(dueView(20_000, "").due)
    }

    // ---- defaultDuePaisa: what the নতুন বাকি screen starts with ----

    @Test
    fun defaultDueIsWhatIsMissingWhenLessWasReceived() {
        assertEquals(8_000L, defaultDuePaisa(total = 20_000, receivedText = "120"))
    }

    @Test
    fun defaultDueIsTheWholeTotalOtherwise() {
        assertEquals(20_000L, defaultDuePaisa(20_000, "200"))  // received exactly the total
        assertEquals(20_000L, defaultDuePaisa(20_000, "500"))  // received more
        assertEquals(20_000L, defaultDuePaisa(20_000, ""))     // empty box
        assertEquals(20_000L, defaultDuePaisa(20_000, "abc"))  // unreadable box
    }

    @Test
    fun defaultDueWhenNothingWasReceived() {
        assertEquals(20_000L, defaultDuePaisa(20_000, "0"))
    }

    @Test
    fun theDefaultDueIsAlwaysAValidDue() {
        for (received in listOf("0", "1", "120", "199.99", "200", "999", "")) {
            val default = defaultDuePaisa(20_000, received)
            val checked = dueView(20_000, (default / 100).toString() + "." + (default % 100).toString().padStart(2, '0'))
            assertNull("default due for received '$received'", checked.error)
        }
    }

    // ---- customerDecision ----

    @Test
    fun switchOffAndNoDueMeansNoCustomer() {
        assertEquals(
            CustomerDecision.NoCustomer,
            customerDecision(sectionOn = false, selectedId = null, name = "", phone = "", address = "", required = false)
        )
    }

    @Test
    fun switchOffWithADueStopsAndAsksForAName() {
        val d = customerDecision(sectionOn = false, selectedId = null, name = "", phone = "", address = "", required = true)
        assertTrue(d is CustomerDecision.Missing)
        assertEquals(FormError.REQUIRED, (d as CustomerDecision.Missing).errors.name)
    }

    @Test
    fun switchOffMeansNoCustomerEvenIfOneWasChosenEarlier() {
        // The customer card is only hidden when the switch is off; this sale then has no customer.
        assertEquals(
            CustomerDecision.NoCustomer,
            customerDecision(sectionOn = false, selectedId = 7, name = "", phone = "", address = "", required = false)
        )
    }

    @Test
    fun switchOffWithADueStillNeedsACustomerEvenIfOneWasChosenEarlier() {
        val d = customerDecision(sectionOn = false, selectedId = 7, name = "", phone = "", address = "", required = true)
        assertTrue(d is CustomerDecision.Missing)
    }

    @Test
    fun aChosenCustomerIsUsed() {
        assertEquals(
            CustomerDecision.UseExisting(7),
            customerDecision(sectionOn = true, selectedId = 7, name = "করিম", phone = "", address = "", required = true)
        )
    }

    @Test
    fun aChosenOrCreatedCustomerIsUsedAgainOnARetry() {
        // After a new customer is created its id is the "selected" one, so a retry never creates a second.
        val first = customerDecision(true, selectedId = null, name = "রহিম", phone = "", address = "", required = true)
        assertTrue(first is CustomerDecision.CreateNew)
        val retry = customerDecision(true, selectedId = 42, name = "রহিম", phone = "", address = "", required = true)
        assertEquals(CustomerDecision.UseExisting(42), retry)
    }

    @Test
    fun aNewNameCreatesACustomerWithCleanDetails() {
        val d = customerDecision(
            sectionOn = true, selectedId = null,
            name = "  রহিম  ", phone = "০১৭১১-০০০০০০", address = "  বাজার  ", required = false
        )
        assertEquals(
            CustomerDecision.CreateNew(ValidatedCustomer(name = "রহিম", phone = "01711000000", address = "বাজার", note = null)),
            d
        )
    }

    @Test
    fun aMobileWithThePlus88IsAccepted() {
        val d = customerDecision(true, null, "রহিম", "+8801711000000", "", required = true)
        assertEquals("01711000000", (d as CustomerDecision.CreateNew).customer.phone)
    }

    @Test
    fun aWrongMobileStopsTheSaleEvenWithoutADue() {
        val d = customerDecision(true, null, "রহিম", "123", "", required = false)
        assertEquals(FormError.INVALID_PHONE, (d as CustomerDecision.Missing).errors.phone)
    }

    @Test
    fun anEmptyNameWithTheSwitchOnIsFineWhenThereIsNoDue() {
        assertEquals(CustomerDecision.NoCustomer, customerDecision(true, null, "  ", "", "", required = false))
    }

    @Test
    fun anEmptyNameWithADueIsMissing() {
        val d = customerDecision(true, null, "", "01711000000", "", required = true)
        assertEquals(FormError.REQUIRED, (d as CustomerDecision.Missing).errors.name)
    }

    // ---- buildDraft carries the note ----

    @Test
    fun theNoteGoesIntoTheDraft() {
        val summary = summarize(
            CartState(lines = listOf(CartLineInput(1, "চাল", ProductUnit.KG, "1", "200")))
        )
        val draft = buildDraft(summary, 20_000, PaymentMethod.CASH, null, "d", note = "পরে দেবে")
        assertEquals("পরে দেবে", draft.note)
        assertNull(buildDraft(summary, 20_000, PaymentMethod.CASH, null, "d").note)
    }

    @Test
    fun aDueSaleIsSavedAsReceivedEqualsPaid() {
        // On the নতুন বাকি screen: due ৳80 of ৳200 -> received (= paid) ৳120, method CASH.
        val summary = summarize(CartState(lines = listOf(CartLineInput(1, "চাল", ProductUnit.KG, "1", "200"))))
        val due = dueView(summary.total, "80")
        val draft = buildDraft(summary, summary.total - due.due!!, PaymentMethod.CASH, customerId = 3, draftId = "d")
        assertEquals(12_000L, draft.received)
        assertEquals(3L, draft.customerId)

        // The same calculator the save uses then gives exactly that due and no change.
        val view = paymentView(summary, "120", hasCustomer = true)
        assertEquals(8_000L, view.due)
        assertEquals(0L, view.change)
    }
}

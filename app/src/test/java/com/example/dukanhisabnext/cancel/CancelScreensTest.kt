package com.example.dukanhisabnext.cancel

import com.example.dukanhisabnext.data.CancelResult
import com.example.dukanhisabnext.data.dao.SaleListRow
import com.example.dukanhisabnext.data.entity.SaleCancellation
import com.example.dukanhisabnext.receipt.CancelNotice
import com.example.dukanhisabnext.receipt.SALE_TIME
import com.example.dukanhisabnext.receipt.aSale
import com.example.dukanhisabnext.receipt.buildReceiptData
import com.example.dukanhisabnext.receipt.buildReceiptText
import com.example.dukanhisabnext.receipt.cancelNotice
import com.example.dukanhisabnext.receipt.customer
import com.example.dukanhisabnext.receipt.receipt
import com.example.dukanhisabnext.receipt.receiptMoney
import com.example.dukanhisabnext.receipt.savedItems
import com.example.dukanhisabnext.receipt.testLabels
import com.example.dukanhisabnext.receipt.units
import com.example.dukanhisabnext.ui.sales.SalesFilter
import com.example.dukanhisabnext.ui.sales.SalesSummary
import com.example.dukanhisabnext.ui.sales.SalesListItem
import com.example.dukanhisabnext.ui.sales.EmptyKind
import com.example.dukanhisabnext.ui.sales.buildSalesListUi
import com.example.dukanhisabnext.ui.sales.matchesSalesQuery
import com.example.dukanhisabnext.ui.sales.salesSummary
import com.example.dukanhisabnext.ui.sales.salesSummaryLine
import com.example.dukanhisabnext.ui.sales.showCancelledTag
import com.example.dukanhisabnext.ui.sales.showDueTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words and small rules of the cancel screens (বিক্রি বাতিল): the confirm dialog summary, the
 * banner, the shared receipt text, the reason box limit, the messages, and the sales list tag and
 * summary line. Plain Kotlin only; the Bengali words are the same as in strings.xml.
 */
class CancelScreensTest {

    private val dialogLabels = CancelDialogLabels(
        stockBack = "স্টক ফেরত যাবে",
        refund = { "কাস্টমারকে $it ফেরত দিন" },
        dueReduced = { "বাকি $it কমবে" },
        advance = { "কাস্টমারের ব্যালেন্স হবে অগ্রিম $it" }
    )

    private fun money(paisa: Long) = receiptMoney(paisa, testLabels)

    // =====================================================================================
    // The confirm dialog summary
    // =====================================================================================

    @Test
    fun aSaleWithPaymentAndDueShowsStockRefundAndDue() {
        // ৳200 paid, ৳300 due, the customer owes ৳300 in total: after cancelling the balance is 0.
        val data = receipt(customerName = "রহিম", paid = 20_000, saleDue = 30_000, total = 50_000, customerBalance = 30_000)
        val lines = cancelSummaryFor(data, dialogLabels, testLabels)

        assertEquals(
            listOf(
                CancelSummaryLine("স্টক ফেরত যাবে"),
                CancelSummaryLine("কাস্টমারকে ${money(20_000)} ফেরত দিন"),
                CancelSummaryLine("বাকি ${money(30_000)} কমবে")
            ),
            lines
        )
        assertTrue(lines.none { it.isWarning })
    }

    @Test
    fun aFullyPaidSaleHasNoDueLine() {
        val data = receipt(customerName = "রহিম", paid = 25_000, saleDue = 0, customerBalance = 8_000)
        val lines = cancelSummaryFor(data, dialogLabels, testLabels).map { it.text }
        assertEquals(listOf("স্টক ফেরত যাবে", "কাস্টমারকে ${money(25_000)} ফেরত দিন"), lines)
    }

    @Test
    fun aSaleWithNothingPaidHasNoRefundLine() {
        val data = receipt(customerName = "রহিম", paid = 0, saleDue = 25_000, customerBalance = 25_000)
        val lines = cancelSummaryFor(data, dialogLabels, testLabels).map { it.text }
        assertEquals(listOf("স্টক ফেরত যাবে", "বাকি ${money(25_000)} কমবে"), lines)
    }

    @Test
    fun aSaleWithoutACustomerHasOnlyStockAndRefund() {
        val data = receipt(paid = 25_000, saleDue = 0, customerBalance = null)
        val lines = cancelSummaryFor(data, dialogLabels, testLabels).map { it.text }
        assertEquals(listOf("স্টক ফেরত যাবে", "কাস্টমারকে ${money(25_000)} ফেরত দিন"), lines)
    }

    @Test
    fun aBalanceThatWouldGoNegativeGetsTheAdvanceWarning() {
        // The sale had a ৳300 due, but the customer already paid ৳100 of it later: balance ৳200.
        // Cancelling takes ৳300 away: the balance becomes -৳100 = an advance of ৳100.
        val data = receipt(customerName = "রহিম", paid = 0, saleDue = 30_000, total = 30_000, customerBalance = 20_000)
        val lines = cancelSummaryFor(data, dialogLabels, testLabels)

        assertEquals(
            CancelSummaryLine("কাস্টমারের ব্যালেন্স হবে অগ্রিম ${money(10_000)}", isWarning = true),
            lines.last()
        )
        assertEquals(1, lines.count { it.isWarning })
    }

    @Test
    fun aBalanceOfExactlyZeroIsNotAnAdvance() {
        val data = receipt(customerName = "রহিম", paid = 0, saleDue = 30_000, total = 30_000, customerBalance = 30_000)
        assertTrue(cancelSummaryFor(data, dialogLabels, testLabels).none { it.isWarning })
    }

    // =====================================================================================
    // The banner and the shared text
    // =====================================================================================

    @Test
    fun anActiveSaleHasNoBannerAndTheNormalText() {
        val data = receipt()
        assertNull(cancelNotice(data, testLabels))
        assertFalse(data.isCancelled)
        assertFalse(buildReceiptText(data, testLabels).contains("বাতিল"))
    }

    @Test
    fun theBannerShowsTheDhakaDateTimeAndTheReason() {
        // 23:45 on 10 Nov 2026 in Dhaka.
        val data = receipt(cancelledAt = SALE_TIME, cancelReason = "ভুল দাম")
        assertTrue(data.isCancelled)
        assertEquals(
            CancelNotice(title = "বাতিল করা হয়েছে", dateTime = "১০/১১/২০২৬ রাত ১১:৪৫", reason = "কারণ: ভুল দাম"),
            cancelNotice(data, testLabels)
        )
    }

    @Test
    fun theBannerHasNoReasonLineWhenNoneWasGiven() {
        val notice = cancelNotice(receipt(cancelledAt = SALE_TIME), testLabels)!!
        assertNull(notice.reason)
    }

    @Test
    fun theSharedTextOfACancelledSaleStartsWithBatil() {
        val text = buildReceiptText(receipt(cancelledAt = SALE_TIME, cancelReason = "ভুল দাম"), testLabels)
        val lines = text.lines()

        assertTrue(text.startsWith("বাতিল"))
        assertEquals("বাতিল করা হয়েছে: ১০/১১/২০২৬ রাত ১১:৪৫", lines[0])
        assertEquals("কারণ: ভুল দাম", lines[1])
        // The receipt itself is still complete underneath.
        assertTrue(text.contains("রসিদ নং: ২৬১১১০-০০১"))
        assertTrue(text.contains("মোট প্রদেয়"))
    }

    @Test
    fun theSharedTextWithoutAReasonHasNoReasonLine() {
        val text = buildReceiptText(receipt(cancelledAt = SALE_TIME), testLabels)
        assertTrue(text.startsWith("বাতিল করা হয়েছে: "))
        assertFalse(text.contains("কারণ"))
    }

    @Test
    fun theReceiptDataKnowsTheCancellationAndTheRealBalance() {
        val cancellation = SaleCancellation(id = 1, saleId = 7, reason = "  ভুল দাম  ", createdAt = SALE_TIME + 60_000)
        // The customer's balance is NEGATIVE (an advance): the receipt keeps the real number,
        // but the "your total due" line stays hidden (it is only for customers who owe money).
        val data = buildReceiptData(
            aSale(customerId = 3, paid = 0, due = 25_000), savedItems, units, customer(), null, -5_000, 0, cancellation
        )

        assertTrue(data.isCancelled)
        assertEquals(SALE_TIME + 60_000, data.cancelledAt)
        assertEquals("ভুল দাম", data.cancelReason)
        assertEquals(7L, data.saleId)
        assertEquals(-5_000L, data.customerBalance)
        assertNull(data.customerTotalDue)
    }

    @Test
    fun aBlankReasonOrNoCancellationMeansNoReason() {
        val blank = SaleCancellation(id = 1, saleId = 7, reason = "   ", createdAt = SALE_TIME)
        assertNull(buildReceiptData(aSale(), savedItems, units, null, null, null, 0, blank).cancelReason)

        val active = buildReceiptData(aSale(), savedItems, units, null, null, null, 0)
        assertFalse(active.isCancelled)
        assertNull(active.cancelledAt)
        assertNull(active.customerBalance) // no customer, no balance
    }

    // =====================================================================================
    // The reason box and the messages
    // =====================================================================================

    @Test
    fun theReasonBoxTakesAtMost200Characters() {
        assertEquals("ভুল", limitReasonInput("ভুল"))
        assertEquals("ক".repeat(200), limitReasonInput("ক".repeat(200)))
        assertEquals("ক".repeat(200), limitReasonInput("ক".repeat(250)))
        assertEquals(200, reasonLength(limitReasonInput("a".repeat(500))))
    }

    @Test
    fun anEmojiIsNeverCutInHalf() {
        val cut = limitReasonInput("😀".repeat(250))
        assertEquals("😀".repeat(200), cut)
        assertEquals(200, reasonLength(cut))
    }

    @Test
    fun theReasonCounterCountsLettersAsTheUserSeesThem() {
        assertEquals(0, reasonLength(""))
        assertEquals(3, reasonLength("ভুল"))
        assertEquals(2, reasonLength("😀😀"))
    }

    private val cancellation = SaleCancellation(id = 1, saleId = 7, reason = null, createdAt = SALE_TIME)

    @Test
    fun aSuccessfulOrRepeatedCancelNeedsNoMessage() {
        assertNull(cancelFeedback(CancelResult.Cancelled(cancellation)))
        // "Already cancelled": the receipt is loaded again and simply shows the banner.
        assertNull(cancelFeedback(CancelResult.AlreadyCancelled(cancellation)))
    }

    @Test
    fun theOtherResultsGetACalmMessage() {
        assertEquals(CancelFeedback.SALE_NOT_FOUND, cancelFeedback(CancelResult.SaleNotFound))
        assertEquals(CancelFeedback.REASON_TOO_LONG, cancelFeedback(CancelResult.ReasonTooLong))
    }

    @Test
    fun theDialogStaysOpenOnlyForATooLongReason() {
        assertTrue(closesDialog(CancelResult.Cancelled(cancellation)))
        assertTrue(closesDialog(CancelResult.AlreadyCancelled(cancellation)))
        assertTrue(closesDialog(CancelResult.SaleNotFound))
        assertFalse(closesDialog(CancelResult.ReasonTooLong))
    }

    @Test
    fun theCancelButtonIsHiddenOnceCancelledAndDisabledWhileCancelling() {
        assertTrue(showCancelButton(receipt()))
        assertFalse(showCancelButton(receipt(cancelledAt = SALE_TIME)))
        assertTrue(cancelButtonEnabled(cancelling = false))
        assertFalse(cancelButtonEnabled(cancelling = true))
    }

    // =====================================================================================
    // The sales list: tag, summary and summary line
    // =====================================================================================

    private fun row(
        id: Long,
        total: Long = 10_000,
        due: Long = 0,
        name: String? = null,
        cancelledAt: Long? = null,
        createdAt: Long = SALE_TIME
    ) = SaleListRow(id, "261110-%03d".format(id), createdAt, total, due, name, null, cancelledAt)

    private val summaryText = { count: String, total: String -> "$count টি বিক্রি · মোট ৳ $total" }
    private val excludedText = { count: String -> "(বাতিল $count টি বাদ)" }

    @Test
    fun theCountAndTotalLeaveCancelledSalesOut() {
        val rows = listOf(row(1, total = 10_000), row(2, total = 20_000, cancelledAt = SALE_TIME + 1), row(3, total = 5_000))
        assertEquals(SalesSummary(count = 2, total = 15_000, cancelledExcluded = 1), salesSummary(rows))
    }

    @Test
    fun whenNothingIsCancelledNothingIsExcluded() {
        assertEquals(SalesSummary(count = 2, total = 30_000, cancelledExcluded = 0), salesSummary(listOf(row(1), row(2, total = 20_000))))
        assertEquals(SalesSummary(0, 0, 0), salesSummary(emptyList()))
    }

    @Test
    fun theSummaryLineAddsTheNoteOnlyWhenSomeAreLeftOut() {
        val plain = salesSummaryLine(SalesSummary(2, 15_000), summaryText, excludedText)
        assertEquals("২ টি বিক্রি · মোট ৳ ১৫০", plain)
        assertFalse(plain.contains("বাতিল"))

        val withNote = salesSummaryLine(SalesSummary(2, 15_000, cancelledExcluded = 3), summaryText, excludedText)
        assertEquals("২ টি বিক্রি · মোট ৳ ১৫০ (বাতিল ৩ টি বাদ)", withNote)
    }

    @Test
    fun whenEverythingIsCancelledTheLineShowsZeroAndTheNote() {
        val summary = salesSummary(listOf(row(1, cancelledAt = SALE_TIME + 1)))
        assertEquals("০ টি বিক্রি · মোট ৳ ০ (বাতিল ১ টি বাদ)", salesSummaryLine(summary, summaryText, excludedText))
    }

    @Test
    fun aCancelledRowGetsTheGreyTagAndNoDueTag() {
        val cancelledWithDue = row(1, due = 5_000, cancelledAt = SALE_TIME + 1)
        assertTrue(showCancelledTag(cancelledWithDue))
        assertFalse(showDueTag(cancelledWithDue))

        val activeWithDue = row(2, due = 5_000)
        assertFalse(showCancelledTag(activeWithDue))
        assertTrue(showDueTag(activeWithDue))

        val activePaid = row(3)
        assertFalse(showCancelledTag(activePaid))
        assertFalse(showDueTag(activePaid))
    }

    @Test
    fun cancelledSalesAreFoundBySearch() {
        val cancelled = row(1, name = "করিম", cancelledAt = SALE_TIME + 1)
        assertTrue(matchesSalesQuery(cancelled, "261110-001"))
        assertTrue(matchesSalesQuery(cancelled, "২৬১১১০০০১"))
        assertTrue(matchesSalesQuery(cancelled, "করিম"))
    }

    @Test
    fun cancelledSalesStayInTheListUnderEveryChipButAreNotCounted() {
        // The database already cuts the rows by the chip; the list keeps every row it is given.
        val rows = listOf(row(1, total = 10_000), row(2, total = 20_000, cancelledAt = SALE_TIME + 1))
        for (chip in SalesFilter.entries) {
            val ui = buildSalesListUi(rows, query = "", totalSalesInDb = 2)
            val entries = ui.items.filterIsInstance<SalesListItem.Entry>().map { it.row.id }
            assertEquals("chip $chip", listOf(2L, 1L), entries) // same time: the newer id first
            assertEquals(SalesSummary(count = 1, total = 10_000, cancelledExcluded = 1), ui.summary)
        }
    }

    @Test
    fun aListWithOnlyCancelledSalesIsNotEmpty() {
        val ui = buildSalesListUi(listOf(row(1, cancelledAt = SALE_TIME + 1)), query = "", totalSalesInDb = 1)
        assertNull(ui.empty)
        assertEquals(1, ui.items.filterIsInstance<SalesListItem.Entry>().size)
        assertEquals(SalesSummary(0, 0, 1), ui.summary)
    }

    @Test
    fun searchingKeepsCancelledSalesAndTheirNote() {
        val rows = listOf(row(1, name = "করিম"), row(2, name = "করিম", cancelledAt = SALE_TIME + 1), row(3, name = "রহিম"))
        val ui = buildSalesListUi(rows, query = "করিম", totalSalesInDb = 3)
        assertEquals(listOf(2L, 1L), ui.items.filterIsInstance<SalesListItem.Entry>().map { it.row.id })
        assertEquals(SalesSummary(count = 1, total = 10_000, cancelledExcluded = 1), ui.summary)
    }

    @Test
    fun noMatchStillGivesTheEmptyMessage() {
        val ui = buildSalesListUi(listOf(row(1)), query = "zzz", totalSalesInDb = 1)
        assertEquals(EmptyKind.NO_MATCH, ui.empty)
    }
}

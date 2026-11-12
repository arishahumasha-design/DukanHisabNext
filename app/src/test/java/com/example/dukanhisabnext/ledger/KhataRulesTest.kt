package com.example.dukanhisabnext.ledger

import com.example.dukanhisabnext.data.dao.KhataCustomerRow
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.util.DhakaTime
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KhataRulesTest {

    // 14:30 on Thursday 12 Nov 2026 in Dhaka (= 08:30 UTC).
    private val now = Instant.parse("2026-11-12T08:30:00Z").toEpochMilli()
    private val today = LocalDate.of(2026, 11, 12)

    private fun dhakaMidnight(date: LocalDate) = DhakaTime.startOfDay(date)

    private val months = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )

    private fun customer(id: Long, name: String, balance: Long, last: Long?, phone: String? = null) =
        KhataCustomerRow(Customer(id = id, name = name, phone = phone, createdAt = 0, updatedAt = 0), balance, last)

    private fun entry(type: LedgerEntryType, amount: Long, saleId: Long? = null, id: Long = 1) =
        LedgerEntry(id, 3, type, amount, null, saleId, null, now, now)

    // =====================================================================================
    // Period ranges
    // =====================================================================================

    @Test
    fun allHasNoTimeWindow() {
        assertNull(periodRange(KhataPeriod.ALL, now))
    }

    @Test
    fun todayIsTheDhakaDay() {
        val range = periodRange(KhataPeriod.TODAY, now)!!
        assertEquals(dhakaMidnight(today), range.startMillis)
        assertEquals(dhakaMidnight(today.plusDays(1)), range.endMillisExclusive)
    }

    @Test
    fun sevenDaysIsTodayPlusTheSixDaysBefore() {
        val range = periodRange(KhataPeriod.LAST_7_DAYS, now)!!
        assertEquals(dhakaMidnight(LocalDate.of(2026, 11, 6)), range.startMillis)
        assertEquals(dhakaMidnight(LocalDate.of(2026, 11, 13)), range.endMillisExclusive)
        assertEquals(7L * 24 * 60 * 60 * 1000, range.endMillisExclusive - range.startMillis)
    }

    @Test
    fun thisMonthAndThisYearFollowDhaka() {
        val month = periodRange(KhataPeriod.THIS_MONTH, now)!!
        assertEquals(dhakaMidnight(LocalDate.of(2026, 11, 1)), month.startMillis)
        assertEquals(dhakaMidnight(LocalDate.of(2026, 12, 1)), month.endMillisExclusive)
        val year = periodRange(KhataPeriod.THIS_YEAR, now)!!
        assertEquals(dhakaMidnight(LocalDate.of(2026, 1, 1)), year.startMillis)
        assertEquals(dhakaMidnight(LocalDate.of(2027, 1, 1)), year.endMillisExclusive)
    }

    @Test
    fun justAfterMidnightInDhakaTheNewDayCounts() {
        // 00:10 on 13 Nov in Dhaka is still 12 Nov in UTC.
        val lateNight = Instant.parse("2026-11-12T18:10:00Z").toEpochMilli()
        assertEquals(dhakaMidnight(LocalDate.of(2026, 11, 13)), periodRange(KhataPeriod.TODAY, lateNight)!!.startMillis)
        assertEquals(dhakaMidnight(LocalDate.of(2026, 11, 7)), periodRange(KhataPeriod.LAST_7_DAYS, lateNight)!!.startMillis)
    }

    @Test
    fun aCustomRangeCoversBothDaysCompletely() {
        val range = periodRange(KhataPeriod.CUSTOM, now, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))!!
        assertEquals(dhakaMidnight(LocalDate.of(2026, 10, 1)), range.startMillis)
        assertEquals(dhakaMidnight(LocalDate.of(2026, 11, 1)), range.endMillisExclusive)
    }

    @Test
    fun aCustomRangeNeedsBothDays() {
        assertNull(periodRange(KhataPeriod.CUSTOM, now))
        assertNull(periodRange(KhataPeriod.CUSTOM, now, customFrom = today))
        assertNull(periodRange(KhataPeriod.CUSTOM, now, customTo = today))
    }

    @Test
    fun customDaysAreSwappedWhenBackwardsAndNeverInTheFuture() {
        assertEquals(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 5), customDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1), today))
        assertEquals(LocalDate.of(2026, 11, 1) to today, customDays(LocalDate.of(2026, 11, 1), today.plusDays(10), today))
        assertEquals(today to today, customDays(today.plusDays(3), today.plusDays(9), today))
        assertEquals(today to today, customDays(today, today, today)) // one single day is fine
    }

    // =====================================================================================
    // Search
    // =====================================================================================

    @Test
    fun searchMatchesNameOrMobileWithBengaliDigits() {
        assertTrue(matchesKhataSearch("করিম মিয়া", "01711000000", ""))
        assertTrue(matchesKhataSearch("করিম মিয়া", "01711000000", "  "))
        assertTrue(matchesKhataSearch("করিম মিয়া", "01711000000", "করিম"))
        assertTrue(matchesKhataSearch("Rahim", null, "rah"))
        assertTrue(matchesKhataSearch("করিম", "01711000000", "01711"))
        assertTrue(matchesKhataSearch("করিম", "01711000000", "০১৭১১"))
        assertTrue(matchesKhataSearch("করিম", "01711000000", "+8801711"))
        assertTrue(matchesKhataSearch("করিম", "01711000000", "017-11"))
        assertFalse(matchesKhataSearch("করিম", "01711000000", "রহিম"))
        assertFalse(matchesKhataSearch("করিম", null, "017"))
    }

    // =====================================================================================
    // The "সব" list: sort and filter
    // =====================================================================================

    private val day = 24L * 60 * 60 * 1000
    private val rows = listOf(
        customer(1, "করিম", balance = 50_000, last = now - 3 * day),
        customer(2, "রহিম", balance = -7_000, last = now),
        customer(3, "আলম", balance = 0, last = null),       // never had an entry
        customer(4, "সালমা", balance = 10_000, last = now - 10 * day),
        customer(5, "বাবলু", balance = 10_000, last = now - 3 * day) // same date as করিম
    )

    private fun ids(list: List<KhataCustomerRow>) = list.map { it.customer.id }

    @Test
    fun newestTransactionFirstIsTheDefaultAndNoEntryCustomersComeLast() {
        assertEquals(KhataSort.LAST_ENTRY_NEWEST, KhataSort.entries.first())
        // করিম and বাবলু have the same date: then by name (ক comes before ব in the Bengali alphabet).
        assertEquals(listOf(2L, 1L, 5L, 4L, 3L), ids(sortKhataCustomers(rows, KhataSort.LAST_ENTRY_NEWEST)))
    }

    @Test
    fun oldestTransactionFirstAlsoPutsNoEntryCustomersLast() {
        assertEquals(listOf(4L, 1L, 5L, 2L, 3L), ids(sortKhataCustomers(rows, KhataSort.LAST_ENTRY_OLDEST)))
    }

    @Test
    fun amountLowToHighPutsTheAdvanceFirst() {
        // সালমা and বাবলু both owe ৳100: then by name (ব before স).
        assertEquals(listOf(2L, 3L, 5L, 4L, 1L), ids(sortKhataCustomers(rows, KhataSort.AMOUNT_LOW_TO_HIGH)))
    }

    @Test
    fun amountHighToLowPutsTheBiggestDueFirst() {
        assertEquals(listOf(1L, 5L, 4L, 3L, 2L), ids(sortKhataCustomers(rows, KhataSort.AMOUNT_HIGH_TO_LOW)))
    }

    @Test
    fun tiesAreBrokenByNameThenIdSoTheOrderNeverJumps() {
        val twins = listOf(customer(9, "Same", 100, now), customer(8, "Same", 100, now), customer(7, "Abc", 100, now))
        for (sort in KhataSort.entries) {
            assertEquals(sort.name, listOf(7L, 8L, 9L), ids(sortKhataCustomers(twins, sort)))
        }
    }

    @Test
    fun theOwingFilterKeepsOnlyBalancesAboveZero() {
        val shown = visibleKhataCustomers(rows, query = "", onlyOwing = true, sort = KhataSort.AMOUNT_HIGH_TO_LOW)
        assertEquals(listOf(1L, 4L, 5L).sorted(), ids(shown).sorted())
        assertTrue(shown.all { it.balance > 0 })
    }

    @Test
    fun searchAndFilterWorkTogether() {
        assertEquals(listOf(1L), ids(visibleKhataCustomers(rows, "করিম", onlyOwing = true, sort = KhataSort.LAST_ENTRY_NEWEST)))
        assertTrue(visibleKhataCustomers(rows, "রহিম", onlyOwing = true, sort = KhataSort.LAST_ENTRY_NEWEST).isEmpty())
        assertEquals(listOf(2L), ids(visibleKhataCustomers(rows, "রহিম", onlyOwing = false, sort = KhataSort.LAST_ENTRY_NEWEST)))
    }

    @Test
    fun everyoneIsShownWithoutFilterOrSearch() {
        assertEquals(5, visibleKhataCustomers(rows, "", onlyOwing = false, sort = KhataSort.LAST_ENTRY_NEWEST).size)
    }

    // =====================================================================================
    // The "সব" summary (the whole shop)
    // =====================================================================================

    @Test
    fun theAllSummaryAddsDuesAndAdvancesSeparately() {
        val summary = khataAllSummary(rows)
        assertEquals(70_000L, summary.receivable) // 500 + 100 + 100
        assertEquals(7_000L, summary.advance)
        assertEquals(63_000L, summary.net)
        assertEquals(rows.sumOf { it.balance }, summary.net)
    }

    @Test
    fun anEmptyShopHasAZeroSummary() {
        assertEquals(KhataAllSummary(0, 0), khataAllSummary(emptyList()))
        assertEquals(0L, khataAllSummary(emptyList()).net)
    }

    // =====================================================================================
    // Last-date text
    // =====================================================================================

    @Test
    fun theLastDateIsTheDhakaDayWithPorjonto() {
        val template = { date: String -> "$date পর্যন্ত" }
        assertEquals("১২ নভেম্বর ২০২৬ পর্যন্ত", lastEntryText(now, months, template))
        // 23:50 on 12 Nov in Dhaka is 17:50 UTC, still 12 Nov in Dhaka.
        assertEquals("১২ নভেম্বর ২০২৬ পর্যন্ত", lastEntryText(Instant.parse("2026-11-12T17:50:00Z").toEpochMilli(), months, template))
        // 00:10 on 13 Nov in Dhaka is still 12 Nov in UTC.
        assertEquals("১৩ নভেম্বর ২০২৬ পর্যন্ত", lastEntryText(Instant.parse("2026-11-12T18:10:00Z").toEpochMilli(), months, template))
        assertNull(lastEntryText(null, months, template))
    }

    // =====================================================================================
    // The period summary
    // =====================================================================================

    private val mixed = listOf(
        entry(LedgerEntryType.SALE_DUE, 30_000, saleId = 1),
        entry(LedgerEntryType.SALE_DUE, 20_000, saleId = 2),
        entry(LedgerEntryType.MANUAL_DUE, 5_000),
        entry(LedgerEntryType.OPENING_DUE, 40_000),
        entry(LedgerEntryType.PAYMENT_RECEIVED, 25_000),
        entry(LedgerEntryType.PAYMENT_RECEIVED, 1_000),
        entry(LedgerEntryType.ADVANCE_RETURNED, 2_000),
        entry(LedgerEntryType.CORRECTION_PLUS, 3_000),
        entry(LedgerEntryType.CORRECTION_MINUS, 1_000),             // manual correction
        entry(LedgerEntryType.CORRECTION_MINUS, 20_000, saleId = 2) // sale 2 was cancelled
    )

    @Test
    fun eachLineHasItsOwnTypes() {
        val s = periodSummary(mixed)
        assertEquals(50_000L, s.dueOnSales)
        assertEquals(5_000L, s.dueByHand)
        assertEquals(55_000L, s.dueGiven)
        assertEquals(40_000L, s.openingDue)
        assertEquals(26_000L, s.received)
        assertEquals(2_000L, s.advanceReturned)
        assertEquals(2_000L, s.correctionNet)
        assertEquals(20_000L, s.saleCancelled)
    }

    @Test
    fun theLinesAlwaysAddUpToTheNetWhichIsTheSumOfSignedAmounts() {
        val s = periodSummary(mixed)
        val expected = 50_000L + 5_000 + 40_000 - 26_000 + 2_000 + 3_000 - 1_000 - 20_000
        assertEquals(expected, s.net)
        assertEquals(mixed.sumOf { signedAmount(it) }, s.net)
        assertEquals(s.net, s.linesTotal)
    }

    @Test
    fun theNetInvariantHoldsForEveryTypeAloneAndBothCorrectionDirections() {
        for (type in LedgerEntryType.entries) {
            for (sale in listOf<Long?>(null, 7)) {
                val list = listOf(entry(type, 12_345, saleId = sale))
                val s = periodSummary(list)
                assertEquals("$type sale=$sale", signedAmount(type, 12_345), s.net)
                assertEquals("$type sale=$sale", s.net, s.linesTotal)
            }
        }
        // Corrections that take away more than they add make the correction line negative.
        val s = periodSummary(listOf(entry(LedgerEntryType.CORRECTION_PLUS, 1_000), entry(LedgerEntryType.CORRECTION_MINUS, 4_000)))
        assertEquals(-3_000L, s.correctionNet)
        assertEquals(s.net, s.linesTotal)
    }

    @Test
    fun everyTypeFallsIntoExactlyOneLine() {
        assertEquals(PeriodLine.DUE_ON_SALES, periodLineOf(entry(LedgerEntryType.SALE_DUE, 1, saleId = 1)))
        assertEquals(PeriodLine.DUE_BY_HAND, periodLineOf(entry(LedgerEntryType.MANUAL_DUE, 1)))
        assertEquals(PeriodLine.OPENING_DUE, periodLineOf(entry(LedgerEntryType.OPENING_DUE, 1)))
        assertEquals(PeriodLine.RECEIVED, periodLineOf(entry(LedgerEntryType.PAYMENT_RECEIVED, 1)))
        assertEquals(PeriodLine.ADVANCE_RETURNED, periodLineOf(entry(LedgerEntryType.ADVANCE_RETURNED, 1)))
        assertEquals(PeriodLine.CORRECTION, periodLineOf(entry(LedgerEntryType.CORRECTION_PLUS, 1)))
        assertEquals(PeriodLine.CORRECTION, periodLineOf(entry(LedgerEntryType.CORRECTION_MINUS, 1)))
        assertEquals(PeriodLine.SALE_CANCEL, periodLineOf(entry(LedgerEntryType.CORRECTION_MINUS, 1, saleId = 4)))
    }

    @Test
    fun aRandomMixAlwaysKeepsTheInvariant() {
        val random = java.util.Random(42)
        repeat(200) { round ->
            val list = (0 until random.nextInt(30)).map { i ->
                val type = LedgerEntryType.entries[random.nextInt(LedgerEntryType.entries.size)]
                entry(type, 1L + random.nextInt(1_000_000), saleId = if (random.nextBoolean()) 9L else null, id = i.toLong())
            }
            val s = periodSummary(list)
            assertEquals("round $round", list.sumOf { signedAmount(it) }, s.net)
            assertEquals("round $round", s.net, s.linesTotal)
            assertEquals("round $round", s.dueOnSales + s.dueByHand, s.dueGiven)
        }
    }

    @Test
    fun anEmptyPeriodIsAllZero() {
        val s = periodSummary(emptyList())
        assertEquals(PeriodSummary(0, 0, 0, 0, 0, 0, 0, 0), s)
        assertFalse(showsDueBreakdown(s))
    }

    @Test
    fun theTwoMainLinesAlwaysShowAndTheOthersOnlyWhenNotZero() {
        assertEquals(
            listOf(SummaryLine.DUE_GIVEN, SummaryLine.RECEIVED, SummaryLine.NET),
            shownSummaryLines(periodSummary(emptyList()))
        )
        assertEquals(
            listOf(
                SummaryLine.DUE_GIVEN, SummaryLine.RECEIVED, SummaryLine.OPENING_DUE, SummaryLine.ADVANCE_RETURNED,
                SummaryLine.CORRECTION, SummaryLine.SALE_CANCEL, SummaryLine.NET
            ),
            shownSummaryLines(periodSummary(mixed))
        )
    }

    @Test
    fun theDueBreakdownSplitsSalesAndHandDues() {
        val s = periodSummary(listOf(entry(LedgerEntryType.SALE_DUE, 30_000, saleId = 1), entry(LedgerEntryType.MANUAL_DUE, 5_000)))
        assertTrue(showsDueBreakdown(s))
        assertEquals(30_000L, s.dueOnSales) // বিক্রিতে
        assertEquals(5_000L, s.dueByHand)   // হাতে
        assertEquals(35_000L, s.dueGiven)
        // A cancelled sale's due still counts as given on that day; the reversal is its own line.
        val cancelled = periodSummary(listOf(entry(LedgerEntryType.SALE_DUE, 30_000, saleId = 1), entry(LedgerEntryType.CORRECTION_MINUS, 30_000, saleId = 1)))
        assertEquals(30_000L, cancelled.dueOnSales)
        assertEquals(30_000L, cancelled.saleCancelled)
        assertEquals(0L, cancelled.net)
    }
}

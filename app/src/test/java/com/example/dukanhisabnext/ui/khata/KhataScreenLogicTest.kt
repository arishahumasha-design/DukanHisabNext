package com.example.dukanhisabnext.ui.khata

import com.example.dukanhisabnext.data.dao.KhataCustomerRow
import com.example.dukanhisabnext.data.dao.KhataEntryRow
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.ledger.KhataPeriod
import com.example.dukanhisabnext.ledger.KhataSort
import com.example.dukanhisabnext.ledger.SummaryLine
import com.example.dukanhisabnext.ledger.dateToPickerMillis
import com.example.dukanhisabnext.ledger.periodSummary
import com.example.dukanhisabnext.ui.components.customRangeText
import com.example.dukanhisabnext.ui.components.isSelectableFromDay
import com.example.dukanhisabnext.ui.components.isSelectableToDay
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The logic of the বাকির খাতা screen and the যোগাযোগ address book (chip state, text and message decisions). */
class KhataScreenLogicTest {

    private val today = LocalDate.of(2026, 11, 12)
    private val now = Instant.parse("2026-11-12T08:30:00Z").toEpochMilli() // 14:30 in Dhaka

    private fun customer(id: Long, name: String, balance: Long = 0, phone: String? = null, last: Long? = null) =
        KhataCustomerRow(Customer(id = id, name = name, phone = phone, createdAt = 0, updatedAt = 0), balance, last)

    private fun entryRow(
        id: Long,
        name: String,
        type: LedgerEntryType = LedgerEntryType.MANUAL_DUE,
        amount: Long = 10_000,
        entryDate: Long = now,
        phone: String? = null,
        saleId: Long? = null
    ) = KhataEntryRow(
        entry = LedgerEntry(id, 3, type, amount, null, saleId, null, entryDate, entryDate),
        customerName = name, customerPhone = phone, receiptNo = null, saleCancelled = false
    )

    // =====================================================================================
    // What the screen remembers
    // =====================================================================================

    @Test
    fun savedNamesAreReadBackAndUnknownOnesFallBackToTheDefaults() {
        for (p in KhataPeriod.entries) assertEquals(p, periodFromName(p.name))
        assertEquals(KhataPeriod.ALL, periodFromName(null))
        assertEquals(KhataPeriod.ALL, periodFromName("nonsense"))
        for (s in KhataSort.entries) assertEquals(s, sortFromName(s.name))
        assertEquals(KhataSort.LAST_ENTRY_NEWEST, sortFromName(null))
        assertEquals(KhataSort.LAST_ENTRY_NEWEST, sortFromName("nonsense"))
    }

    @Test
    fun aSavedDayRoundTripsAndNoDayMeansNull() {
        assertNull(dayOrNull(NO_DAY))
        assertEquals(today, dayOrNull(today.toEpochDay()))
        assertEquals(LocalDate.of(1970, 1, 1), dayOrNull(0L)) // day 0 is a real day, not "none"
    }

    @Test
    fun theScreenStartsOnSabWithTheNewestFirstSortAndNoFilter() {
        val c = KhataControls()
        assertEquals(KhataPeriod.ALL, c.period)
        assertTrue(c.isAll)
        assertEquals(KhataSort.LAST_ENTRY_NEWEST, c.sort)
        assertFalse(c.onlyOwing)
        assertEquals("", c.query)
        assertFalse(c.searching)
        assertNull(c.customFrom)
        assertNull(c.customTo)
    }

    @Test
    fun aBlankSearchIsNotSearching() {
        assertFalse(KhataControls(query = "   ").searching)
        assertTrue(KhataControls(query = "ক").searching)
        assertFalse(KhataControls(period = KhataPeriod.TODAY).isAll)
    }

    @Test
    fun theChipsAreInTheAgreedOrderAndSabIsFirst() {
        assertEquals(
            listOf(
                KhataPeriod.ALL, KhataPeriod.TODAY, KhataPeriod.LAST_7_DAYS,
                KhataPeriod.THIS_MONTH, KhataPeriod.THIS_YEAR, KhataPeriod.CUSTOM
            ),
            KhataPeriod.entries
        )
    }

    // =====================================================================================
    // The "তারিখ বাছুন" chip and its two pickers
    // =====================================================================================

    @Test
    fun theCustomChipShowsTheChosenRange() {
        assertNull(customRangeText(null, null))
        assertNull(customRangeText(today, null))
        assertNull(customRangeText(null, today))
        assertEquals("০১/১১/২০২৬ – ১২/১১/২০২৬", customRangeText(LocalDate.of(2026, 11, 1), today))
        assertEquals("১২/১১/২০২৬", customRangeText(today, today)) // one day shows one date
    }

    @Test
    fun theFirstPickerOffersNoFutureDay() {
        assertTrue(isSelectableFromDay(dateToPickerMillis(today), today))
        assertTrue(isSelectableFromDay(dateToPickerMillis(today.minusYears(3)), today))
        assertFalse(isSelectableFromDay(dateToPickerMillis(today.plusDays(1)), today))
    }

    @Test
    fun theSecondPickerOffersOnlyFromTheFromDayUpToToday() {
        val from = LocalDate.of(2026, 11, 5)
        assertFalse(isSelectableToDay(dateToPickerMillis(from.minusDays(1)), from, today)) // before "from"
        assertTrue(isSelectableToDay(dateToPickerMillis(from), from, today))               // the same day
        assertTrue(isSelectableToDay(dateToPickerMillis(today), from, today))              // today
        assertFalse(isSelectableToDay(dateToPickerMillis(today.plusDays(1)), from, today)) // future
    }

    @Test
    fun whenFromIsTodayOnlyTodayCanBeTheEnd() {
        assertTrue(isSelectableToDay(dateToPickerMillis(today), today, today))
        assertFalse(isSelectableToDay(dateToPickerMillis(today.minusDays(1)), today, today))
        assertFalse(isSelectableToDay(dateToPickerMillis(today.plusDays(1)), today, today))
    }

    // =====================================================================================
    // The customers: the picker and the address book
    // =====================================================================================

    private val customers = listOf(
        customer(1, "রহিম", balance = 5_000, phone = "01811000000"),
        customer(2, "Karim", phone = "01711000000"),
        customer(3, "আলম", balance = -2_000),
        customer(4, "salma", balance = 100)
    )

    @Test
    fun customersAreAToZIgnoringCaseWhateverTheirBalance() {
        assertEquals(listOf(2L, 4L, 3L, 1L).sorted(), customersAtoZ(customers).map { it.customer.id }.sorted())
        // Latin letters come before Bengali ones; inside a script the order is alphabetical, case ignored.
        assertEquals(listOf(2L, 4L), customersAtoZ(customers).map { it.customer.id }.take(2))
    }

    @Test
    fun equalNamesAreOrderedByIdSoTheListNeverJumps() {
        val twins = listOf(customer(9, "Same"), customer(8, "same"), customer(7, "Abc"))
        assertEquals(listOf(7L, 8L, 9L), customersAtoZ(twins).map { it.customer.id })
    }

    @Test
    fun thePickerAndTheAddressBookSearchNameAndMobile() {
        assertEquals(4, searchedCustomersAtoZ(customers, "").size)
        assertEquals(listOf(1L), searchedCustomersAtoZ(customers, "রহিম").map { it.customer.id })
        assertEquals(listOf(2L), searchedCustomersAtoZ(customers, "karim").map { it.customer.id })
        assertEquals(listOf(2L), searchedCustomersAtoZ(customers, "০১৭১১").map { it.customer.id }) // Bengali digits
        assertEquals(listOf(1L), searchedCustomersAtoZ(customers, "+88018").map { it.customer.id })
        assertTrue(searchedCustomersAtoZ(customers, "zzz").isEmpty())
    }

    @Test
    fun theAddressBookShowsCustomersWithAnyBalance() {
        val ids = searchedCustomersAtoZ(customers, "").map { it.customer.id }.toSet()
        assertEquals(setOf(1L, 2L, 3L, 4L), ids) // due, clear, advance and small due: all shown
    }

    // =====================================================================================
    // The period chips: search and grouping
    // =====================================================================================

    @Test
    fun theSearchOfAPeriodFindsTheCustomerNameOrMobile() {
        val rows = listOf(
            entryRow(1, "করিম", phone = "01711000000"),
            entryRow(2, "রহিম", phone = "01811000000"),
            entryRow(3, "করিম", phone = "01711000000")
        )
        assertEquals(3, searchedEntries(rows, "").size)
        assertEquals(listOf(1L, 3L), searchedEntries(rows, "করিম").map { it.entry.id })
        assertEquals(listOf(2L), searchedEntries(rows, "01811").map { it.entry.id })
        assertEquals(listOf(2L), searchedEntries(rows, "০১৮১১").map { it.entry.id })
        assertTrue(searchedEntries(rows, "zzz").isEmpty())
    }

    @Test
    fun entriesAreGroupedByTheDhakaDayInTheGivenOrder() {
        val day = 24L * 60 * 60 * 1000
        val rows = listOf(
            entryRow(4, "ক", entryDate = now + 60_000),
            entryRow(3, "খ", entryDate = now),
            // 23:50 on 11 Nov in Dhaka = 17:50 UTC the same day: still 11 Nov in Dhaka.
            entryRow(2, "গ", entryDate = Instant.parse("2026-11-11T17:50:00Z").toEpochMilli()),
            entryRow(1, "ঘ", entryDate = now - 5 * day)
        )
        val groups = groupEntriesByDay(rows)
        assertEquals(listOf(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 7)), groups.map { it.date })
        assertEquals(listOf(4L, 3L), groups[0].rows.map { it.entry.id })
        assertTrue(groupEntriesByDay(emptyList()).isEmpty())
    }

    // =====================================================================================
    // The summary box lines
    // =====================================================================================

    private val mixed = periodSummary(
        listOf(
            LedgerEntry(1, 3, LedgerEntryType.SALE_DUE, 30_000, null, 1, null, now, now),
            LedgerEntry(2, 3, LedgerEntryType.MANUAL_DUE, 5_000, null, null, null, now, now),
            LedgerEntry(3, 3, LedgerEntryType.PAYMENT_RECEIVED, 12_000, null, null, null, now, now),
            LedgerEntry(4, 3, LedgerEntryType.CORRECTION_MINUS, 2_000, null, null, null, now, now)
        )
    )

    @Test
    fun eachLineTakesItsValueFromThePeriodSummary() {
        assertEquals(35_000L, summaryAmount(mixed, SummaryLine.DUE_GIVEN))
        assertEquals(12_000L, summaryAmount(mixed, SummaryLine.RECEIVED))
        assertEquals(-2_000L, summaryAmount(mixed, SummaryLine.CORRECTION))
        assertEquals(0L, summaryAmount(mixed, SummaryLine.OPENING_DUE))
        assertEquals(0L, summaryAmount(mixed, SummaryLine.ADVANCE_RETURNED))
        assertEquals(0L, summaryAmount(mixed, SummaryLine.SALE_CANCEL))
        assertEquals(21_000L, summaryAmount(mixed, SummaryLine.NET))
    }

    @Test
    fun onlyTheCorrectionAndTheNetLinesCanShowASign() {
        for (line in SummaryLine.entries) {
            assertEquals(line.name, line == SummaryLine.CORRECTION || line == SummaryLine.NET, isSignedLine(line))
        }
    }

    @Test
    fun theAmountTextHasASignOnlyOnSignedLinesAndNeverForZero() {
        assertEquals("৳ ১২০", summaryAmountText(SummaryLine.DUE_GIVEN, 12_000, "৳", "১২০"))
        assertEquals("৳ ১২০", summaryAmountText(SummaryLine.RECEIVED, 12_000, "৳", "১২০"))
        assertEquals("+ ৳ ২১০", summaryAmountText(SummaryLine.NET, 21_000, "৳", "২১০"))
        assertEquals("− ৳ ২০", summaryAmountText(SummaryLine.CORRECTION, -2_000, "৳", "২০"))
        assertEquals("৳ ০", summaryAmountText(SummaryLine.NET, 0, "৳", "০"))
    }

    // =====================================================================================
    // Empty messages
    // =====================================================================================

    @Test
    fun sabIsEmptyBecauseThereAreNoCustomersOrBecauseNothingMatches() {
        assertNull(khataEmptyKind(KhataPeriod.ALL, true, false, totalCustomers = 5, windowCount = 0, shownCount = 3))
        assertEquals(KhataEmpty.NO_CUSTOMERS, khataEmptyKind(KhataPeriod.ALL, true, false, 0, 0, 0))
        assertEquals(KhataEmpty.NO_MATCH, khataEmptyKind(KhataPeriod.ALL, true, true, 5, 0, 0))
        // The "যাদের বাকি আছে" filter with nobody owing is also "no match" (customers exist).
        assertEquals(KhataEmpty.NO_MATCH, khataEmptyKind(KhataPeriod.ALL, true, false, 5, 0, 0))
    }

    @Test
    fun aPeriodWithNoEntriesSaysSo() {
        assertEquals(KhataEmpty.NO_ENTRIES, khataEmptyKind(KhataPeriod.TODAY, true, false, 5, windowCount = 0, shownCount = 0))
        assertEquals(KhataEmpty.NO_ENTRIES, khataEmptyKind(KhataPeriod.THIS_YEAR, true, true, 5, windowCount = 0, shownCount = 0))
        assertNull(khataEmptyKind(KhataPeriod.TODAY, true, false, 5, windowCount = 4, shownCount = 4))
    }

    @Test
    fun aSearchThatFindsNothingInAPeriodWithEntriesIsNoMatch() {
        assertEquals(KhataEmpty.NO_MATCH, khataEmptyKind(KhataPeriod.LAST_7_DAYS, true, true, 5, windowCount = 4, shownCount = 0))
        assertNull(khataEmptyKind(KhataPeriod.LAST_7_DAYS, true, true, 5, windowCount = 4, shownCount = 1))
    }

    @Test
    fun theCustomChipAsksForDatesUntilBothAreChosen() {
        assertEquals(KhataEmpty.PICK_DATES, khataEmptyKind(KhataPeriod.CUSTOM, rangeReady = false, searching = false, totalCustomers = 5, windowCount = 0, shownCount = 0))
        assertEquals(KhataEmpty.PICK_DATES, khataEmptyKind(KhataPeriod.CUSTOM, rangeReady = false, searching = true, totalCustomers = 5, windowCount = 0, shownCount = 0))
        assertEquals(KhataEmpty.NO_ENTRIES, khataEmptyKind(KhataPeriod.CUSTOM, rangeReady = true, searching = false, totalCustomers = 5, windowCount = 0, shownCount = 0))
    }

    @Test
    fun theAddressBookIsEmptyBecauseOfNoCustomersOrNoMatch() {
        assertNull(contactsEmptyKind(totalCustomers = 3, shownCount = 3))
        assertEquals(KhataEmpty.NO_CUSTOMERS, contactsEmptyKind(0, 0))
        assertEquals(KhataEmpty.NO_MATCH, contactsEmptyKind(3, 0))
    }
}

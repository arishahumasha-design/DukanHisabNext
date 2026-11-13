package com.example.dukanhisabnext.ui.sales

import com.example.dukanhisabnext.data.dao.SaleListRow
import com.example.dukanhisabnext.util.DayPeriod
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesListLogicTest {

    // ---- Helpers ----

    /** A UTC instant in millis. Dhaka is UTC+6: 18:00Z is midnight in Dhaka. */
    private fun utc(text: String): Long = Instant.parse(text).toEpochMilli()

    private fun row(
        id: Long,
        createdAt: Long,
        receiptNo: String = "261110-" + id.toString().padStart(3, '0'), // not String.format: that follows the phone's language
        total: Long = 10_000,
        due: Long = 0,
        name: String? = null,
        phone: String? = null
    ) = SaleListRow(id, receiptNo, createdAt, total, due, name, phone)

    private val months = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )

    // 23:30 on 10 Nov in Dhaka, and 00:10 on 11 Nov in Dhaka: 40 minutes apart, different days.
    private val at2330 = utc("2026-11-10T17:30:00Z")
    private val at0010 = utc("2026-11-10T18:10:00Z")

    // =====================================================================================
    // Grouping by Dhaka day
    // =====================================================================================

    @Test
    fun aSaleAt2330AndOneAt0010AreOnDifferentDays() {
        val groups = groupByDhakaDay(listOf(row(1, at2330), row(2, at0010)))

        assertEquals(2, groups.size)
        assertEquals(LocalDate.of(2026, 11, 11), groups[0].date) // the 00:10 sale: the next Dhaka day, first
        assertEquals(listOf(2L), groups[0].rows.map { it.id })
        assertEquals(LocalDate.of(2026, 11, 10), groups[1].date)
        assertEquals(listOf(1L), groups[1].rows.map { it.id })
    }

    @Test
    fun theLateNightSaleBelongsToThatDhakaDayEvenThoughItIsStillTheSameDayInUtc() {
        // 17:30 UTC on 10 Nov is 23:30 in Dhaka: 10 Nov in both. But 18:10 UTC is 00:10 on 11 Nov in Dhaka,
        // while it is STILL 10 Nov in UTC.
        val group = groupByDhakaDay(listOf(row(1, at0010))).single()
        assertEquals(LocalDate.of(2026, 11, 11), group.date)
    }

    @Test
    fun salesOnTheSameDayShareOneGroupNewestFirst() {
        val rows = listOf(
            row(1, utc("2026-11-10T03:00:00Z")), // 09:00
            row(2, utc("2026-11-10T09:30:00Z")), // 15:30
            row(3, utc("2026-11-10T17:30:00Z"))  // 23:30
        )
        val groups = groupByDhakaDay(rows)
        assertEquals(1, groups.size)
        assertEquals(listOf(3L, 2L, 1L), groups.single().rows.map { it.id })
    }

    @Test
    fun daysAreNewestFirstEvenIfTheInputIsInAnyOrder() {
        val rows = listOf(
            row(1, utc("2026-11-08T06:00:00Z")),
            row(2, utc("2026-11-10T06:00:00Z")),
            row(3, utc("2026-11-09T06:00:00Z")),
            row(4, utc("2026-11-10T07:00:00Z"))
        )
        val groups = groupByDhakaDay(rows)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 8)),
            groups.map { it.date }
        )
        assertEquals(listOf(4L, 2L), groups[0].rows.map { it.id })
    }

    @Test
    fun twoSalesAtTheSameMomentKeepTheNewerIdFirst() {
        val groups = groupByDhakaDay(listOf(row(5, at2330), row(6, at2330)))
        assertEquals(listOf(6L, 5L), groups.single().rows.map { it.id })
    }

    @Test
    fun midnightExactlyStartsTheNewDay() {
        val justBefore = utc("2026-11-10T17:59:59Z") // 23:59:59 on 10 Nov in Dhaka
        val midnight = utc("2026-11-10T18:00:00Z")   // 00:00:00 on 11 Nov in Dhaka
        val groups = groupByDhakaDay(listOf(row(1, justBefore), row(2, midnight)))
        assertEquals(listOf(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 10)), groups.map { it.date })
    }

    @Test
    fun noSalesGiveNoGroups() {
        assertTrue(groupByDhakaDay(emptyList()).isEmpty())
    }

    // ---- Day header and time text ----

    @Test
    fun dayHeaderIsBengaliWithBengaliDigits() {
        assertEquals("১০ নভেম্বর ২০২৬", dayHeaderText(LocalDate.of(2026, 11, 10), months))
        assertEquals("১ জানুয়ারি ২০২৭", dayHeaderText(LocalDate.of(2027, 1, 1), months))
        assertEquals("৩১ ডিসেম্বর ২০২৬", dayHeaderText(LocalDate.of(2026, 12, 31), months))
        assertEquals("২৯ ফেব্রুয়ারি ২০২৮", dayHeaderText(LocalDate.of(2028, 2, 29), months))
    }

    private val period: (DayPeriod) -> String = {
        when (it) {
            DayPeriod.MORNING -> "সকাল"
            DayPeriod.NOON -> "দুপুর"
            DayPeriod.AFTERNOON -> "বিকাল"
            DayPeriod.EVENING -> "সন্ধ্যা"
            DayPeriod.NIGHT -> "রাত"
        }
    }

    @Test
    fun theTimeIsInDhakaTimeWithThePartOfTheDay() {
        assertEquals("রাত ১১:৩০", saleTimeText(at2330, period))
        assertEquals("রাত ১২:১০", saleTimeText(at0010, period))
        assertEquals("সকাল ৯:০৭", saleTimeText(utc("2026-11-10T03:07:00Z"), period))
        assertEquals("দুপুর ১২:০৫", saleTimeText(utc("2026-11-10T06:05:00Z"), period))
    }

    // =====================================================================================
    // The filter ranges: আজ, এই মাস, সব
    // =====================================================================================

    @Test
    fun todayIsTheDhakaDayAroundNow() {
        val range = salesFilterRange(SalesFilter.TODAY, nowMillis = utc("2026-11-10T06:00:00Z")) // noon in Dhaka
        assertEquals(utc("2026-11-09T18:00:00Z"), range.startMillis)            // 00:00 on 10 Nov, Dhaka
        assertEquals(utc("2026-11-10T18:00:00Z"), range.endMillisExclusive)     // 00:00 on 11 Nov, Dhaka
    }

    @Test
    fun at2330InDhakaTodayIsStillThatDay() {
        val range = salesFilterRange(SalesFilter.TODAY, nowMillis = at2330)
        assertEquals(utc("2026-11-09T18:00:00Z"), range.startMillis)
        assertEquals(utc("2026-11-10T18:00:00Z"), range.endMillisExclusive)
        // The 23:30 sale is inside "আজ"; the 00:10 sale (the next Dhaka day) is not.
        assertTrue(at2330 >= range.startMillis && at2330 < range.endMillisExclusive)
        assertFalse(at0010 >= range.startMillis && at0010 < range.endMillisExclusive)
    }

    @Test
    fun at0010InDhakaTodayIsAlreadyTheNextDay() {
        val range = salesFilterRange(SalesFilter.TODAY, nowMillis = at0010)
        assertEquals(utc("2026-11-10T18:00:00Z"), range.startMillis)
        assertEquals(utc("2026-11-11T18:00:00Z"), range.endMillisExclusive)
        assertTrue(at0010 >= range.startMillis && at0010 < range.endMillisExclusive)
        assertFalse(at2330 >= range.startMillis && at2330 < range.endMillisExclusive)
    }

    @Test
    fun thisMonthIsTheDhakaMonthAroundNow() {
        val range = salesFilterRange(SalesFilter.THIS_MONTH, nowMillis = utc("2026-11-10T06:00:00Z"))
        assertEquals(utc("2026-10-31T18:00:00Z"), range.startMillis)        // 00:00 on 1 Nov, Dhaka
        assertEquals(utc("2026-11-30T18:00:00Z"), range.endMillisExclusive) // 00:00 on 1 Dec, Dhaka
    }

    @Test
    fun theMonthBoundaryFollowsDhakaNotUtc() {
        val range = salesFilterRange(SalesFilter.THIS_MONTH, nowMillis = utc("2026-11-10T06:00:00Z"))
        val lastNightOfOctober = utc("2026-10-31T17:30:00Z") // 23:30 on 31 Oct in Dhaka: October
        val firstMinutesOfNovember = utc("2026-10-31T18:10:00Z") // 00:10 on 1 Nov in Dhaka: November
        assertFalse(lastNightOfOctober >= range.startMillis && lastNightOfOctober < range.endMillisExclusive)
        assertTrue(firstMinutesOfNovember >= range.startMillis && firstMinutesOfNovember < range.endMillisExclusive)
    }

    @Test
    fun theLastMinutesOfTheMonthAreStillInIt() {
        val range = salesFilterRange(SalesFilter.THIS_MONTH, nowMillis = utc("2026-11-10T06:00:00Z"))
        val lastMoment = utc("2026-11-30T17:59:59Z")   // 23:59:59 on 30 Nov in Dhaka
        val nextMonth = utc("2026-11-30T18:00:00Z")    // 00:00 on 1 Dec in Dhaka
        assertTrue(lastMoment >= range.startMillis && lastMoment < range.endMillisExclusive)
        assertFalse(nextMonth >= range.startMillis && nextMonth < range.endMillisExclusive) // end is excluded
    }

    @Test
    fun theMonthChangesAtDhakaMidnightEvenWhenUtcIsStillTheOldMonth() {
        // 18:10 UTC on 30 Nov is 00:10 on 1 Dec in Dhaka, so "this month" is already December.
        val range = salesFilterRange(SalesFilter.THIS_MONTH, nowMillis = utc("2026-11-30T18:10:00Z"))
        assertEquals(utc("2026-11-30T18:00:00Z"), range.startMillis)
        assertEquals(utc("2026-12-31T18:00:00Z"), range.endMillisExclusive)
    }

    @Test
    fun theYearEndRollsOverToJanuary() {
        val range = salesFilterRange(SalesFilter.THIS_MONTH, nowMillis = utc("2026-12-31T18:00:00Z")) // 1 Jan 2027, Dhaka
        assertEquals(utc("2026-12-31T18:00:00Z"), range.startMillis)
        assertEquals(utc("2027-01-31T18:00:00Z"), range.endMillisExclusive)
    }

    @Test
    fun allCoversEveryMoment() {
        val range = salesFilterRange(SalesFilter.ALL, nowMillis = utc("2026-11-10T06:00:00Z"))
        for (moment in listOf(0L, utc("2020-01-01T00:00:00Z"), at2330, utc("2099-12-31T00:00:00Z"))) {
            assertTrue(moment >= range.startMillis && moment < range.endMillisExclusive)
        }
    }

    // =====================================================================================
    // Search
    // =====================================================================================

    private val karim = row(1, at2330, receiptNo = "261110-001", name = "করিম মিয়া", phone = "01711000000")
    private val rahim = row(2, at2330, receiptNo = "261110-002", name = "Rahim Khan", phone = "01811000000")
    private val walkIn = row(3, at2330, receiptNo = "261109-017")

    private fun matches(row: SaleListRow, query: String) = matchesSalesQuery(row, query)

    @Test
    fun aBlankSearchMatchesEverything() {
        for (r in listOf(karim, rahim, walkIn)) {
            assertTrue(matches(r, ""))
            assertTrue(matches(r, "   "))
        }
    }

    @Test
    fun searchByReceiptNumberInBengaliDigits() {
        assertTrue(matches(karim, "২৬১১১০-০০১"))
        assertTrue(matches(karim, "২৬১১১০"))
        assertFalse(matches(walkIn, "২৬১১১০")) // 261109-017 is another day
        assertTrue(matches(walkIn, "২৬১১০৯-০১৭"))
    }

    @Test
    fun searchByPartOfTheReceiptNumber() {
        assertTrue(matches(karim, "261110"))
        assertTrue(matches(karim, "1110-0"))
        assertTrue(matches(karim, "001"))
        assertTrue(matches(walkIn, "017"))
        assertFalse(matches(rahim, "017")) // not in rahim's receipt number, name or mobile
        assertFalse(matches(karim, "999"))
    }

    @Test
    fun theReceiptNumberMatchesWithOrWithoutTheDash() {
        assertTrue(matches(karim, "261110001"))
        assertTrue(matches(karim, "261110 001"))
        assertTrue(matches(karim, "২৬১১১০০০১"))
    }

    @Test
    fun searchByPartOfTheCustomerName() {
        assertTrue(matches(karim, "করিম"))
        assertTrue(matches(karim, "মিয়া"))
        assertTrue(matches(karim, "করিম মিয়া"))
        assertFalse(matches(karim, "রহিম"))
    }

    @Test
    fun theNameSearchIgnoresUpperAndLowerCase() {
        assertTrue(matches(rahim, "rahim"))
        assertTrue(matches(rahim, "RAHIM"))
        assertTrue(matches(rahim, "khan"))
        assertTrue(matches(rahim, "ah"))
    }

    @Test
    fun searchByCustomerMobileWithLatinOrBengaliDigits() {
        assertTrue(matches(karim, "01711"))
        assertTrue(matches(karim, "1711000"))
        assertTrue(matches(karim, "০১৭১১"))
        assertTrue(matches(karim, "০১৭১১০০০০০০"))
        assertFalse(matches(karim, "01811"))
        assertTrue(matches(rahim, "01811"))
    }

    @Test
    fun theMobileMayBeTypedWithPlus88OrSpaces() {
        assertTrue(matches(karim, "+8801711000000"))
        assertTrue(matches(karim, "8801711"))
        assertTrue(matches(karim, "01711 000"))
        assertTrue(matches(karim, "01711-000"))
        assertFalse(matches(rahim, "+8801711000000"))
    }

    @Test
    fun aSaleWithoutACustomerIsFoundOnlyByItsReceiptNumber() {
        assertTrue(matches(walkIn, "261109"))
        assertFalse(matches(walkIn, "করিম"))
        assertFalse(matches(walkIn, "01711"))
    }

    @Test
    fun filterSalesKeepsOnlyTheMatchingRows() {
        val all = listOf(karim, rahim, walkIn)
        assertEquals(listOf(1L), filterSales(all, "করিম").map { it.id })
        assertEquals(listOf(1L, 2L), filterSales(all, "261110").map { it.id })
        assertEquals(listOf(1L, 2L, 3L), filterSales(all, "").map { it.id })
        assertTrue(filterSales(all, "zzz").isEmpty())
    }

    // =====================================================================================
    // The line above the list: count and total
    // =====================================================================================

    @Test
    fun theTotalsLineIsTheCountAndTheSumOfTheTotals() {
        val rows = listOf(row(1, at2330, total = 10_000), row(2, at2330, total = 25_050), row(3, at2330, total = 1))
        assertEquals(SalesSummary(count = 3, total = 35_051), salesSummary(rows))
    }

    @Test
    fun noSalesGiveZeroAndZero() {
        assertEquals(SalesSummary(0, 0), salesSummary(emptyList()))
    }

    @Test
    fun theTotalIsTheSaleTotalNotTheMoneyReceived() {
        // A sale of ৳500 with ৳200 still due counts as ৳500.
        assertEquals(50_000L, salesSummary(listOf(row(1, at2330, total = 50_000, due = 20_000))).total)
    }

    @Test
    fun theTotalsLineFollowsTheSearch() {
        val rows = listOf(
            row(1, at2330, total = 10_000, name = "করিম"),
            row(2, at2330, total = 20_000, name = "রহিম"),
            row(3, at2330, total = 5_000, name = "করিম মিয়া")
        )
        val ui = buildSalesListUi(rows, query = "করিম", totalSalesInDb = 3)
        assertEquals(SalesSummary(count = 2, total = 15_000), ui.summary)
    }

    // =====================================================================================
    // Customer label
    // =====================================================================================

    // =====================================================================================
    // Searching ignores the আজ / এই মাস / সব chip (step 5b2)
    // =====================================================================================

    private val noonOn10Nov = utc("2026-11-10T06:00:00Z")
    private val saleToday = row(1, utc("2026-11-10T03:00:00Z"), name = "করিম", total = 10_000)
    private val saleLastWeek = row(2, utc("2026-11-03T03:00:00Z"), name = "করিম মিয়া", total = 20_000)
    private val saleLastMonth = row(3, utc("2026-10-05T03:00:00Z"), name = "রহিম", total = 5_000)
    private val everySale = listOf(saleToday, saleLastWeek, saleLastMonth)

    /**
     * What the screen does: load the window of the EFFECTIVE filter, then search, group and total.
     * (In the app the database cuts the window; here the same cut is done in memory.)
     */
    private fun screen(chip: SalesFilter, query: String): SalesListUi {
        val range = salesFilterRange(effectiveSalesFilter(chip, query), noonOn10Nov)
        val loaded = everySale.filter { it.createdAt >= range.startMillis && it.createdAt < range.endMillisExclusive }
        return buildSalesListUi(loaded, query, totalSalesInDb = everySale.size)
    }

    private fun ids(ui: SalesListUi) = ui.items.filterIsInstance<SalesListItem.Entry>().map { it.row.id }

    @Test
    fun isSearchingOnlyWhenThereIsRealText() {
        assertFalse(isSearching(""))
        assertFalse(isSearching("   "))
        assertTrue(isSearching("করিম"))
        assertTrue(isSearching(" a "))
        assertTrue(isSearching("২৬১১১০"))
    }

    @Test
    fun withoutSearchTextTheChipDecidesTheWindow() {
        for (chip in SalesFilter.entries) {
            assertEquals(chip, effectiveSalesFilter(chip, ""))
            assertEquals(chip, effectiveSalesFilter(chip, "   "))
        }
    }

    @Test
    fun withSearchTextTheWindowIsAlwaysAllWhateverTheChip() {
        for (chip in SalesFilter.entries) {
            assertEquals(SalesFilter.ALL, effectiveSalesFilter(chip, "করিম"))
            assertEquals(SalesFilter.ALL, effectiveSalesFilter(chip, "২৬১১১০"))
            assertEquals(SalesFilter.ALL, effectiveSalesFilter(chip, "x"))
        }
    }

    @Test
    fun theEffectiveWindowWhileSearchingCoversEveryMoment() {
        val range = salesFilterRange(effectiveSalesFilter(SalesFilter.TODAY, "করিম"), noonOn10Nov)
        for (moment in listOf(0L, saleLastMonth.createdAt, saleToday.createdAt, utc("2099-01-01T00:00:00Z"))) {
            assertTrue(moment >= range.startMillis && moment < range.endMillisExclusive)
        }
    }

    @Test
    fun searchFindsOlderSalesEvenWithTheTodayChip() {
        // Without searching, আজ shows only today's sale...
        assertEquals(listOf(1L), ids(screen(SalesFilter.TODAY, "")))
        // ...but searching "করিম" also finds last week's sale.
        assertEquals(listOf(1L, 2L), ids(screen(SalesFilter.TODAY, "করিম")))
    }

    @Test
    fun searchFindsOlderSalesEvenWithTheThisMonthChip() {
        // এই মাস does not include October...
        assertEquals(listOf(1L, 2L), ids(screen(SalesFilter.THIS_MONTH, "")))
        // ...but searching does.
        assertEquals(listOf(3L), ids(screen(SalesFilter.THIS_MONTH, "রহিম")))
    }

    @Test
    fun whileSearchingTheChipMakesNoDifference() {
        val results = SalesFilter.entries.map { ids(screen(it, "করিম")) }
        // Every chip (আজ, ৭ দিন, এই মাস, এই বছর, তারিখ বাছুন, সব) gives the same result while searching.
        assertEquals(List(SalesFilter.entries.size) { listOf(1L, 2L) }, results)
    }

    @Test
    fun clearingTheSearchRestoresTheChipWindow() {
        // The chip stays আজ the whole time: type, then clear.
        assertEquals(listOf(1L), ids(screen(SalesFilter.TODAY, "")))        // before
        assertEquals(listOf(1L, 2L), ids(screen(SalesFilter.TODAY, "করিম"))) // searching: all sales
        assertEquals(listOf(1L), ids(screen(SalesFilter.TODAY, "")))        // cleared: আজ again
    }

    @Test
    fun clearingTheSearchRestoresTheMonthChipToo() {
        assertEquals(listOf(1L, 2L, 3L), ids(screen(SalesFilter.THIS_MONTH, "ম"))) // matches all three names
        assertEquals(listOf(1L, 2L), ids(screen(SalesFilter.THIS_MONTH, "")))      // cleared: Nov only
    }

    @Test
    fun spacesAloneDoNotCountAsSearching() {
        assertEquals(listOf(1L), ids(screen(SalesFilter.TODAY, "   "))) // still আজ
    }

    @Test
    fun theCountAndTotalFollowTheSearchResult() {
        assertEquals(SalesSummary(count = 2, total = 30_000), screen(SalesFilter.TODAY, "করিম").summary)
        assertEquals(SalesSummary(count = 1, total = 10_000), screen(SalesFilter.TODAY, "").summary)
        assertEquals(SalesSummary(count = 3, total = 35_000), screen(SalesFilter.TODAY, "ম").summary)
    }

    @Test
    fun aSearchWithNoResultInAllSalesShowsTheNoMatchMessage() {
        for (chip in SalesFilter.entries) {
            val ui = screen(chip, "zzz")
            assertEquals(EmptyKind.NO_MATCH, ui.empty)
            assertEquals(SalesSummary(0, 0), ui.summary)
        }
    }

    @Test
    fun searchingByReceiptNumberAlsoLooksAtAllSales() {
        val old = row(9, utc("2026-01-15T06:00:00Z"), receiptNo = "260115-004")
        val range = salesFilterRange(effectiveSalesFilter(SalesFilter.TODAY, "260115"), noonOn10Nov)
        val ui = buildSalesListUi(
            listOf(old).filter { it.createdAt >= range.startMillis && it.createdAt < range.endMillisExclusive },
            "260115", totalSalesInDb = 1
        )
        assertEquals(listOf(9L), ui.items.filterIsInstance<SalesListItem.Entry>().map { it.row.id })
    }

    @Test
    fun theChosenChipIsNeverChangedByTheFunction() {
        // The chip is only read, never replaced: after clearing, the same chip is back in force.
        var chip = SalesFilter.THIS_MONTH
        effectiveSalesFilter(chip, "করিম")
        assertEquals(SalesFilter.THIS_MONTH, chip)
        assertEquals(SalesFilter.THIS_MONTH, effectiveSalesFilter(chip, ""))
        chip = SalesFilter.ALL
        assertEquals(SalesFilter.ALL, effectiveSalesFilter(chip, ""))
    }

    @Test
    fun theCustomerLabelIsTheNameOrTheNeutralWord() {
        assertEquals("করিম", customerLabel("করিম", "সাধারণ কাস্টমার"))
        assertEquals("করিম", customerLabel("  করিম ", "সাধারণ কাস্টমার"))
        assertEquals("সাধারণ কাস্টমার", customerLabel(null, "সাধারণ কাস্টমার"))
        assertEquals("সাধারণ কাস্টমার", customerLabel("  ", "সাধারণ কাস্টমার"))
    }

    // =====================================================================================
    // Empty states and the whole screen
    // =====================================================================================

    @Test
    fun emptyStates() {
        assertNull(emptyKind(totalSalesInDb = 5, visibleCount = 2))   // something to show
        assertEquals(EmptyKind.NO_SALES_AT_ALL, emptyKind(totalSalesInDb = 0, visibleCount = 0))
        assertEquals(EmptyKind.NO_MATCH, emptyKind(totalSalesInDb = 5, visibleCount = 0))
    }

    @Test
    fun noSalesAtAllShowsTheFirstEmptyMessage() {
        val ui = buildSalesListUi(emptyList(), query = "", totalSalesInDb = 0)
        assertEquals(EmptyKind.NO_SALES_AT_ALL, ui.empty)
        assertTrue(ui.items.isEmpty())
        assertEquals(SalesSummary(0, 0), ui.summary)
    }

    @Test
    fun noSalesForTheFilterButSalesExistShowsTheNoMatchMessage() {
        // For example "আজ" with nothing today, while older sales exist.
        val ui = buildSalesListUi(emptyList(), query = "", totalSalesInDb = 40)
        assertEquals(EmptyKind.NO_MATCH, ui.empty)
    }

    @Test
    fun aSearchWithNoMatchShowsTheNoMatchMessage() {
        val ui = buildSalesListUi(listOf(karim, rahim), query = "zzz", totalSalesInDb = 2)
        assertEquals(EmptyKind.NO_MATCH, ui.empty)
        assertTrue(ui.items.isEmpty())
    }

    @Test
    fun theListIsAHeaderThenItsSalesForEachDay() {
        val rows = listOf(row(1, at2330), row(2, at0010), row(3, utc("2026-11-09T06:00:00Z")))
        val ui = buildSalesListUi(rows, query = "", totalSalesInDb = 3)

        assertNull(ui.empty)
        assertEquals(
            listOf(
                SalesListItem.Header(LocalDate.of(2026, 11, 11)),
                SalesListItem.Entry(rows[1]),
                SalesListItem.Header(LocalDate.of(2026, 11, 10)),
                SalesListItem.Entry(rows[0]),
                SalesListItem.Header(LocalDate.of(2026, 11, 9)),
                SalesListItem.Entry(rows[2])
            ),
            ui.items
        )
    }

    @Test
    fun everyItemHasAUniqueStableKey() {
        val rows = (1L..50L).map { row(it, utc("2026-11-10T03:00:00Z") + it * 3_600_000L) }
        val keys = buildSalesListUi(rows, "", 50).items.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun aDueIsKeptOnTheRow() {
        val ui = buildSalesListUi(listOf(row(1, at2330, total = 50_000, due = 20_000)), "", 1)
        val entry = ui.items.filterIsInstance<SalesListItem.Entry>().single()
        assertEquals(20_000L, entry.row.dueAmount)
    }

    @Test
    fun thousandsOfSalesAreHandledQuickly() {
        // A smoke test for "fast with thousands": 20,000 sales over 200 days, searched and grouped.
        val base = utc("2026-01-01T00:00:00Z")
        val rows = (1L..20_000L).map { row(it, base + it * 777_777L, name = if (it % 3 == 0L) "করিম" else null) }

        val started = System.nanoTime()
        val ui = buildSalesListUi(rows, query = "করিম", totalSalesInDb = rows.size)
        val millis = (System.nanoTime() - started) / 1_000_000

        assertEquals(6_666, ui.summary.count)
        assertTrue("took $millis ms", millis < 2_000)
    }
}

package com.example.dukanhisabnext.ui.sales

import com.example.dukanhisabnext.data.dao.SaleListRow
import com.example.dukanhisabnext.ledger.dateToPickerMillis
import com.example.dukanhisabnext.ui.components.customRangeLongText
import com.example.dukanhisabnext.ui.components.customRangeText
import com.example.dukanhisabnext.ui.components.isSelectableFromDay
import com.example.dukanhisabnext.ui.components.isSelectableToDay
import com.example.dukanhisabnext.ui.components.toPickerInitial
import com.example.dukanhisabnext.util.DhakaTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sales list's period chips (আজ | ৭ দিন | এই মাস | এই বছর | তারিখ বাছুন | সব), the single-day case of
 * the shared date range logic, and the back-dated rows (version 5, part 2).
 */
class SalesPeriodChipsTest {

    private val months = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )

    /** 13 Nov 2026, 15:20 in Dhaka. */
    private val now = LocalDateTime.of(2026, 11, 13, 15, 20).atZone(DhakaTime.ZONE).toInstant().toEpochMilli()
    private val today = LocalDate.of(2026, 11, 13)

    private fun start(d: LocalDate) = DhakaTime.startOfDay(d)

    // =====================================================================================
    // The six windows
    // =====================================================================================

    @Test
    fun theChipsAreInTheOrderOfTheScreen() {
        assertEquals(
            listOf(SalesFilter.TODAY, SalesFilter.LAST_7_DAYS, SalesFilter.THIS_MONTH, SalesFilter.THIS_YEAR, SalesFilter.CUSTOM, SalesFilter.ALL),
            SalesFilter.entries.toList()
        )
    }

    @Test
    fun todayIsTheDhakaDay() {
        val r = salesFilterRange(SalesFilter.TODAY, now)
        assertEquals(start(today), r.startMillis)
        assertEquals(start(today.plusDays(1)), r.endMillisExclusive)
    }

    @Test
    fun sevenDaysIsTodayAndTheSixDaysBefore() {
        val r = salesFilterRange(SalesFilter.LAST_7_DAYS, now)
        assertEquals(start(LocalDate.of(2026, 11, 7)), r.startMillis)
        assertEquals(start(today.plusDays(1)), r.endMillisExclusive)
    }

    @Test
    fun theMonthAndTheYearAreDhakaBoundaries() {
        val m = salesFilterRange(SalesFilter.THIS_MONTH, now)
        assertEquals(start(LocalDate.of(2026, 11, 1)), m.startMillis)
        assertEquals(start(LocalDate.of(2026, 12, 1)), m.endMillisExclusive)
        val y = salesFilterRange(SalesFilter.THIS_YEAR, now)
        assertEquals(start(LocalDate.of(2026, 1, 1)), y.startMillis)
        assertEquals(start(LocalDate.of(2027, 1, 1)), y.endMillisExclusive)
    }

    @Test
    fun allCoversEveryMoment() {
        val r = salesFilterRange(SalesFilter.ALL, now)
        assertEquals(0L, r.startMillis)
        assertEquals(Long.MAX_VALUE, r.endMillisExclusive)
    }

    // =====================================================================================
    // "তারিখ বাছুন": one day or a range
    // =====================================================================================

    @Test
    fun aSingleDayIsExactlyThatDhakaDay() {
        val day = LocalDate.of(2026, 11, 12)
        val r = salesFilterRange(SalesFilter.CUSTOM, now, day, day)
        assertEquals(start(day), r.startMillis)
        assertEquals(start(day.plusDays(1)), r.endMillisExclusive)
        // A sale at 23:30 Dhaka on that day is inside, one at 00:10 the next day is not.
        val at2330 = Instant.parse("2026-11-12T17:30:00Z").toEpochMilli()
        val nextDay0010 = Instant.parse("2026-11-12T18:10:00Z").toEpochMilli()
        assertTrue(at2330 >= r.startMillis && at2330 < r.endMillisExclusive)
        assertFalse(nextDay0010 >= r.startMillis && nextDay0010 < r.endMillisExclusive)
    }

    @Test
    fun aRangeCoversFromTheStartOfTheFirstDayToTheEndOfTheLast() {
        val r = salesFilterRange(SalesFilter.CUSTOM, now, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 12))
        assertEquals(start(LocalDate.of(2026, 11, 1)), r.startMillis)
        assertEquals(start(LocalDate.of(2026, 11, 13)), r.endMillisExclusive)
    }

    @Test
    fun aBackwardsPairIsSwappedAndAFutureDayBecomesToday() {
        val swapped = salesFilterRange(SalesFilter.CUSTOM, now, LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 1))
        assertEquals(start(LocalDate.of(2026, 11, 1)), swapped.startMillis)
        val future = salesFilterRange(SalesFilter.CUSTOM, now, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 25))
        assertEquals(start(today), future.startMillis)
        assertEquals(start(today.plusDays(1)), future.endMillisExclusive)
    }

    @Test
    fun beforeBothDaysArePickedTheWindowIsEmptyAndTheScreenAsks() {
        val r = salesFilterRange(SalesFilter.CUSTOM, now, null, null)
        assertEquals(0L, r.startMillis)
        assertEquals(0L, r.endMillisExclusive)
        assertTrue(needsDatesPicked(SalesFilter.CUSTOM, null, null))
        assertTrue(needsDatesPicked(SalesFilter.CUSTOM, today, null))
        assertFalse(needsDatesPicked(SalesFilter.CUSTOM, today, today))
        assertFalse(needsDatesPicked(SalesFilter.TODAY, null, null))
    }

    @Test
    fun searchStillSearchesEverySaleWhateverTheChip() {
        for (chip in SalesFilter.entries) {
            assertEquals(SalesFilter.ALL, effectiveSalesFilter(chip, "করিম"))
            assertEquals(chip, effectiveSalesFilter(chip, ""))
        }
    }

    // =====================================================================================
    // The text on the chip
    // =====================================================================================

    @Test
    fun oneDayIsShownAsTheLongDate() {
        val d = LocalDate.of(2026, 11, 12)
        assertEquals("১২ নভেম্বর ২০২৬", customRangeLongText(d, d, months))
    }

    @Test
    fun aRangeInOneYearWritesTheYearOnce() {
        assertEquals(
            "১ নভেম্বর – ১২ নভেম্বর ২০২৬",
            customRangeLongText(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 12), months)
        )
    }

    @Test
    fun aRangeAcrossTwoYearsWritesBothYears() {
        assertEquals(
            "২৫ ডিসেম্বর ২০২৫ – ৩ জানুয়ারি ২০২৬",
            customRangeLongText(LocalDate.of(2025, 12, 25), LocalDate.of(2026, 1, 3), months)
        )
    }

    @Test
    fun noRangeNoText() {
        assertNull(customRangeLongText(null, null, months))
        assertNull(customRangeLongText(today, null, months))
        assertNull(customRangeLongText(null, today, months))
    }

    // =====================================================================================
    // The single-day case of the shared range logic
    // =====================================================================================

    @Test
    fun theSharedChipTextAlsoMakesOneDayFromTwoEqualDays() {
        val d = LocalDate.of(2026, 11, 12)
        assertEquals(customRangeText(d, d), customRangeText(d, d)?.takeIf { !it.contains("–") })
    }

    @Test
    fun theSecondPickerOffersTheFromDayItselfSoOneDayCanBeChosen() {
        val from = LocalDate.of(2026, 11, 12)
        assertTrue(isSelectableToDay(dateToPickerMillis(from), from, today))               // the same day: allowed
        assertFalse(isSelectableToDay(dateToPickerMillis(from.minusDays(1)), from, today)) // before "from": not
        assertFalse(isSelectableToDay(dateToPickerMillis(today.plusDays(1)), from, today)) // the future: not
        assertTrue(isSelectableFromDay(dateToPickerMillis(today), today))
    }

    @Test
    fun theSecondPickerOpensOnTheFromDayWhenNothingIsChosenYet() {
        val from = LocalDate.of(2026, 11, 12)
        assertEquals(from, toPickerInitial(from, null))
        assertEquals(from, toPickerInitial(from, from))
    }

    // =====================================================================================
    // Back-dated rows show the date only
    // =====================================================================================

    private fun row(createdAt: Long, saleDate: Long) =
        SaleListRow(1, "261110-001", createdAt, 10_000, 0, null, null, null, saleDate)

    @Test
    fun aNormalRowShowsTheReceiptNumberAndTheTime() {
        val t = Instant.parse("2026-11-10T14:00:00Z").toEpochMilli()
        val r = row(t, t)
        assertFalse(r.isBackDated())
        assertEquals("২৬১১১০-০০১  ·  রাত ৮:০০", saleRowTitle(r, "রাত ৮:০০"))
    }

    @Test
    fun aBackDatedRowShowsTheReceiptNumberOnly() {
        val saved = Instant.parse("2026-11-13T09:05:00Z").toEpochMilli()
        val sold = Instant.parse("2026-11-10T09:05:00Z").toEpochMilli()
        val r = row(saved, sold)
        assertTrue(r.isBackDated())
        assertEquals("২৬১১১০-০০১", saleRowTitle(r, "সকাল ১১:০৫")) // no time
    }

    @Test
    fun aRowWithoutASaleDateTakesItsCreationTime() {
        val t = Instant.parse("2026-11-10T14:00:00Z").toEpochMilli()
        assertFalse(SaleListRow(1, "x", t, 0, 0, null, null).isBackDated())
    }

    @Test
    fun theListGroupsABackDatedSaleUnderItsOwnDay() {
        val saved = Instant.parse("2026-11-13T09:05:00Z").toEpochMilli()
        val sold = Instant.parse("2026-11-10T09:05:00Z").toEpochMilli()
        val today = Instant.parse("2026-11-13T10:00:00Z").toEpochMilli()
        val groups = groupByDhakaDay(
            listOf(
                SaleListRow(1, "261113-001", today, 100, 0, null, null, null, today),
                SaleListRow(2, "261110-002", saved, 100, 0, null, null, null, sold)
            )
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 10)), groups.map { it.date })
        assertEquals(listOf(2L), groups[1].rows.map { it.id })
    }
}

package com.example.dukanhisabnext.ui.components

import com.example.dukanhisabnext.ledger.dateToPickerMillis
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules of the two-step "তারিখ বাছুন" picker, shared by the Khata tab and the expense list. These
 * tests pin down the behaviour the Khata tab had before the picker was moved into a shared component.
 */
class DateRangeLogicTest {

    private val today = LocalDate.of(2026, 11, 12)

    @Test
    fun theChipShowsTheChosenRange() {
        assertNull(customRangeText(null, null))
        assertNull(customRangeText(today, null))
        assertNull(customRangeText(null, today))
        assertEquals("০১/১১/২০২৬ – ১২/১১/২০২৬", customRangeText(LocalDate.of(2026, 11, 1), today))
        assertEquals("১২/১১/২০২৬", customRangeText(today, today))
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
        assertFalse(isSelectableToDay(dateToPickerMillis(from.minusDays(1)), from, today))
        assertTrue(isSelectableToDay(dateToPickerMillis(from), from, today))
        assertTrue(isSelectableToDay(dateToPickerMillis(today), from, today))
        assertFalse(isSelectableToDay(dateToPickerMillis(today.plusDays(1)), from, today))
    }

    @Test
    fun theFirstPickerOpensOnTheChosenFromDayOrToday() {
        assertEquals(today, fromPickerInitial(null, today))
        assertEquals(LocalDate.of(2026, 10, 1), fromPickerInitial(LocalDate.of(2026, 10, 1), today))
    }

    @Test
    fun theSecondPickerOpensOnTheChosenToDayOrTheFromDayButNeverBeforeFrom() {
        val from = LocalDate.of(2026, 11, 5)
        assertEquals(from, toPickerInitial(from, null))                             // nothing chosen yet: the "from" day
        assertEquals(LocalDate.of(2026, 11, 9), toPickerInitial(from, LocalDate.of(2026, 11, 9)))
        assertEquals(from, toPickerInitial(from, LocalDate.of(2026, 11, 1)))          // an older "to" is lifted to "from"
        assertEquals(from, toPickerInitial(from, from))
    }

    @Test
    fun pickerStartIsTheUtcMidnightOfTheDay() {
        assertEquals(dateToPickerMillis(today), pickerStartFor(today))
    }
}

package com.example.dukanhisabnext.warranty

import com.example.dukanhisabnext.data.entity.WarrantyUnit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WarrantyRulesTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    // ---- the limits ----

    @Test
    fun noWarrantyIsBothNull() {
        assertNull(checkWarranty(null, null))
    }

    @Test
    fun onlyOneHalfIsIncomplete() {
        assertEquals(WarrantyProblem.INCOMPLETE, checkWarranty(6, null))
        assertEquals(WarrantyProblem.INCOMPLETE, checkWarranty(null, WarrantyUnit.MONTH))
    }

    @Test
    fun theLimitsPerUnitAreInclusive() {
        assertNull(checkWarranty(1, WarrantyUnit.DAY))
        assertNull(checkWarranty(999, WarrantyUnit.DAY))
        assertEquals(WarrantyProblem.OUT_OF_RANGE, checkWarranty(1000, WarrantyUnit.DAY))
        assertNull(checkWarranty(120, WarrantyUnit.MONTH))
        assertEquals(WarrantyProblem.OUT_OF_RANGE, checkWarranty(121, WarrantyUnit.MONTH))
        assertNull(checkWarranty(50, WarrantyUnit.YEAR))
        assertEquals(WarrantyProblem.OUT_OF_RANGE, checkWarranty(51, WarrantyUnit.YEAR))
    }

    @Test
    fun zeroAndNegativeAreRefused() {
        for (unit in WarrantyUnit.entries) {
            assertEquals(WarrantyProblem.OUT_OF_RANGE, checkWarranty(0, unit))
            assertEquals(WarrantyProblem.OUT_OF_RANGE, checkWarranty(-1, unit))
        }
    }

    // ---- the last day, on the calendar ----

    @Test
    fun sixMonthsFromThirteenNovember() {
        assertEquals(d(2027, 5, 13), warrantyUntil(d(2026, 11, 13), 6, WarrantyUnit.MONTH))
    }

    @Test
    fun daysCrossMonthAndYearEnds() {
        assertEquals(d(2027, 1, 6), warrantyUntil(d(2026, 12, 30), 7, WarrantyUnit.DAY))
        assertEquals(d(2026, 3, 1), warrantyUntil(d(2026, 2, 28), 1, WarrantyUnit.DAY))
        assertEquals(d(2028, 2, 29), warrantyUntil(d(2028, 2, 28), 1, WarrantyUnit.DAY))
        assertEquals(d(2026, 12, 14).plusDays(999), warrantyUntil(d(2026, 12, 14), 999, WarrantyUnit.DAY))
    }

    @Test
    fun aMonthFromTheThirtyFirstIsTheLastDayOfTheNextMonth() {
        assertEquals(d(2027, 2, 28), warrantyUntil(d(2027, 1, 31), 1, WarrantyUnit.MONTH))
        assertEquals(d(2028, 2, 29), warrantyUntil(d(2028, 1, 31), 1, WarrantyUnit.MONTH)) // leap year
        assertEquals(d(2027, 4, 30), warrantyUntil(d(2027, 3, 31), 1, WarrantyUnit.MONTH))
        assertEquals(d(2027, 2, 28), warrantyUntil(d(2026, 8, 31), 6, WarrantyUnit.MONTH))
    }

    @Test
    fun monthsAreCalendarMonthsNotThirtyDays() {
        // 1 Jan + 1 month = 1 Feb (31 days later); 1 Feb + 1 month = 1 Mar (28 days later).
        assertEquals(d(2027, 2, 1), warrantyUntil(d(2027, 1, 1), 1, WarrantyUnit.MONTH))
        assertEquals(d(2027, 3, 1), warrantyUntil(d(2027, 2, 1), 1, WarrantyUnit.MONTH))
        assertEquals(d(2036, 11, 13), warrantyUntil(d(2026, 11, 13), 120, WarrantyUnit.MONTH))
    }

    @Test
    fun aYearFromTwentyNinthFebruaryIsTwentyEighthFebruary() {
        assertEquals(d(2029, 2, 28), warrantyUntil(d(2028, 2, 29), 1, WarrantyUnit.YEAR))
        assertEquals(d(2032, 2, 29), warrantyUntil(d(2028, 2, 29), 4, WarrantyUnit.YEAR)) // a leap year again
        assertEquals(d(2027, 11, 13), warrantyUntil(d(2026, 11, 13), 1, WarrantyUnit.YEAR))
        assertEquals(d(2076, 11, 13), warrantyUntil(d(2026, 11, 13), 50, WarrantyUnit.YEAR))
    }

    @Test(expected = IllegalArgumentException::class)
    fun anUncheckedWarrantyIsNeverWorkedOut() {
        warrantyUntil(d(2026, 11, 13), 0, WarrantyUnit.MONTH)
    }
}

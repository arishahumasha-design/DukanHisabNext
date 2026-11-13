package com.example.dukanhisabnext.warranty

import com.example.dukanhisabnext.data.entity.WarrantyUnit
import com.example.dukanhisabnext.ui.products.FormError
import com.example.dukanhisabnext.ui.products.validateWarrantyInput
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WarrantyTextTest {

    private val months = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )
    private val unitName: (WarrantyUnit) -> String = {
        when (it) {
            WarrantyUnit.DAY -> "দিন"
            WarrantyUnit.MONTH -> "মাস"
            WarrantyUnit.YEAR -> "বছর"
        }
    }

    @Test
    fun thePeriodIsBengaliDigitsAndTheUnitName() {
        assertEquals("৬ মাস", warrantyPeriodText(6, WarrantyUnit.MONTH, unitName))
        assertEquals("১০ দিন", warrantyPeriodText(10, WarrantyUnit.DAY, unitName))
        assertEquals("১২০ মাস", warrantyPeriodText(120, WarrantyUnit.MONTH, unitName))
        assertEquals("১ বছর", warrantyPeriodText(1, WarrantyUnit.YEAR, unitName))
    }

    @Test
    fun theDayIsDayMonthNameYear() {
        assertEquals("১৩ মে ২০২৭", warrantyDayText(LocalDate.of(2027, 5, 13), months))
        assertEquals("১ জানুয়ারি ২০২৭", warrantyDayText(LocalDate.of(2027, 1, 1), months))
        assertEquals("২৯ ফেব্রুয়ারি ২০২৮", warrantyDayText(LocalDate.of(2028, 2, 29), months))
        assertEquals("১৩ মে ২০২৭", warrantyUntilDayText(LocalDate.of(2027, 5, 13).toEpochDay(), months))
    }

    // ---- the live preview of the cart's warranty dialog ----

    @Test
    fun theLivePreviewIsTheLastCoveredDay() {
        // The example of the plan: sold 13 Nov 2026 with 6 months.
        assertEquals("১৩ মে ২০২৭", warrantyPreviewDay(LocalDate.of(2026, 11, 13), 6, WarrantyUnit.MONTH, months))
        assertEquals("২৮ ফেব্রুয়ারি ২০২৭", warrantyPreviewDay(LocalDate.of(2027, 1, 31), 1, WarrantyUnit.MONTH, months)) // month end
        assertEquals("২০ নভেম্বর ২০২৬", warrantyPreviewDay(LocalDate.of(2026, 11, 13), 7, WarrantyUnit.DAY, months))
    }

    @Test
    fun theLivePreviewFollowsTheSaleDay() {
        // A back-dated sale counts from its own day.
        assertEquals("১০ মে ২০২৭", warrantyPreviewDay(LocalDate.of(2026, 11, 10), 6, WarrantyUnit.MONTH, months))
    }

    @Test
    fun thereIsNoPreviewForAnEmptyOrOutOfRangeNumber() {
        val day = LocalDate.of(2026, 11, 13)
        assertNull(warrantyPreviewDay(day, null, WarrantyUnit.MONTH, months))
        assertNull(warrantyPreviewDay(day, 6, null, months))
        assertNull(warrantyPreviewDay(day, 0, WarrantyUnit.MONTH, months))
        assertNull(warrantyPreviewDay(day, 121, WarrantyUnit.MONTH, months))
        assertNull(warrantyPreviewDay(day, 1000, WarrantyUnit.DAY, months))
    }

    // ---- the number box ----

    @Test
    fun theBoxKeepsOnlyUpToThreeDigitsAndAcceptsBengaliDigits() {
        assertEquals("12", sanitizeWarrantyInput("১২"))
        assertEquals("999", sanitizeWarrantyInput("9999"))
        assertEquals("65", sanitizeWarrantyInput("6.5")) // a decimal point is dropped, not read as a fraction
        assertEquals("", sanitizeWarrantyInput("abc-"))
        assertEquals(12, parseWarrantyInput("১২"))
        assertNull(parseWarrantyInput(""))
        assertNull(parseWarrantyInput("x"))
    }

    // ---- the product form's check ----

    @Test
    fun theSwitchOffMeansNoWarrantyAndChecksNothing() {
        val r = validateWarrantyInput(false, "999999", WarrantyUnit.DAY)
        assertNull(r.value)
        assertNull(r.unit)
        assertNull(r.error)
    }

    @Test
    fun theSwitchOnNeedsAWholeNumberWithinTheLimit() {
        assertEquals(FormError.REQUIRED, validateWarrantyInput(true, "", WarrantyUnit.MONTH).error)
        assertEquals(FormError.REQUIRED, validateWarrantyInput(true, "  ", WarrantyUnit.MONTH).error)
        assertEquals(FormError.WARRANTY_OUT_OF_RANGE, validateWarrantyInput(true, "0", WarrantyUnit.MONTH).error)
        assertEquals(FormError.WARRANTY_OUT_OF_RANGE, validateWarrantyInput(true, "121", WarrantyUnit.MONTH).error)
        assertEquals(FormError.WARRANTY_OUT_OF_RANGE, validateWarrantyInput(true, "51", WarrantyUnit.YEAR).error)
        val ok = validateWarrantyInput(true, "6", WarrantyUnit.MONTH)
        assertEquals(6, ok.value)
        assertEquals(WarrantyUnit.MONTH, ok.unit)
        assertNull(ok.error)
        assertEquals(999, validateWarrantyInput(true, "999", WarrantyUnit.DAY).value)
    }

    @Test
    fun theSameNumberCanBeFineInOneUnitAndTooBigInAnother() {
        assertEquals(100, validateWarrantyInput(true, "100", WarrantyUnit.DAY).value)
        assertEquals(FormError.WARRANTY_OUT_OF_RANGE, validateWarrantyInput(true, "100", WarrantyUnit.YEAR).error)
    }
}

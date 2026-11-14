package com.example.dukanhisabnext.util

import com.example.dukanhisabnext.ui.sales.dayHeaderText
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Digits, money, dates, times and the language choice, in Bengali and in English. */
class LanguageFormattingTest {

    @After
    fun backToDefaults() {
        CurrentLanguage.value = AppLanguage.BENGALI
        CurrentCurrency.value = AppCurrency.DEFAULT
    }

    private fun english() { CurrentLanguage.value = AppLanguage.ENGLISH }

    private val bnMonths = listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")
    private val enMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    // 23:45 on 14 Nov 2026 in Dhaka (= 17:45 UTC); and 02:30 on 15 Nov in Dhaka.
    private val evening = Instant.parse("2026-11-14T17:45:00Z").toEpochMilli()
    private val smallHours = Instant.parse("2026-11-14T20:30:00Z").toEpochMilli()
    private val noonish = Instant.parse("2026-11-14T06:09:00Z").toEpochMilli() // 12:09 in Dhaka

    // ---- digits ----

    @Test
    fun bengaliShowsBengaliDigitsAndEnglishShowsWesternDigits() {
        assertEquals("১,৫০০.৫০", localDigits("1,500.50"))
        english()
        assertEquals("1,500.50", localDigits("1,500.50"))
    }

    @Test
    fun englishAlsoTurnsBengaliDigitsIntoWesternOnes() {
        english()
        assertEquals("017", localDigits("০১৭"))
        assertEquals("12/11/2026", localDigits("১২/১১/২০২৬"))
    }

    @Test
    fun bengaliTurnsWesternDigitsIntoBengaliOnesAndLeavesLettersAlone() {
        assertEquals("রসিদ ২৬১১১২-০০১", localDigits("রসিদ 261112-001"))
    }

    // ---- money and quantity ----

    @Test
    fun moneyInBengali() {
        assertEquals("১,৫০০", formatTaka(150_000))
        assertEquals("১,২৩,৪৫৬", formatTaka(12_345_600))
        assertEquals("১,৫০০.৫০", formatTaka(150_050))
        assertEquals("০", formatTaka(0))
    }

    @Test
    fun moneyInEnglishKeepsTheLakhGroupingOfTheTaka() {
        english()
        assertEquals("1,500", formatTaka(150_000))
        assertEquals("1,23,456", formatTaka(12_345_600))
        assertEquals("1,500.50", formatTaka(150_050))
        assertEquals("0", formatTaka(0))
        assertEquals("-1,500", formatTaka(-150_000))
    }

    @Test
    fun quantityInBothLanguages() {
        assertEquals("২.৫", formatQuantity(2_500))
        assertEquals("০.২৫", formatQuantity(250))
        english()
        assertEquals("2.5", formatQuantity(2_500))
        assertEquals("0.25", formatQuantity(250))
        assertEquals("12", formatQuantity(12_000))
    }

    @Test
    fun typedInputIsAcceptedInBothDigitStylesInBothLanguages() {
        for (language in AppLanguage.entries) {
            CurrentLanguage.value = language
            assertEquals(120_000L, parseTakaToPaisa("1,200"))
            assertEquals(120_000L, parseTakaToPaisa("১,২০০"))
            assertEquals(2_500L, parseQuantity("২.৫"))
            assertEquals(2_500L, parseQuantity("2.5"))
            assertEquals("01712345678", toLatinDigits("০১৭১২৩৪৫৬৭৮"))
        }
    }

    // ---- dates ----

    @Test
    fun dateInBothLanguages() {
        assertEquals("১৪/১১/২০২৬", formatDate(evening))
        english()
        assertEquals("14 Nov 2026", formatDate(evening))
    }

    @Test
    fun dayHeaderShowsTheMonthName() {
        val day = LocalDate.of(2026, 11, 14)
        assertEquals("১৪ নভেম্বর ২০২৬", dayHeaderText(day, bnMonths))
        english()
        assertEquals("14 Nov 2026", dayHeaderText(day, enMonths))
    }

    @Test
    fun timeOfDayBengaliPartOfDayFirstEnglishAmPmAfter() {
        val words = mapOf(
            DayPeriod.MORNING to "সকাল", DayPeriod.NOON to "দুপুর", DayPeriod.AFTERNOON to "বিকাল",
            DayPeriod.EVENING to "সন্ধ্যা", DayPeriod.NIGHT to "রাত"
        )
        val bn: (DayPeriod) -> String = { words.getValue(it) }
        assertEquals("রাত ১১:৪৫", clockText(evening, bn))
        assertEquals("রাত ২:৩০", clockText(smallHours, bn))
        assertEquals("দুপুর ১২:০৯", clockText(noonish, bn))

        english()
        val en: (DayPeriod) -> String = { if (it == DayPeriod.MORNING) "am" else "pm" }
        assertEquals("11:45 pm", clockText(evening, en))
        assertEquals("2:30 am", clockText(smallHours, en))
        assertEquals("12:09 pm", clockText(noonish, en))
    }

    @Test
    fun warrantyPeriodSaysOneYearNotOneYears() {
        val many: (com.example.dukanhisabnext.data.entity.WarrantyUnit) -> String = { "years" }
        val one: (com.example.dukanhisabnext.data.entity.WarrantyUnit) -> String = { "year" }
        val unit = com.example.dukanhisabnext.data.entity.WarrantyUnit.YEAR
        english()
        assertEquals("1 year", com.example.dukanhisabnext.warranty.warrantyPeriodText(1, unit, many, one))
        assertEquals("2 years", com.example.dukanhisabnext.warranty.warrantyPeriodText(2, unit, many, one))
        assertEquals("1 years", com.example.dukanhisabnext.warranty.warrantyPeriodText(1, unit, many)) // no singular given: the same name
        CurrentLanguage.value = AppLanguage.BENGALI
        assertEquals("১ বছর", com.example.dukanhisabnext.warranty.warrantyPeriodText(1, unit, { "বছর" }, { "বছর" }))
    }

    // ---- the language choice ----

    @Test
    fun languageTags() {
        assertEquals(AppLanguage.BENGALI, languageFromTag("bn"))
        assertEquals(AppLanguage.BENGALI, languageFromTag("bn-BD"))
        assertEquals(AppLanguage.ENGLISH, languageFromTag("en"))
        assertEquals(AppLanguage.ENGLISH, languageFromTag("en-US"))
        assertEquals(AppLanguage.ENGLISH, languageFromTag("EN_gb"))
        assertNull(languageFromTag("fr"))
        assertNull(languageFromTag(""))
        assertNull(languageFromTag(null))
    }

    @Test
    fun theSystemSettingWinsThenTheSavedChoiceThenBengali() {
        assertEquals(AppLanguage.ENGLISH, resolveLanguage("en", AppLanguage.BENGALI))
        assertEquals(AppLanguage.ENGLISH, resolveLanguage(null, AppLanguage.ENGLISH))
        assertEquals(AppLanguage.BENGALI, resolveLanguage(null, null))
        assertEquals(AppLanguage.BENGALI, resolveLanguage("fr", null)) // an unknown language is ignored
        assertEquals(AppLanguage.ENGLISH, resolveLanguage("fr", AppLanguage.ENGLISH))
    }

    private class FakeStore(var language: AppLanguage? = null, var currency: AppCurrency? = null) : DisplayStore {
        override fun loadLanguage() = language
        override fun saveLanguage(language: AppLanguage) { this.language = language }
        override fun loadCurrency() = currency
        override fun saveCurrency(currency: AppCurrency) { this.currency = currency }
    }

    @Test
    fun aNewInstallStartsInBengaliWithTakaAndSavesTheLanguage() {
        val store = FakeStore()
        val settings = DisplaySettings(store, systemTag = { null })
        assertEquals(AppLanguage.BENGALI, settings.resolveAtStart())
        assertEquals(AppLanguage.BENGALI, CurrentLanguage.value)
        assertEquals(AppCurrency.BDT, CurrentCurrency.value)
        assertEquals(AppLanguage.BENGALI, store.language)
    }

    @Test
    fun theChoiceSurvivesARestart() {
        val store = FakeStore()
        DisplaySettings(store, { null }).apply {
            resolveAtStart()
            chooseLanguage(AppLanguage.ENGLISH)
            chooseCurrency(AppCurrency.INR)
        }
        assertEquals(AppLanguage.ENGLISH, CurrentLanguage.value)
        // A fresh start (a new manager over the same store) reads both choices again.
        CurrentLanguage.value = AppLanguage.BENGALI
        CurrentCurrency.value = AppCurrency.BDT
        assertEquals(AppLanguage.ENGLISH, DisplaySettings(store, { null }).resolveAtStart())
        assertEquals(AppLanguage.ENGLISH, CurrentLanguage.value)
        assertEquals(AppCurrency.INR, CurrentCurrency.value)
    }

    @Test
    fun theSystemsPerAppLanguageIsAdoptedAndSaved() {
        val store = FakeStore(language = AppLanguage.BENGALI)
        val settings = DisplaySettings(store, systemTag = { "en" })
        assertEquals(AppLanguage.ENGLISH, settings.resolveAtStart())
        assertEquals(AppLanguage.ENGLISH, store.language)
    }

    @Test
    fun everyLanguageHasALocale() {
        assertEquals("bn", AppLanguage.BENGALI.locale.language)
        assertEquals("en", AppLanguage.ENGLISH.locale.language)
        assertTrue(AppLanguage.BENGALI.usesBengaliDigits && !AppLanguage.ENGLISH.usesBengaliDigits)
    }
}

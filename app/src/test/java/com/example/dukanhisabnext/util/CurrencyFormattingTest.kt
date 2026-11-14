package com.example.dukanhisabnext.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The currency is display only: the symbol and the grouping change, the number never does.
 * Every currency of the list, in both languages.
 */
class CurrencyFormattingTest {

    @After
    fun backToDefaults() {
        CurrentLanguage.value = AppLanguage.BENGALI
        CurrentCurrency.value = AppCurrency.DEFAULT
    }

    /** What an amount of 123,456,789 paisa (1234567.89) looks like in each currency, with Western digits. */
    private val big = mapOf(
        AppCurrency.BDT to "৳ 12,34,567.89",
        AppCurrency.INR to "₹ 12,34,567.89",
        AppCurrency.PKR to "Rs 12,34,567.89",
        AppCurrency.NPR to "Rs 12,34,567.89",
        AppCurrency.USD to "$ 1,234,567.89",
        AppCurrency.EUR to "€ 1,234,567.89",
        AppCurrency.GBP to "£ 1,234,567.89",
        AppCurrency.SAR to "SAR 1,234,567.89",
        AppCurrency.AED to "AED 1,234,567.89",
        AppCurrency.QAR to "QAR 1,234,567.89",
        AppCurrency.KWD to "KWD 1,234,567.89",
        AppCurrency.OMR to "OMR 1,234,567.89",
        AppCurrency.MYR to "RM 1,234,567.89",
        AppCurrency.SGD to "S$ 1,234,567.89",
        AppCurrency.CAD to "C$ 1,234,567.89",
        AppCurrency.AUD to "A$ 1,234,567.89"
    )

    private fun shown(currency: AppCurrency, paisa: Long): String {
        CurrentCurrency.value = currency
        return "${currency.symbol} ${formatTaka(paisa)}"
    }

    @Test
    fun theListHasEveryCurrencyWeTestHere() {
        assertEquals(AppCurrency.entries.toSet(), big.keys)
        assertEquals(16, AppCurrency.entries.size)
        assertEquals(AppCurrency.BDT, AppCurrency.DEFAULT)
        assertEquals(listOf("৳", "₹"), listOf(AppCurrency.BDT.symbol, AppCurrency.INR.symbol))
    }

    @Test
    fun everyCurrencyInEnglish() {
        CurrentLanguage.value = AppLanguage.ENGLISH
        for ((currency, expected) in big) assertEquals(currency.code, expected, shown(currency, 123_456_789))
    }

    @Test
    fun everyCurrencyInBengaliOnlyChangesTheDigits() {
        CurrentLanguage.value = AppLanguage.BENGALI
        for ((currency, expected) in big) {
            val digits = expected.substringAfter(' ')
            assertEquals(currency.code, "${currency.symbol} ${toBengaliDigits(digits)}", shown(currency, 123_456_789))
        }
    }

    @Test
    fun lakhGroupingOnlyForTakaAndTheRupees() {
        val lakh = AppCurrency.entries.filter { it.lakhGrouping }.map { it.code }
        assertEquals(listOf("BDT", "INR", "PKR", "NPR"), lakh)
        CurrentLanguage.value = AppLanguage.ENGLISH
        CurrentCurrency.value = AppCurrency.INR
        assertEquals("1,00,000", formatTaka(10_000_000))
        CurrentCurrency.value = AppCurrency.USD
        assertEquals("100,000", formatTaka(10_000_000))
        CurrentCurrency.value = AppCurrency.EUR
        assertEquals("100,000", formatTaka(10_000_000))
        CurrentCurrency.value = AppCurrency.GBP
        assertEquals("100,000", formatTaka(10_000_000))
    }

    @Test
    fun smallAmountsNeedNoGrouping() {
        CurrentLanguage.value = AppLanguage.ENGLISH
        for (currency in AppCurrency.entries) {
            assertEquals("999", shown(currency, 99_900).substringAfter(' '))
            assertEquals("1,000", shown(currency, 100_000).substringAfter(' '))
            assertEquals("0.05", shown(currency, 5).substringAfter(' '))
        }
    }

    @Test
    fun theAmountItselfNeverChanges() {
        // The stored number is the same whatever is shown: only the text around it differs.
        CurrentLanguage.value = AppLanguage.ENGLISH
        val digitsOnly = AppCurrency.entries.map { shown(it, 150_050).substringAfter(' ').replace(",", "") }.toSet()
        assertEquals(setOf("1500.50"), digitsOnly)
        // And what the user types is read the same way in every currency.
        for (currency in AppCurrency.entries) {
            CurrentCurrency.value = currency
            assertEquals(150_050L, parseTakaToPaisa("1500.50"))
            assertEquals(150_050L, parseTakaToPaisa("১,৫০০.৫০"))
        }
    }

    @Test
    fun aTextFromStringsXmlShowsTheChosenSymbol() {
        CurrentCurrency.value = AppCurrency.INR
        assertEquals("Price (₹)", "Price (৳)".withCurrency())
        assertEquals("Due ₹ 50", "Due ৳ 50".withCurrency())
        CurrentCurrency.value = AppCurrency.USD
        assertEquals("Due $ 50", "Due ৳ 50".withCurrency())
        CurrentCurrency.value = AppCurrency.BDT
        assertEquals("Due ৳ 50", "Due ৳ 50".withCurrency())
    }

    @Test
    fun theLargestAmountTokenFollowsTheCurrencyAndTheLanguage() {
        CurrentLanguage.value = AppLanguage.ENGLISH
        CurrentCurrency.value = AppCurrency.BDT
        assertEquals("At most ৳ 1,00,00,000", "At most {max}".withCurrency())
        CurrentCurrency.value = AppCurrency.USD
        assertEquals("At most $ 10,000,000", "At most {max}".withCurrency())
        CurrentLanguage.value = AppLanguage.BENGALI
        CurrentCurrency.value = AppCurrency.INR
        assertEquals("সর্বোচ্চ ₹ ১,০০,০০,০০০", "সর্বোচ্চ {max}".withCurrency())
        assertEquals(1_000_000_000L, MAX_AMOUNT_PAISA)
    }

    @Test
    fun theSampleInTheWarningDialogShowsBothStyles() {
        CurrentLanguage.value = AppLanguage.ENGLISH
        assertEquals("৳ 1,50,000", sampleMoney(AppCurrency.BDT))
        assertEquals("₹ 1,50,000", sampleMoney(AppCurrency.INR))
        assertEquals("$ 150,000", sampleMoney(AppCurrency.USD))
        CurrentLanguage.value = AppLanguage.BENGALI
        assertEquals("$ ১৫০,০০০", sampleMoney(AppCurrency.USD))
    }

    @Test
    fun groupingFunction() {
        assertEquals("1", groupDigits("1", true))
        assertEquals("123", groupDigits("123", true))
        assertEquals("1,234", groupDigits("1234", true))
        assertEquals("12,34,567", groupDigits("1234567", true))
        assertEquals("1,23,45,678", groupDigits("12345678", true))
        assertEquals("1,234,567", groupDigits("1234567", false))
        assertEquals("12,345", groupDigits("12345", false))
        assertEquals("123,456", groupDigits("123456", false))
    }

    @Test
    fun currencyCodesAreUniqueAndSymbolsAreNotEmpty() {
        assertEquals(AppCurrency.entries.size, AppCurrency.entries.map { it.code }.toSet().size)
        assertTrue(AppCurrency.entries.all { it.symbol.isNotBlank() })
        assertEquals(AppCurrency.INR, AppCurrency.fromCode("INR"))
        assertEquals(null, AppCurrency.fromCode("XYZ"))
        assertFalse(AppCurrency.USD.lakhGrouping)
    }
}

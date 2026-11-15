package com.example.dukanhisabnext.testing

import com.example.dukanhisabnext.util.AppCurrency
import com.example.dukanhisabnext.util.AppLanguage
import com.example.dukanhisabnext.util.CurrentCurrency
import com.example.dukanhisabnext.util.CurrentLanguage
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Phone tests run inside the app's own process, and the app starts with the language and currency the user
 * (or an earlier session) saved on this phone. A test that expects Bengali digits or dates must not depend on
 * that, so this rule sets the language and the currency explicitly before each test (Bengali and ৳ unless a test
 * class asks for something else) and puts the previous values back afterwards. Only the in-memory values change;
 * the saved choice in display_prefs is never touched.
 *
 * DatabaseTestBase has one, so every test built on it is covered; other test classes add
 * `@get:Rule val display = DisplayDefaultsRule()`.
 */
class DisplayDefaultsRule(
    private val language: AppLanguage = AppLanguage.BENGALI,
    private val currency: AppCurrency = AppCurrency.DEFAULT
) : TestWatcher() {

    private var previousLanguage: AppLanguage = AppLanguage.BENGALI
    private var previousCurrency: AppCurrency = AppCurrency.DEFAULT

    override fun starting(description: Description) {
        previousLanguage = CurrentLanguage.value
        previousCurrency = CurrentCurrency.value
        CurrentLanguage.value = language
        CurrentCurrency.value = currency
    }

    override fun finished(description: Description) {
        CurrentLanguage.value = previousLanguage
        CurrentCurrency.value = previousCurrency
    }
}

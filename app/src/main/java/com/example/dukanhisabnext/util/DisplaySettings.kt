package com.example.dukanhisabnext.util

import android.content.Context
import android.content.res.Configuration

/** Where the display choices (language and currency) are kept between runs. A small interface so tests can use a fake. */
interface DisplayStore {
    /** The saved language, or null when the user never chose (a new install). */
    fun loadLanguage(): AppLanguage?

    fun saveLanguage(language: AppLanguage)

    /** The saved currency, or null when the user never chose. */
    fun loadCurrency(): AppCurrency?

    fun saveCurrency(currency: AppCurrency)
}

/**
 * The saved language and currency in their OWN small settings file, display_prefs.xml. It is deliberately
 * not backup_prefs.xml and is not listed in backup_rules.xml / data_extraction_rules.xml, and the backup
 * file (database + manifest) never contains it: a restore must not change how the app looks.
 */
class SharedPrefsDisplayStore(context: Context) : DisplayStore {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun loadLanguage(): AppLanguage? = languageFromTag(prefs.getString(KEY_LANGUAGE, null))

    override fun saveLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.tag).commit() // commit: a restart may follow at once
    }

    override fun loadCurrency(): AppCurrency? = AppCurrency.fromCode(prefs.getString(KEY_CURRENCY, null))

    override fun saveCurrency(currency: AppCurrency) {
        prefs.edit().putString(KEY_CURRENCY, currency.code).commit() // commit: after a restore the app restarts at once
    }

    companion object {
        const val FILE = "display_prefs"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_CURRENCY = "currency"
    }
}

/**
 * Decides, remembers and announces the language and the currency. [systemTag] gives the language tag of
 * the system's per-app language setting (Android 13+), or null when there is none or the phone is older.
 */
class DisplaySettings(
    private val store: DisplayStore,
    private val systemTag: () -> String?
) {
    /** At app / screen start: works out both choices, makes them current and remembers the language. */
    fun resolveAtStart(): AppLanguage {
        val saved = store.loadLanguage()
        val language = resolveLanguage(systemTag(), saved)
        CurrentLanguage.value = language
        if (saved != language) store.saveLanguage(language)
        CurrentCurrency.value = store.loadCurrency() ?: AppCurrency.DEFAULT
        return language
    }

    /** The user's language choice in Settings: it becomes current at once and is saved. */
    fun chooseLanguage(language: AppLanguage) {
        CurrentLanguage.value = language
        store.saveLanguage(language)
    }

    /** The user's currency choice in Settings: only the symbol and the grouping change, never an amount. */
    fun chooseCurrency(currency: AppCurrency) {
        CurrentCurrency.value = currency
        store.saveCurrency(currency)
    }
}

/** This context, but with the strings and the dates of [language] (for code that has no screen, like the PDF). */
fun Context.withLanguage(language: AppLanguage): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(language.locale)
    return createConfigurationContext(config)
}

/**
 * A string of strings.xml in the chosen currency: the currency swap ([withCurrency]) is done on the template,
 * then the arguments are put in, so an argument that names a currency on purpose stays as it is.
 */
fun android.content.res.Resources.textWithCurrency(id: Int, vararg args: Any): String {
    val template = getString(id).withCurrency()
    return if (args.isEmpty()) template else String.format(configuration.locales[0], template, *args)
}

/** [textWithCurrency] for a <plurals> string. */
fun android.content.res.Resources.quantityTextWithCurrency(id: Int, count: Int, vararg args: Any): String =
    String.format(configuration.locales[0], getQuantityString(id, count).withCurrency(), *args)

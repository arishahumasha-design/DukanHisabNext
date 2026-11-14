package com.example.dukanhisabnext.util

import java.util.Locale

/**
 * The language of the app: Bengali (the default) or English. It decides the strings (values/ is Bengali,
 * values-en/ is English), the digits (০১২ or 012), the month names and how a time of day is written.
 * Only the display changes: money, quantities and dates are stored exactly the same in both.
 */
enum class AppLanguage(val tag: String) {
    BENGALI("bn-BD"),
    ENGLISH("en");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    val usesBengaliDigits: Boolean get() = this == BENGALI
}

/**
 * The language the app shows right now. Set when the app (and each screen) starts, from the saved
 * choice, and again when the user changes it. The formatting functions (formatTaka, formatDate,
 * localDigits...) read it, so no screen has to pass it around. Tests set it and put it back.
 */
object CurrentLanguage {
    @Volatile
    var value: AppLanguage = AppLanguage.BENGALI
}

/** "bn", "bn-BD" -> Bengali; "en", "en-US" -> English; anything else (or nothing) -> null. */
fun languageFromTag(tag: String?): AppLanguage? {
    val language = tag?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_')
    return when (language) {
        "bn" -> AppLanguage.BENGALI
        "en" -> AppLanguage.ENGLISH
        else -> null
    }
}

/**
 * Which language to use at start. On Android 13+ the system's per-app language setting may already say
 * (the user can change it in the phone's own settings): it wins when it is one of ours. Otherwise the
 * saved choice, and for a new install Bengali.
 */
fun resolveLanguage(systemTag: String?, saved: AppLanguage?): AppLanguage =
    languageFromTag(systemTag) ?: saved ?: AppLanguage.BENGALI

/**
 * The digits of [text] in the digit style of the current language: Bengali (১২৩) or Western (123).
 * "1,500.50" -> "১,৫০০.৫০" in Bengali, and "১,৫০০.৫০" -> "1,500.50" in English. Only digits change.
 * For display only: parsing always goes through toLatinDigits, so both digit styles are accepted.
 */
fun localDigits(text: String): String =
    if (CurrentLanguage.value.usesBengaliDigits) toBengaliDigits(text) else toLatinDigits(text)

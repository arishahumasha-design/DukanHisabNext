package com.example.dukanhisabnext

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * values/strings.xml (Bengali, the default) and values-en/strings.xml (English) must stay in step: every key
 * in one is in the other, the formatted places (%1$s ...) match, plurals have their "other" form and arrays
 * the same length. A new string added to only one file fails here.
 */
class StringsParityTest {

    private class Entry(val kind: String, val items: List<String>)

    private fun load(path: String): Map<String, Entry> {
        val xml = File(path).readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        val out = linkedMapOf<String, Entry>()
        for (m in Regex("""<string name="(\w+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(xml)) {
            assertTrue("duplicate key ${m.groupValues[1]} in $path", out.put(m.groupValues[1], Entry("string", listOf(m.groupValues[2]))) == null)
        }
        for (m in Regex("""<plurals name="(\w+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL).findAll(xml)) {
            val items = Regex("""<item quantity="(\w+)">(.*?)</item>""", RegexOption.DOT_MATCHES_ALL).findAll(m.groupValues[2]).toList()
            assertTrue("plurals ${m.groupValues[1]} needs an \"other\" item in $path", items.any { it.groupValues[1] == "other" })
            assertTrue("duplicate key ${m.groupValues[1]} in $path", out.put(m.groupValues[1], Entry("plurals", items.map { it.groupValues[2] })) == null)
        }
        for (m in Regex("""<string-array name="(\w+)">(.*?)</string-array>""", RegexOption.DOT_MATCHES_ALL).findAll(xml)) {
            val items = Regex("""<item>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL).findAll(m.groupValues[2]).map { it.groupValues[1] }.toList()
            assertTrue("duplicate key ${m.groupValues[1]} in $path", out.put(m.groupValues[1], Entry("array", items)) == null)
        }
        return out
    }

    private val bengali = load("src/main/res/values/strings.xml")
    private val english = load("src/main/res/values-en/strings.xml")

    @Test
    fun bothFilesHaveTheSameKeys() {
        val missingInEnglish = bengali.keys - english.keys
        val missingInBengali = english.keys - bengali.keys
        assertTrue("missing in values-en: $missingInEnglish", missingInEnglish.isEmpty())
        assertTrue("missing in values (Bengali): $missingInBengali", missingInBengali.isEmpty())
        assertTrue("the files are really read (${bengali.size} keys)", bengali.size > 500)
    }

    @Test
    fun aKeyIsTheSameKindInBothFiles() {
        for ((key, entry) in bengali) assertEquals("kind of $key", entry.kind, english.getValue(key).kind)
    }

    @Test
    fun theFormattedPlacesMatch() {
        val place = Regex("""%\d\$[sd]|\{max\}""")
        fun places(texts: List<String>) = texts.flatMap { t -> place.findAll(t).map { it.value }.toList() }.toSortedSet()
        for ((key, entry) in bengali) {
            assertEquals("formatted places of $key", places(entry.items), places(english.getValue(key).items))
        }
    }

    @Test
    fun arraysHaveTheSameLength() {
        for ((key, entry) in bengali.filterValues { it.kind == "array" }) {
            assertEquals("length of $key", entry.items.size, english.getValue(key).items.size)
        }
        assertEquals(12, english.getValue("months_bn").items.size)
        assertEquals(7, english.getValue("expense_default_categories").items.size)
    }

    private val bengaliLetters = Regex("[\\u0980-\\u09FF]")

    /** Only these keep Bengali on purpose, in both files (the language names must be readable in either language, ৳ is the taka sign). */
    private val bengaliOnPurpose = setOf(
        "settings_language_title", "language_bengali", "settings_currency_title",
        "price_format", "unit_taka", "field_sell_price", "field_cost_price", "field_unit_cost", "discount_amount_line",
        "change_line", "due_preview_line", "customer_off_needed", "customer_needed_for_due", "paid_line", "sale_due_tag",
        "balance_advance", "below_cost_line", "currency_warning_text", "sales_summary"
    )

    @Test
    fun theEnglishFileHasNoBengaliLeftOver() {
        for ((key, entry) in english) {
            if (key in bengaliOnPurpose) {
                // These only contain the taka sign ৳ (U+09F3), no Bengali words.
                for (item in entry.items) assertTrue("$key: $item", !Regex("[\\u0980-\\u09F2\\u09F4-\\u09FF]").containsMatchIn(item) || key.startsWith("settings_") || key == "language_bengali")
                continue
            }
            for (item in entry.items) assertTrue("Bengali text left in values-en/$key: $item", !bengaliLetters.containsMatchIn(item))
        }
    }

    @Test
    fun noEnglishStringIsEmptyAndEveryApostropheIsSafe() {
        for ((key, entry) in english) for (item in entry.items) {
            assertTrue("$key is empty", item.isNotBlank())
            assertTrue("$key has an unescaped apostrophe (use ’ or \\')", !Regex("""(?<!\\)'""").containsMatchIn(item))
        }
    }

    @Test
    fun englishPluralsDistinguishOneFromMany() {
        for (key in listOf("sales_summary", "home_sales_count", "home_low_stock_count", "expense_count", "report_approximate")) {
            val items = english.getValue(key).items
            assertEquals("$key has one and other", 2, items.size)
            assertTrue("$key: one and other differ", items[0] != items[1])
        }
    }
}

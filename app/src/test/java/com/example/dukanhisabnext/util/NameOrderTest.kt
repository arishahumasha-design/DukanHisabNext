package com.example.dukanhisabnext.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The name order of the product, customer, contact and category lists (twin of ORDER BY name COLLATE NOCASE, name). */
class NameOrderTest {

    private fun sorted(vararg names: String) = names.toList().sortedWith(NAME_ORDER)

    @Test
    fun upperAndLowerCaseSitTogetherInAlphabeticalOrder() {
        // Plain text order would put every capital first: Banana, Zebra, apple, mango.
        assertEquals(listOf("apple", "Banana", "mango", "Zebra"), sorted("Zebra", "mango", "apple", "Banana"))
    }

    @Test
    fun theSameNameInTwoCasesHasAStableOrder() {
        assertEquals(listOf("Rahim", "rahim"), sorted("rahim", "Rahim"))
        assertEquals(listOf("Rahim", "rahim"), sorted("Rahim", "rahim"))
        assertEquals(0, NAME_ORDER.compare("same", "same"))
    }

    @Test
    fun bengaliNamesAreNotChanged() {
        // Bengali has no case; the code point order of the letters is kept, and English comes before Bengali.
        assertEquals(listOf("apple", "করিম", "রহিম"), sorted("রহিম", "apple", "করিম"))
        assertEquals("করিম মিয়া", foldAscii("করিম মিয়া"))
    }

    @Test
    fun onlyTheLettersAToZAreFolded() {
        assertEquals("josim mazi 12", foldAscii("Josim MAZI 12"))
        assertEquals("É", foldAscii("É")) // like SQLite's NOCASE: other letters are left alone
    }

    @Test
    fun theRealDemoNamesComeOutAlphabetically() {
        // From the emulator data: capital and small first letters were mixed.
        assertEquals(listOf("elias", "Josim Mazi", "shahin"), sorted("shahin", "Josim Mazi", "elias"))
        assertEquals(listOf("Acmix", "Kiulop", "sonet"), sorted("sonet", "Kiulop", "Acmix"))
    }

    @Test
    fun theOrderIsConsistentBothWays() {
        val names = listOf("a", "A", "b", "B", "ab", "Ab", "aB", "", " ", "1", "অ")
        for (x in names) for (y in names) {
            assertEquals("$x vs $y", -Integer.signum(NAME_ORDER.compare(x, y)), Integer.signum(NAME_ORDER.compare(y, x)))
        }
        assertTrue(sorted(*names.toTypedArray()).size == names.size)
    }
}

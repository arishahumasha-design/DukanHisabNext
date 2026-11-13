package com.example.dukanhisabnext.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialAvatarTest {

    @Test
    fun theSameNameAlwaysGetsTheSameTint() {
        assertEquals(avatarTintIndex("রহিম উদ্দিন"), avatarTintIndex("রহিম উদ্দিন"))
        // Spaces around the name do not change the colour.
        assertEquals(avatarTintIndex("করিম"), avatarTintIndex("  করিম "))
    }

    @Test
    fun theTintIsAlwaysOneOfTheSix() {
        val names = listOf("", "আ", "রহিম", "Karim", "সালমা বেগম", "a".repeat(500), "😀")
        for (name in names) {
            assertTrue(name, avatarTintIndex(name) in 0 until AVATAR_TINT_COUNT)
        }
    }

    @Test
    fun theHashIsPinnedSoAnAppUpdateNeverRecolours() {
        // String.hashCode is defined by the Java language; these values must never change.
        assertEquals(Math.floorMod("Karim".hashCode(), 6), avatarTintIndex("Karim"))
        assertEquals(Math.floorMod(-7, 6), 5) // negative hashes still land in 0..5
    }

    @Test
    fun differentNamesSpreadOverSeveralTints() {
        val used = listOf("রহিম", "করিম", "সালমা", "জব্বার", "নাসরিন", "হাসান", "মিনা", "আলম")
            .map { avatarTintIndex(it) }.toSet()
        assertTrue(used.size >= 3)
    }
}

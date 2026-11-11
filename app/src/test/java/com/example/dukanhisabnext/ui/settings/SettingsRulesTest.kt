package com.example.dukanhisabnext.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRulesTest {

    // ---- hasShop and the shop button label ----

    @Test
    fun noShopNameMeansNoShop() {
        assertFalse(hasShop(null))
        assertFalse(hasShop(""))
        assertFalse(hasShop("   "))
        assertFalse(hasShop("\n\t "))
    }

    @Test
    fun aNameMeansThereIsAShop() {
        assertTrue(hasShop("করিম স্টোর"))
        assertTrue(hasShop("Karim Store"))
        assertTrue(hasShop("  করিম  ")) // spaces around a real name do not matter
        assertTrue(hasShop("১"))
    }

    @Test
    fun theButtonSaysAddWhenThereIsNoShopAndChangeWhenThereIs() {
        assertEquals(ShopButtonKind.ADD, shopButtonKind(null))
        assertEquals(ShopButtonKind.ADD, shopButtonKind(""))
        assertEquals(ShopButtonKind.ADD, shopButtonKind("   "))
        assertEquals(ShopButtonKind.CHANGE, shopButtonKind("করিম স্টোর"))
        assertEquals(ShopButtonKind.CHANGE, shopButtonKind(" Karim Store "))
    }

    @Test
    fun theLabelFollowsTheShopNameWhenItIsSavedOrCleared() {
        // Settings watches the profile row, so the kind is simply worked out again from the new name.
        var name: String? = null
        assertEquals(ShopButtonKind.ADD, shopButtonKind(name))
        name = "করিম স্টোর"
        assertEquals(ShopButtonKind.CHANGE, shopButtonKind(name))
        name = ""
        assertEquals(ShopButtonKind.ADD, shopButtonKind(name))
    }

    // ---- avatarLetter: the first letter of the owner name ----

    @Test
    fun bengaliNamesGiveTheirFirstLetter() {
        assertEquals("ক", avatarLetter("করিম"))
        assertEquals("আ", avatarLetter("আনিস"))
        assertEquals("ঈ", avatarLetter("ঈশান"))
        assertEquals("ম", avatarLetter("মোঃ করিম")) // the vowel sign after ম is not a letter of its own
    }

    @Test
    fun englishNamesGiveACapitalLetter() {
        assertEquals("R", avatarLetter("rahim"))
        assertEquals("R", avatarLetter("Rahim"))
        assertEquals("K", avatarLetter("karim khan"))
        assertEquals("A", avatarLetter("A"))
    }

    @Test
    fun leadingSpacesAreSkipped() {
        assertEquals("ক", avatarLetter("  করিম"))
        assertEquals("R", avatarLetter("   rahim"))
        assertEquals("ক", avatarLetter("\n\t করিম"))
    }

    @Test
    fun anEmptyNameGivesNoLetter() {
        assertNull(avatarLetter(null))
        assertNull(avatarLetter(""))
        assertNull(avatarLetter("   "))
        assertNull(avatarLetter("\n\t"))
    }

    @Test
    fun leadingSymbolsAreSkippedToo() {
        assertEquals("ক", avatarLetter("(করিম)"))
        assertEquals("R", avatarLetter("-rahim"))
        assertEquals("ক", avatarLetter("😀 করিম")) // an emoji is not a letter
        assertNull(avatarLetter("---"))
        assertNull(avatarLetter(" . , ! "))
    }

    @Test
    fun aNameStartingWithADigitGivesTheDigit() {
        assertEquals("১", avatarLetter("১২৩ স্টোর"))
        assertEquals("7", avatarLetter("7 Eleven"))
    }

    @Test
    fun aJoinedFirstLetterGivesItsFirstConsonant() {
        // "ফ্রান্সিস" starts with the joined letter ফ্র; the avatar shows its first letter "ফ".
        assertEquals("ফ", avatarLetter("ফ্রান্সিস"))
    }

    // ---- ownerDisplayName: the text on the card ----

    private val placeholder = "মালিকের নাম দিন"

    @Test
    fun aBengaliNameIsShownAsTyped() {
        assertEquals("করিম মিয়া", ownerDisplayName("করিম মিয়া", placeholder))
    }

    @Test
    fun anEnglishNameKeepsItsOwnCapitals() {
        assertEquals("rahim khan", ownerDisplayName("rahim khan", placeholder))
        assertEquals("Rahim Khan", ownerDisplayName("Rahim Khan", placeholder))
    }

    @Test
    fun leadingAndTrailingSpacesAreRemoved() {
        assertEquals("করিম", ownerDisplayName("   করিম  ", placeholder))
        assertEquals("Rahim", ownerDisplayName("  Rahim", placeholder))
    }

    @Test
    fun spacesInsideTheNameAreKept() {
        assertEquals("করিম  মিয়া", ownerDisplayName("করিম  মিয়া", placeholder))
    }

    @Test
    fun anEmptyNameShowsThePlaceholderNotACrash() {
        assertEquals(placeholder, ownerDisplayName(null, placeholder))
        assertEquals(placeholder, ownerDisplayName("", placeholder))
        assertEquals(placeholder, ownerDisplayName("   ", placeholder))
    }

    @Test
    fun theAvatarLetterAndTheDisplayNameAgree() {
        // A name with leading spaces: the card shows the trimmed name and the letter of its first character.
        val name = "  rahim"
        assertEquals("rahim", ownerDisplayName(name, placeholder))
        assertEquals("R", avatarLetter(name))
    }

    // ---- ownerPhoneDisplay ----

    @Test
    fun theMobileIsShownInBengaliDigits() {
        assertEquals("০১৭১১০০০০০০", ownerPhoneDisplay("01711000000"))
        assertEquals("০১৭১১০০০০০০", ownerPhoneDisplay("  01711000000 "))
    }

    @Test
    fun noMobileShowsNothing() {
        assertNull(ownerPhoneDisplay(null))
        assertNull(ownerPhoneDisplay(""))
        assertNull(ownerPhoneDisplay("   "))
    }
}

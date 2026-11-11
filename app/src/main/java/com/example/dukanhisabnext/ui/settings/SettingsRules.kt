package com.example.dukanhisabnext.ui.settings

import com.example.dukanhisabnext.util.toBengaliDigits

// Small decisions of the Settings screen, as plain Kotlin (no Android) so they can be
// unit-tested on the PC. The words themselves come from strings.xml.

/** True when the shop has a name. An empty or blank name means "no shop yet". */
fun hasShop(shopName: String?): Boolean = !shopName.isNullOrBlank()

/** Which label the big shop button shows. */
enum class ShopButtonKind {
    ADD,    // "দোকান যুক্ত করুন": the shop name is empty
    CHANGE  // "দোকান পরিবর্তন করুন": there is a shop (its name is shown in small text above)
}

fun shopButtonKind(shopName: String?): ShopButtonKind =
    if (hasShop(shopName)) ShopButtonKind.CHANGE else ShopButtonKind.ADD

/**
 * The letter inside the round avatar: the first letter (or digit) of the owner name.
 * - Leading spaces and symbols are skipped: "  করিম" and "(করিম)" both give "ক".
 * - An English letter is shown in capitals: "rahim" gives "R".
 * - Bengali letters have no capitals and stay as they are.
 * - An empty or blank name gives null: the screen then shows a person icon instead.
 * A name that starts with a joined letter such as "ফ্রান্সিস" gives its first letter "ফ".
 */
fun avatarLetter(ownerName: String?): String? {
    val name = ownerName ?: return null
    var index = 0
    while (index < name.length) {
        val codePoint = name.codePointAt(index)
        if (Character.isLetterOrDigit(codePoint)) {
            return String(Character.toChars(Character.toUpperCase(codePoint)))
        }
        index += Character.charCount(codePoint)
    }
    return null
}

/**
 * The owner name as shown on the card: leading and trailing spaces removed, otherwise as typed
 * (an English name keeps its own capitals). An empty or blank name gives the neutral
 * [placeholder] from strings.xml.
 */
fun ownerDisplayName(ownerName: String?, placeholder: String): String =
    ownerName?.trim()?.ifEmpty { null } ?: placeholder

/** The owner's mobile in Bengali digits for the card, or null when there is none (then nothing is shown). */
fun ownerPhoneDisplay(phone: String?): String? =
    phone?.trim()?.ifEmpty { null }?.let { toBengaliDigits(it) }

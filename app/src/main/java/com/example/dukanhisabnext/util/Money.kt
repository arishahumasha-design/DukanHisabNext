package com.example.dukanhisabnext.util

import kotlin.math.abs

// Money is stored as Long paisa (1 taka = 100 paisa). See SPEC.md principle 3.

const val PAISA_PER_TAKA = 100L

/**
 * Paisa -> Bengali taka text, without the ৳ sign (screens add it from strings.xml).
 *   150000 -> "১,৫০০"      (whole taka: no decimals)
 *   150050 -> "১,৫০০.৫০"
 *   12345600 -> "১,২৩,৪৫৬" (Bangladeshi grouping: lakh, crore)
 */
fun formatTaka(paisa: Long): String {
    val taka = abs(paisa / PAISA_PER_TAKA)
    val restPaisa = abs(paisa % PAISA_PER_TAKA)

    val sign = if (paisa < 0) "-" else ""
    val wholeText = groupDigits(taka.toString(), CurrentCurrency.value.lakhGrouping)
    val text = if (restPaisa == 0L) {
        wholeText
    } else {
        "$wholeText.${restPaisa.toString().padStart(2, '0')}"
    }
    return localDigits(sign + text)
}

/**
 * User text -> paisa. Accepts Bengali or Latin digits and commas.
 *   "১২.৫" -> 1250, "1,200" -> 120000, "abc" -> null, "1.234" -> null
 */
fun parseTakaToPaisa(input: String): Long? = parseFixedPoint(input, decimals = 2)

/**
 * Paisa -> text with normal (Latin) digits, no commas, no sign, trailing zeros dropped.
 * Used to fill an edit box (the screen shows it with Bengali digits). Paisa must not be negative.
 *   150000 -> "1500", 150050 -> "1500.5", 5 -> "0.05"
 */
fun formatTakaPlain(paisa: Long): String {
    val taka = paisa / PAISA_PER_TAKA
    val restPaisa = paisa % PAISA_PER_TAKA
    return if (restPaisa == 0L) {
        taka.toString()
    } else {
        "$taka." + restPaisa.toString().padStart(2, '0').trimEnd('0')
    }
}

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
    val wholeText = groupBangladeshi(taka.toString())
    val text = if (restPaisa == 0L) {
        wholeText
    } else {
        "$wholeText.${restPaisa.toString().padStart(2, '0')}"
    }
    return toBengaliDigits(sign + text)
}

/**
 * User text -> paisa. Accepts Bengali or Latin digits and commas.
 *   "১২.৫" -> 1250, "1,200" -> 120000, "abc" -> null, "1.234" -> null
 */
fun parseTakaToPaisa(input: String): Long? = parseFixedPoint(input, decimals = 2)

/**
 * Bangladesh groups numbers as 12,34,56,789: the last 3 digits together,
 * then groups of 2 going left.
 */
internal fun groupBangladeshi(digits: String): String {
    if (digits.length <= 3) return digits
    val lastThree = digits.takeLast(3)
    val head = digits.dropLast(3)
    // Walk the head from the right in pairs, then put the pairs back in order.
    val pairs = head.reversed().chunked(2).map { it.reversed() }.reversed()
    return pairs.joinToString(",") + "," + lastThree
}

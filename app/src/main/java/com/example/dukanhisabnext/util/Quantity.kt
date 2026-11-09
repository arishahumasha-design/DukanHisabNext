package com.example.dukanhisabnext.util

import kotlin.math.abs

// Quantity is stored as Long thousandths: 1000 = 1 unit, 250 = 0.250 kg. See SPEC.md principle 4.

const val QUANTITY_SCALE = 1000L

/**
 * Thousandths -> Bengali text. Trailing zeros after the dot are dropped.
 *   1000 -> "১", 250 -> "০.২৫", 1500 -> "১.৫", 1005 -> "১.০০৫"
 */
fun formatQuantity(thousandths: Long): String {
    val whole = abs(thousandths / QUANTITY_SCALE)
    val fraction = abs(thousandths % QUANTITY_SCALE)

    val sign = if (thousandths < 0) "-" else "" // stock can go negative
    val text = if (fraction == 0L) {
        whole.toString()
    } else {
        "$whole." + fraction.toString().padStart(3, '0').trimEnd('0')
    }
    return toBengaliDigits(sign + text)
}

/** User text -> thousandths. "০.২৫" -> 250, "2" -> 2000, "1.2345" -> null. */
fun parseQuantity(input: String): Long? = parseFixedPoint(input, decimals = 3)

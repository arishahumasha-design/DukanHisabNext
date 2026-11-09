package com.example.dukanhisabnext.util

import kotlin.math.abs

// Quantity is stored as Long thousandths: 1000 = 1 unit, 250 = 0.250 kg. See SPEC.md principle 4.

const val QUANTITY_SCALE = 1000L

/**
 * Thousandths -> text with normal (Latin) digits and no grouping.
 * Used to fill an edit box. The screen shows it with Bengali digits.
 *   1000 -> "1", 250 -> "0.25", 1005 -> "1.005"
 */
fun formatQuantityPlain(thousandths: Long): String {
    val whole = abs(thousandths / QUANTITY_SCALE)
    val fraction = abs(thousandths % QUANTITY_SCALE)

    val sign = if (thousandths < 0) "-" else "" // stock can go negative
    return if (fraction == 0L) {
        "$sign$whole"
    } else {
        "$sign$whole." + fraction.toString().padStart(3, '0').trimEnd('0')
    }
}

/**
 * Thousandths -> Bengali text. Trailing zeros after the dot are dropped.
 *   1000 -> "১", 250 -> "০.২৫", 1500 -> "১.৫", 1005 -> "১.০০৫"
 */
fun formatQuantity(thousandths: Long): String = toBengaliDigits(formatQuantityPlain(thousandths))

/** Like [formatQuantity], but positive numbers get a plus sign: 2000 -> "+২", -250 -> "-০.২৫". */
fun formatSignedQuantity(thousandths: Long): String =
    if (thousandths > 0) "+" + formatQuantity(thousandths) else formatQuantity(thousandths)

/** User text -> thousandths. "০.২৫" -> 250, "2" -> 2000, "1.2345" -> null. */
fun parseQuantity(input: String): Long? = parseFixedPoint(input, decimals = 3)

package com.example.dukanhisabnext.util

// Shared parsing logic for money (2 decimals) and quantity (3 decimals).
// We never go through Double: we split the text at the "." and build a whole
// number (Long) by hand, so 0.1 + 0.2 problems can never happen.

private val NUMBER_PATTERN = Regex("""^(\d*)(?:\.(\d*))?$""")

// 15 digits before the dot keeps us far away from the Long limit (about 9.2 x 10^18).
private const val MAX_WHOLE_DIGITS = 15

/**
 * Turns user text into a whole number with [decimals] implied decimal places.
 * Example with decimals = 2: "12.5" -> 1250, "১,২০০" -> 120000.
 * Returns null for empty, negative, badly formed, too precise or too large input.
 */
internal fun parseFixedPoint(input: String, decimals: Int): Long? {
    val cleaned = toLatinDigits(input).trim().replace(",", "")
    val match = NUMBER_PATTERN.matchEntire(cleaned) ?: return null

    val wholePart = match.groupValues[1]
    val fractionPart = match.groupValues[2] // "" when there is no dot

    if (wholePart.isEmpty() && fractionPart.isEmpty()) return null // "" or "."
    if (fractionPart.length > decimals) return null                // "1.234" for money
    if (wholePart.length > MAX_WHOLE_DIGITS) return null

    var multiplier = 1L
    repeat(decimals) { multiplier *= 10 }

    val whole = wholePart.ifEmpty { "0" }.toLong()
    val fraction = fractionPart.padEnd(decimals, '0').ifEmpty { "0" }.toLong()
    return whole * multiplier + fraction
}

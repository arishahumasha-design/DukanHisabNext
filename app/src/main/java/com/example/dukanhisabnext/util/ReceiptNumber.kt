package com.example.dukanhisabnext.util

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Receipt numbers look like "261109-001": the Dhaka date (yyMMdd), a dash, and that day's
 * counter (at least 3 digits; it simply grows to 1000, 1001... on a very busy day).
 * They are stored with normal digits and shown with Bengali digits.
 *
 * The next counter is the HIGHEST counter already used that day + 1, worked out as a
 * NUMBER. Sorting the text would be wrong: as text "261109-999" comes after "261109-1000".
 */
object ReceiptNumber {

    private val DATE_PART: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd", Locale.ENGLISH)
    private const val MIN_COUNTER_DIGITS = 3

    /** "261109" for any moment on 9 Nov 2026 in Dhaka. */
    fun datePrefix(epochMillis: Long): String = DATE_PART.format(DhakaTime.localDate(epochMillis))

    /** "261109" + 7 -> "261109-007". */
    fun build(prefix: String, counter: Int): String {
        require(counter > 0) { "counter must be 1 or more" }
        return "$prefix-" + counter.toString().padStart(MIN_COUNTER_DIGITS, '0')
    }

    /**
     * The counter part of [receiptNo] as a number, or null if it does not belong to [prefix]
     * or is not well formed. "261109-1000" with prefix "261109" -> 1000.
     */
    fun counterOf(receiptNo: String, prefix: String): Int? {
        val start = "$prefix-"
        if (!receiptNo.startsWith(start)) return null
        val digits = receiptNo.substring(start.length)
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
        return digits.toIntOrNull()
    }

    /**
     * The next receipt number for the day [prefix], given the receipt numbers that already
     * exist. Numbers from other days or badly formed ones are ignored.
     */
    fun next(prefix: String, existing: List<String>): String {
        val highest = existing.mapNotNull { counterOf(it, prefix) }.maxOrNull() ?: 0
        return build(prefix, highest + 1)
    }
}

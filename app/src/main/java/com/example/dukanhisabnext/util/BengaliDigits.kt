package com.example.dukanhisabnext.util

// Bengali digits ০১২৩৪৫৬৭৮৯ sit next to each other in Unicode, starting at '০' (U+09E6),
// just like 0-9 do in ASCII. So converting is simple arithmetic on the character code.
private const val BENGALI_ZERO = '০'
private const val BENGALI_NINE = '৯'

/** "1,500.50" -> "১,৫০০.৫০". Only digits change; other characters stay. For display only. */
fun toBengaliDigits(text: String): String = buildString(text.length) {
    for (c in text) {
        append(if (c in '0'..'9') BENGALI_ZERO + (c - '0') else c)
    }
}

/** "১২.৫" -> "12.5". Used before parsing what the user typed. */
fun toLatinDigits(text: String): String = buildString(text.length) {
    for (c in text) {
        append(if (c in BENGALI_ZERO..BENGALI_NINE) '0' + (c - BENGALI_ZERO) else c)
    }
}

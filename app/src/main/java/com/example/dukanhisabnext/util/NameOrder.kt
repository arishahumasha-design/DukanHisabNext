package com.example.dukanhisabnext.util

/**
 * How names are sorted in the product, customer, contact and category lists (M9): letters of the
 * English alphabet ignore upper and lower case ("apple" sits next to "Apple", not after "Zebra"),
 * and an exact tie falls back to the plain text, so the order never jumps around. Bengali has no
 * upper or lower case, so it is untouched. Stored names are NEVER changed, only their order.
 *
 * This is the Kotlin twin of the SQL "ORDER BY name COLLATE NOCASE, name" in the DAOs (SQLite's
 * NOCASE folds only A-Z, exactly like [foldAscii]); a phone test checks the two give the same order.
 */
val NAME_ORDER: Comparator<String> = Comparator { a, b ->
    val folded = foldAscii(a).compareTo(foldAscii(b))
    if (folded != 0) folded else a.compareTo(b)
}

/** Lower-cases only the letters A-Z (what SQLite's NOCASE does). */
fun foldAscii(text: String): String = buildString(text.length) {
    for (ch in text) append(if (ch in 'A'..'Z') ch + 32 else ch)
}

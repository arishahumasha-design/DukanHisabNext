package com.example.dukanhisabnext.util

// A percentage is kept as a whole number of HUNDREDTHS of a percent, never as a Double:
//   10%    -> 1000
//   12.5%  -> 1250
//   100%   -> 10000
// This keeps the discount math exact, like money (paisa) and quantity (thousandths).

/** 100% in hundredths of a percent. */
const val MAX_PERCENT_HUNDREDTHS = 10_000L

/**
 * User text -> hundredths of a percent. Bengali digits are accepted.
 * "10" -> 1000, "12.5" -> 1250, "0" -> 0. Returns null for empty, negative or badly
 * formed text and for more than 2 decimals. It does NOT limit the size: "150" gives 15000,
 * and the caller checks the 0 to 100 rule (see [MAX_PERCENT_HUNDREDTHS]).
 */
fun parsePercent(input: String): Long? = parseFixedPoint(input, decimals = 2)

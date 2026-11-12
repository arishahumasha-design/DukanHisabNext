package com.example.dukanhisabnext.ui.components

// When to ask "টাকার পরিমাণ ঠিক আছে?" before saving (M9). Plain Kotlin, PC-tested.
//
// The most expensive typing mistake in a shop app is one extra zero: ৳500 saved as ৳5000 changes a
// customer's due or the day's profit, and (because ledger entries and stock movements are never
// edited) can only be fixed by another entry. So big numbers get ONE extra question; small ones
// (nearly every real entry) never do.

/** Paisa. A khata entry of ৳10,000 or more asks first. A small shop's usual entry is hundreds to a few thousand taka. */
const val LEDGER_CONFIRM_PAISA: Long = 1_000_000L

/** Paisa. An expense of ৳5,000 or more asks first (lower than the khata: an expense is rarely that big, except rent and pay, which are worth a second look). */
const val EXPENSE_CONFIRM_PAISA: Long = 500_000L

/** Thousandths. A stock change of 1,000 units or more (of any unit) asks first. */
const val STOCK_CONFIRM_QUANTITY: Long = 1_000_000L

/** Thousandths. A change this big AND at least [STOCK_CONFIRM_RATIO] times the stock now asks first (stock 20, typed 200). */
const val STOCK_CONFIRM_RELATIVE_MIN_QUANTITY: Long = 100_000L

/** How many times bigger than the current stock a change must be to ask (see above). */
const val STOCK_CONFIRM_RATIO: Long = 10L

/** Money: ask when the typed amount (paisa) reaches [thresholdPaisa]. An empty or unreadable box (null) is left to the normal error. */
fun needsMoneyConfirm(amountPaisa: Long?, thresholdPaisa: Long): Boolean =
    amountPaisa != null && amountPaisa >= thresholdPaisa

/**
 * Stock: [change] is the quantity added or removed (thousandths, the sign does not matter);
 * [current] is the stock now (null while unknown). Ask when the change is 1,000 units or more, or
 * when it is at least 10 times the stock now (and at least 100 units, so a new product's first
 * small purchase never asks).
 */
fun needsStockConfirm(change: Long?, current: Long?): Boolean {
    if (change == null) return false
    val size = if (change < 0) -change else change // never negative-overflows: quantities are far below Long.MAX
    if (size >= STOCK_CONFIRM_QUANTITY) return true
    if (current != null && current > 0 && size >= STOCK_CONFIRM_RELATIVE_MIN_QUANTITY && size >= current * STOCK_CONFIRM_RATIO) return true
    return false
}

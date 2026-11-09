package com.example.dukanhisabnext.util

/** Small, testable stock calculations (SPEC.md principle 6). All values are thousandths. */
object StockMath {
    /**
     * The shopkeeper counts the real stock on the shelf ("counted"). The app saves the
     * DIFFERENCE as one ADJUSTMENT movement. Positive = stock was missing, negative = too much.
     * Example: current 12, counted 10 gives -2.
     */
    fun correctionDifference(counted: Long, current: Long): Long = counted - current
}

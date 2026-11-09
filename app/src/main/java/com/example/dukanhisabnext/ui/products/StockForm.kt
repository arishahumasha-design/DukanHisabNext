package com.example.dukanhisabnext.ui.products

import com.example.dukanhisabnext.util.StockMath
import com.example.dukanhisabnext.util.parseQuantity
import com.example.dukanhisabnext.util.parseTakaToPaisa

/** The two things you can do on the stock screen. */
enum class StockMode {
    PURCHASE,   // stock bought: add to stock
    CORRECTION  // stock counted again: fix the number
}

data class StockFormErrors(
    val quantity: FormError? = null,
    val unitCost: FormError? = null,
    val counted: FormError? = null
) {
    val hasErrors: Boolean get() = quantity != null || unitCost != null || counted != null
}

sealed interface StockFormResult {
    /** Save a PURCHASE movement. [unitCost] is paisa per unit, or null if not entered. */
    data class Purchase(val quantity: Long, val unitCost: Long?) : StockFormResult

    /** Save an ADJUSTMENT so that the stock becomes [counted]. */
    data class Correction(val counted: Long) : StockFormResult

    data class Invalid(val errors: StockFormErrors) : StockFormResult
}

/**
 * Checks the stock form. [currentStock] (thousandths) is needed to notice a correction
 * that changes nothing.
 */
fun validateStockForm(
    mode: StockMode,
    quantity: String,
    unitCost: String,
    counted: String,
    currentStock: Long
): StockFormResult = when (mode) {
    StockMode.PURCHASE -> {
        val qty = if (quantity.isBlank()) null else parseQuantity(quantity)
        val qtyError = when {
            quantity.isBlank() -> FormError.REQUIRED
            qty == null -> FormError.INVALID_NUMBER
            qty <= 0L -> FormError.MUST_BE_POSITIVE
            else -> null
        }
        val cost = readOptional(unitCost, ::parseTakaToPaisa)

        if (qtyError != null || cost.error != null) {
            StockFormResult.Invalid(StockFormErrors(quantity = qtyError, unitCost = cost.error))
        } else {
            StockFormResult.Purchase(quantity = qty!!, unitCost = cost.value)
        }
    }

    StockMode.CORRECTION -> {
        val value = if (counted.isBlank()) null else parseQuantity(counted)
        val error = when {
            counted.isBlank() -> FormError.REQUIRED
            value == null -> FormError.INVALID_NUMBER
            StockMath.correctionDifference(value, currentStock) == 0L -> FormError.NO_CHANGE
            else -> null
        }
        if (error != null) {
            StockFormResult.Invalid(StockFormErrors(counted = error))
        } else {
            StockFormResult.Correction(counted = value!!)
        }
    }
}

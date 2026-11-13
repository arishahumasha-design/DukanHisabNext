package com.example.dukanhisabnext.ui.products

import com.example.dukanhisabnext.data.entity.WarrantyUnit
import com.example.dukanhisabnext.util.parseQuantity
import com.example.dukanhisabnext.warranty.checkWarranty
import com.example.dukanhisabnext.warranty.parseWarrantyInput
import com.example.dukanhisabnext.util.parseTakaToPaisa

// Form checking is plain Kotlin (no Android), so it can be unit-tested on the PC.
// It never shows text itself: it returns an error CODE and the screen maps it to Bengali.

enum class FormError {
    REQUIRED, INVALID_NUMBER, INVALID_PHONE, MUST_BE_POSITIVE, DUPLICATE, NO_CHANGE,
    TOO_LARGE,          // a number so big the sum would overflow (a typo)
    DISCOUNT_TOO_LARGE, // discount more than the subtotal
    PERCENT_OUT_OF_RANGE, // a percentage below 0 or above 100
    DUE_MORE_THAN_TOTAL,  // a due bigger than the sale total
    WARRANTY_OUT_OF_RANGE // a warranty number below 1 or above the limit of its unit (the screen names the limit)
}

/** What the user typed. All numbers are still text here. */
data class ProductFormInput(
    val name: String,
    val sellPrice: String,
    val costPrice: String,
    val openingStock: String,
    val lowStockLevel: String,
    val isNew: Boolean // opening stock is only used (and checked) for a NEW product
)

data class ProductFormErrors(
    val name: FormError? = null,
    val sellPrice: FormError? = null,
    val costPrice: FormError? = null,
    val openingStock: FormError? = null,
    val lowStockLevel: FormError? = null,
    /** Version 5: the warranty number box (only while the warranty switch is on). */
    val warranty: FormError? = null
) {
    val hasErrors: Boolean
        get() = name != null || sellPrice != null || costPrice != null ||
            openingStock != null || lowStockLevel != null || warranty != null
}

/** The checked warranty of the form: both null (no warranty) or both set. [error] when the box is wrong. */
data class WarrantyInputResult(val value: Int?, val unit: WarrantyUnit?, val error: FormError?)

/**
 * The warranty part of the product form. Switch off = no warranty (nothing is checked). Switch on:
 * the box must hold a whole number within the limit of the unit (1-999 days, 1-120 months, 1-50
 * years; the same [checkWarranty] as the cart and the save).
 */
fun validateWarrantyInput(on: Boolean, text: String, unit: WarrantyUnit): WarrantyInputResult {
    if (!on) return WarrantyInputResult(null, null, null)
    if (text.isBlank()) return WarrantyInputResult(null, null, FormError.REQUIRED)
    val value = parseWarrantyInput(text) ?: return WarrantyInputResult(null, null, FormError.INVALID_NUMBER)
    if (checkWarranty(value, unit) != null) return WarrantyInputResult(null, null, FormError.WARRANTY_OUT_OF_RANGE)
    return WarrantyInputResult(value, unit, null)
}

/** Clean, checked values: money in paisa, quantities in thousandths. */
data class ValidatedProduct(
    val name: String,
    val sellPrice: Long,
    val costPrice: Long?,
    val lowStockLevel: Long?,
    val openingStock: Long // 0 when blank
)

sealed interface ProductFormResult {
    data class Valid(val product: ValidatedProduct) : ProductFormResult
    data class Invalid(val errors: ProductFormErrors) : ProductFormResult
}

/** The result of reading one optional number box. */
internal class OptionalNumber(val value: Long?, val error: FormError?)

/** Blank is fine (value = null). Typed but unreadable gives INVALID_NUMBER. */
internal fun readOptional(text: String, parse: (String) -> Long?): OptionalNumber =
    if (text.isBlank()) {
        OptionalNumber(null, null)
    } else {
        val parsed = parse(text)
        if (parsed == null) OptionalNumber(null, FormError.INVALID_NUMBER) else OptionalNumber(parsed, null)
    }

/**
 * Rules:
 * - name: required
 * - selling price: required and greater than 0
 * - cost price, low-stock level: optional (0 is allowed)
 * - opening stock: optional, only for a new product (blank = 0)
 */
fun validateProductForm(input: ProductFormInput): ProductFormResult {
    val name = input.name.trim()

    val nameError = if (name.isEmpty()) FormError.REQUIRED else null

    var sellPrice: Long? = null
    val sellPriceError = if (input.sellPrice.isBlank()) {
        FormError.REQUIRED
    } else {
        sellPrice = parseTakaToPaisa(input.sellPrice)
        when {
            sellPrice == null -> FormError.INVALID_NUMBER
            sellPrice <= 0L -> FormError.MUST_BE_POSITIVE
            else -> null
        }
    }

    val cost = readOptional(input.costPrice, ::parseTakaToPaisa)
    val low = readOptional(input.lowStockLevel, ::parseQuantity)
    val opening = if (input.isNew) readOptional(input.openingStock, ::parseQuantity) else OptionalNumber(null, null)

    val errors = ProductFormErrors(
        name = nameError,
        sellPrice = sellPriceError,
        costPrice = cost.error,
        openingStock = opening.error,
        lowStockLevel = low.error
    )
    if (errors.hasErrors) return ProductFormResult.Invalid(errors)

    return ProductFormResult.Valid(
        ValidatedProduct(
            name = name,
            sellPrice = sellPrice!!, // safe: no error means it was read correctly
            costPrice = cost.value,
            lowStockLevel = low.value,
            openingStock = opening.value ?: 0L
        )
    )
}

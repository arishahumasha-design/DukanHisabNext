package com.example.dukanhisabnext.ui.products

import com.example.dukanhisabnext.util.parseQuantity
import com.example.dukanhisabnext.util.parseTakaToPaisa

// Form checking is plain Kotlin (no Android), so it can be unit-tested on the PC.
// It never shows text itself: it returns an error CODE and the screen maps it to Bengali.

enum class FormError {
    REQUIRED, INVALID_NUMBER, INVALID_PHONE, MUST_BE_POSITIVE, DUPLICATE, NO_CHANGE,
    TOO_LARGE,          // a number so big the sum would overflow (a typo)
    DISCOUNT_TOO_LARGE  // discount more than the subtotal
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
    val lowStockLevel: FormError? = null
) {
    val hasErrors: Boolean
        get() = name != null || sellPrice != null || costPrice != null ||
            openingStock != null || lowStockLevel != null
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

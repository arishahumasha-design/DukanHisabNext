package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.util.QUANTITY_SCALE
import com.example.dukanhisabnext.util.formatQuantityPlain
import com.example.dukanhisabnext.util.formatTakaPlain
import com.example.dukanhisabnext.util.parseQuantity

// The cart is plain Kotlin data, so its rules can be unit-tested on the PC.
// Numbers are kept as the TEXT the user typed (normal digits), because half-typed
// numbers like "2." or "" must be allowed while typing. They are read (parsed) only
// when the summary is worked out (see CartSummary.kt).

/** One cart line: one product. */
data class CartLineInput(
    val productId: Long,
    val name: String,          // the product's name when it was added (shown in the cart)
    val unit: ProductUnit,
    val quantityText: String,  // "1", "2.5", ... typed by the user or set by tapping
    val unitPriceText: String  // taka, for ONE unit. Starts as the product's price; the user may edit
                               // it, and then it changes THIS sale only, never the product.
)

/** How the discount box is read: a fixed taka amount, or a percentage of the subtotal. */
enum class DiscountUnit { TAKA, PERCENT }

/**
 * The discount and the delivery charge each have a switch. While a switch is OFF its value
 * is 0, but what was typed is kept, so switching it on again brings it back.
 */
data class CartState(
    val lines: List<CartLineInput> = emptyList(),
    val discountOn: Boolean = false,
    val discountUnit: DiscountUnit = DiscountUnit.TAKA,
    val discountText: String = "",
    val deliveryOn: Boolean = false,
    val deliveryText: String = ""
)

/**
 * Tapping a product: a new line with quantity 1, or +1 on the line it already has
 * (the same product is always ONE line). The price of an existing line is left alone,
 * so a price the user typed is not overwritten.
 */
fun CartState.withProductAdded(
    productId: Long,
    name: String,
    unit: ProductUnit,
    sellPricePaisa: Long
): CartState {
    val index = lines.indexOfFirst { it.productId == productId }
    if (index < 0) {
        val line = CartLineInput(
            productId = productId,
            name = name,
            unit = unit,
            quantityText = "1",
            unitPriceText = formatTakaPlain(sellPricePaisa)
        )
        return copy(lines = lines + line)
    }

    val line = lines[index]
    val current = parseQuantity(line.quantityText) ?: 0L // an unreadable box counts as 0
    // +1 unit. A silly huge quantity is left as it is, so we never overflow.
    val next = if (current > Long.MAX_VALUE - QUANTITY_SCALE) current else current + QUANTITY_SCALE
    return copy(lines = lines.replaceAt(index, line.copy(quantityText = formatQuantityPlain(next))))
}

fun CartState.withQuantityText(productId: Long, text: String): CartState =
    copy(lines = lines.map { if (it.productId == productId) it.copy(quantityText = text) else it })

fun CartState.withUnitPriceText(productId: Long, text: String): CartState =
    copy(lines = lines.map { if (it.productId == productId) it.copy(unitPriceText = text) else it })

fun CartState.withoutLine(productId: Long): CartState =
    copy(lines = lines.filterNot { it.productId == productId })

fun CartState.withDiscountOn(on: Boolean): CartState = copy(discountOn = on)

fun CartState.withDiscountUnit(unit: DiscountUnit): CartState = copy(discountUnit = unit)

fun CartState.withDiscountText(text: String): CartState = copy(discountText = text)

fun CartState.withDeliveryOn(on: Boolean): CartState = copy(deliveryOn = on)

fun CartState.withDeliveryText(text: String): CartState = copy(deliveryText = text)

private fun <T> List<T>.replaceAt(index: Int, item: T): List<T> =
    toMutableList().also { it[index] = item }

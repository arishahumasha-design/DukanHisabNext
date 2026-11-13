package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.WarrantyUnit
import com.example.dukanhisabnext.sale.checkSaleDay
import com.example.dukanhisabnext.sale.needsOldDateConfirm
import java.time.LocalDate
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
    val unitPriceText: String, // taka, for ONE unit. Starts as the product's price; the user may edit
                               // it, and then it changes THIS sale only, never the product.
    /**
     * Version 5: this line's warranty for THIS sale (both null = none). Copied from the product's
     * default when the line is added; the cart may change, remove or add it, never the product.
     */
    val warrantyValue: Int? = null,
    val warrantyUnit: WarrantyUnit? = null
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
    val deliveryText: String = "",
    /** Version 5: the Dhaka day the sale belongs to. null = today (it follows the clock). */
    val saleDay: LocalDate? = null,
    /** The old day the shopkeeper already said "হ্যাঁ, ঠিক আছে" to (valid only while it is the saleDay). */
    val oldDateConfirmedDay: LocalDate? = null
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
    sellPricePaisa: Long,
    warrantyValue: Int? = null,
    warrantyUnit: WarrantyUnit? = null
): CartState {
    val index = lines.indexOfFirst { it.productId == productId }
    if (index < 0) {
        val line = CartLineInput(
            productId = productId,
            name = name,
            unit = unit,
            quantityText = "1",
            unitPriceText = formatTakaPlain(sellPricePaisa),
            warrantyValue = warrantyValue,
            warrantyUnit = warrantyUnit
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

/** Sets or removes (both null) the warranty of one line, for this sale only. */
fun CartState.withLineWarranty(productId: Long, value: Int?, unit: WarrantyUnit?): CartState =
    copy(lines = lines.map { if (it.productId == productId) it.copy(warrantyValue = value, warrantyUnit = unit) else it })

// ---- The sale date (version 5) ----

/** The day the sale belongs to: the chosen day, or [today]. */
fun CartState.saleDayOrToday(today: LocalDate): LocalDate = saleDay ?: today

/** True when the sale day is an earlier day than [today] (the cart shows the amber "পুরনো তারিখ"). */
fun CartState.isBackDated(today: LocalDate): Boolean = saleDay != null && saleDay.isBefore(today)

/** Picking [day]: today is stored as null ("today", following the clock). */
fun CartState.withSaleDay(day: LocalDate, today: LocalDate): CartState =
    copy(saleDay = if (day == today) null else day)

/** The shopkeeper said "হ্যাঁ, ঠিক আছে" to the old day [day]. */
fun CartState.withOldDateConfirmed(day: LocalDate): CartState = copy(oldDateConfirmedDay = day)

/**
 * Must the "আপনি অনেক পুরনো তারিখ দিয়েছেন, ঠিক আছে?" question be asked now? Only for a chosen day more
 * than 30 days before [today] (31 or older) that was not confirmed yet. Exactly 30 days never asks.
 * A day that grows older while the cart waits (a day passes) asks when it crosses the limit.
 */
fun CartState.needsOldDateQuestion(today: LocalDate): Boolean {
    val day = saleDay ?: return false
    return needsOldDateConfirm(day, today) && oldDateConfirmedDay != day
}

/**
 * The cart as read back from the disk: a saved sale day that is NOT allowed any more (after today,
 * for example the phone clock moved back, or before 1 January 2020) goes back to today. The second
 * value is true when that happened, so the screen can say so once.
 */
fun CartState.withValidSaleDay(today: LocalDate): Pair<CartState, Boolean> {
    val day = saleDay ?: return this to false
    return if (checkSaleDay(day, today) != null) copy(saleDay = null, oldDateConfirmedDay = null) to true else this to false
}

fun CartState.withDiscountOn(on: Boolean): CartState = copy(discountOn = on)

fun CartState.withDiscountUnit(unit: DiscountUnit): CartState = copy(discountUnit = unit)

fun CartState.withDiscountText(text: String): CartState = copy(discountText = text)

fun CartState.withDeliveryOn(on: Boolean): CartState = copy(deliveryOn = on)

fun CartState.withDeliveryText(text: String): CartState = copy(deliveryText = text)

private fun <T> List<T>.replaceAt(index: Int, item: T): List<T> =
    toMutableList().also { it[index] = item }

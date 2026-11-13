package com.example.dukanhisabnext.receipt

import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.WarrantyUnit
import com.example.dukanhisabnext.warranty.warrantyPeriodText
import com.example.dukanhisabnext.warranty.warrantyUntilDayText
import com.example.dukanhisabnext.util.DayPeriod
import com.example.dukanhisabnext.util.dayPeriod
import com.example.dukanhisabnext.util.formatDate
import com.example.dukanhisabnext.util.formatQuantity
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.formatTime12
import com.example.dukanhisabnext.util.toBengaliDigits

/**
 * All the words a receipt needs. They come from strings.xml on the phone (so no Bengali text is
 * written in Kotlin code); the tests pass their own. The functions below stay free of Android.
 */
data class ReceiptLabels(
    val shopPlaceholder: String,   // shown when the shop name is not filled in
    val mobile: String,
    val receiptNo: String,
    val date: String,
    val customer: String,
    val subtotal: String,
    val discount: String,
    val delivery: String,
    val grandTotal: String,        // মোট প্রদেয়
    val paid: String,
    val change: String,
    val saleDue: String,           // এই বিক্রির বাকি
    val customerTotalDue: String,
    val note: String,
    val cancelledNotice: String,   // বাতিল করা হয়েছে (the banner title; the shared text starts with it)
    val cancelReason: String,      // কারণ
    val taka: String,              // ৳
    val unit: (ProductUnit) -> String,
    val method: (PaymentMethod) -> String,
    val period: (DayPeriod) -> String,
    // ---- Version 5: the warranty caption under a product ("ওয়ারেন্টি: ৬ মাস (১৩ মে ২০২৭ পর্যন্ত)") ----
    val warrantyUnit: (WarrantyUnit) -> String = { it.name },
    val monthNames: List<String> = emptyList(),   // the 12 Bengali month names, January first
    val warrantyUntil: (day: String) -> String = { "$it পর্যন্ত" },        // "১৩ মে ২০২৭ পর্যন্ত"
    val warrantyCaption: (period: String, until: String) -> String = { p, u -> "ওয়ারেন্টি: $p ($u)" }
)

/** "৳ ১,২৩৪.৫০": the taka sign, then Bengali digits with Bangladeshi grouping. */
fun receiptMoney(paisa: Long, labels: ReceiptLabels): String = "${labels.taka} ${formatTaka(paisa)}"

/** "২.৫ কেজি" (the unit is left out only if it is unknown). Trailing zeros are dropped: 250 -> "০.২৫". */
fun receiptQuantity(line: ReceiptLine, labels: ReceiptLabels): String =
    formatQuantity(line.quantity) + (line.unit?.let { " " + labels.unit(it) } ?: "")

/** "১০/১১/২০২৬ রাত ১১:৪৫": the Asia/Dhaka date and 12-hour time, in Bengali digits. */
fun receiptDateTime(createdAt: Long, labels: ReceiptLabels): String =
    "${formatDate(createdAt)} ${labels.period(dayPeriod(createdAt))} ${formatTime12(createdAt)}"

/**
 * The caption under a product that has a warranty: "ওয়ারেন্টি: ৬ মাস (১৩ মে ২০২৭ পর্যন্ত)". null when
 * the line has no warranty (nothing is printed).
 */
fun receiptWarrantyCaption(line: ReceiptLine, labels: ReceiptLabels): String? {
    val value = line.warrantyValue ?: return null
    val unit = line.warrantyUnit ?: return null
    val until = line.warrantyUntil ?: return null
    return labels.warrantyCaption(
        warrantyPeriodText(value, unit, labels.warrantyUnit),
        labels.warrantyUntil(warrantyUntilDayText(until, labels.monthNames))
    )
}

/**
 * The date of the sale on the receipt: the sale date with its time of day, or the DATE ONLY when the
 * sale was back-dated (its time of day is the time of saving, not of selling).
 */
fun receiptSaleDate(data: ReceiptData, labels: ReceiptLabels): String =
    if (data.isBackDated) formatDate(data.saleDate) else receiptDateTime(data.saleDate, labels)

/**
 * For a back-dated sale: the real moment it was written ("১২/১১/২০২৬ রাত ৯:০৫"), shown on the receipt
 * SCREEN under the date as "লেখা হয়েছে …" (not in the shared text). null for a normal sale.
 */
fun receiptWrittenOn(data: ReceiptData, labels: ReceiptLabels): String? =
    if (data.isBackDated) receiptDateTime(data.createdAt, labels) else null

/** The label of the "পরিশোধ" line: the method is named only when something was paid. */
fun receiptPaidLabel(data: ReceiptData, labels: ReceiptLabels): String =
    if (data.showMethod) "${labels.paid} (${labels.method(data.paymentMethod)})" else labels.paid

/**
 * The "this sale was cancelled" notice: shown as a red banner on the receipt screen and as the
 * first lines of the shared text. [dateTime] is when it was cancelled (Dhaka), [reason] is already
 * labelled ("কারণ: ...") and null when no reason was given.
 */
data class CancelNotice(val title: String, val dateTime: String, val reason: String?)

/** null while the sale is active. */
fun cancelNotice(data: ReceiptData, labels: ReceiptLabels): CancelNotice? {
    val at = data.cancelledAt ?: return null
    return CancelNotice(
        title = labels.cancelledNotice,
        dateTime = receiptDateTime(at, labels),
        reason = data.cancelReason?.let { "${labels.cancelReason}: $it" }
    )
}

private const val SEPARATOR = "--------------------"

/**
 * The receipt as plain text, for the share sheet (WhatsApp, SMS...). No VAT line, no print
 * text, no advertising. Long product names are kept whole: each item is a name line followed by
 * a "quantity x price = total" line. Optional lines are left out when they do not apply.
 */
fun buildReceiptText(data: ReceiptData, labels: ReceiptLabels): String = buildString {
    fun money(paisa: Long) = receiptMoney(paisa, labels)

    // ---- A cancelled sale says so first ("বাতিল করা হয়েছে: <date time>", then the reason) ----
    cancelNotice(data, labels)?.let { notice ->
        appendLine("${notice.title}: ${notice.dateTime}")
        notice.reason?.let { appendLine(it) }
        appendLine(SEPARATOR)
    }

    // ---- Shop ----
    appendLine(data.shopName ?: labels.shopPlaceholder)
    data.shopAddress?.let { appendLine(it) }
    data.shopPhone?.let { appendLine("${labels.mobile}: ${toBengaliDigits(it)}") }
    appendLine(SEPARATOR)

    // ---- This sale ----
    appendLine("${labels.receiptNo}: ${toBengaliDigits(data.receiptNo)}")
    appendLine("${labels.date}: ${receiptSaleDate(data, labels)}")
    data.customerName?.let { name ->
        appendLine("${labels.customer}: $name")
        data.customerPhone?.let { appendLine("${labels.mobile}: ${toBengaliDigits(it)}") }
    }
    appendLine(SEPARATOR)

    // ---- Items ----
    data.lines.forEachIndexed { index, line ->
        appendLine("${toBengaliDigits((index + 1).toString())}. ${line.name}")
        appendLine("   ${receiptQuantity(line, labels)} × ${money(line.unitPrice)} = ${money(line.lineTotal)}")
        receiptWarrantyCaption(line, labels)?.let { appendLine("   $it") }
    }
    appendLine(SEPARATOR)

    // ---- Totals ----
    appendLine("${labels.subtotal}: ${money(data.subtotal)}")
    if (data.showDiscount) appendLine("${labels.discount}: -${money(data.discount)}")
    if (data.showDelivery) appendLine("${labels.delivery}: ${money(data.deliveryCharge)}")
    appendLine("${labels.grandTotal}: ${money(data.total)}")
    appendLine("${receiptPaidLabel(data, labels)}: ${money(data.paid)}")
    if (data.showChange) appendLine("${labels.change}: ${money(data.change)}")
    if (data.showSaleDue) appendLine("${labels.saleDue}: ${money(data.saleDue)}")
    data.customerTotalDue?.let { appendLine("${labels.customerTotalDue}: ${money(it)}") }
    data.note?.let { appendLine("${labels.note}: $it") }
}.trimEnd()

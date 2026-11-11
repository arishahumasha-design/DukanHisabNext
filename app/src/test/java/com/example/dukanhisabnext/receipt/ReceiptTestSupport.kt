package com.example.dukanhisabnext.receipt

import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.data.entity.SaleItem
import com.example.dukanhisabnext.data.entity.ShopProfile
import com.example.dukanhisabnext.util.DayPeriod
import java.time.Instant

// Shared helpers for the receipt tests: the Bengali words (the same as strings.xml) and
// small builders for sales, items and receipts.

/** 23:45 on 10 Nov 2026 in Dhaka (= 17:45 UTC). */
val SALE_TIME: Long = Instant.parse("2026-11-10T17:45:00Z").toEpochMilli()

val testLabels = ReceiptLabels(
    shopPlaceholder = "বিক্রয় রসিদ",
    mobile = "মোবাইল",
    receiptNo = "রসিদ নং",
    date = "তারিখ",
    customer = "কাস্টমার",
    subtotal = "সাব-টোটাল",
    discount = "ছাড়",
    delivery = "ডেলিভারি চার্জ",
    grandTotal = "মোট প্রদেয়",
    paid = "পরিশোধ",
    change = "ফেরত",
    saleDue = "এই বিক্রির বাকি",
    customerTotalDue = "আপনার মোট বাকি",
    note = "মন্তব্য",
    cancelledNotice = "বাতিল করা হয়েছে",
    cancelReason = "কারণ",
    taka = "৳",
    unit = {
        when (it) {
            ProductUnit.PIECE -> "পিস"
            ProductUnit.KG -> "কেজি"
            ProductUnit.LITRE -> "লিটার"
            ProductUnit.PACKET -> "প্যাকেট"
            ProductUnit.DOZEN -> "ডজন"
        }
    },
    method = {
        when (it) {
            PaymentMethod.CASH -> "নগদ"
            PaymentMethod.BKASH -> "বিকাশ"
            PaymentMethod.NAGAD -> "নগদ (মোবাইল)"
            PaymentMethod.BANK -> "ব্যাংক"
            PaymentMethod.OTHER -> "অন্যান্য"
        }
    },
    period = {
        when (it) {
            DayPeriod.MORNING -> "সকাল"
            DayPeriod.NOON -> "দুপুর"
            DayPeriod.AFTERNOON -> "বিকাল"
            DayPeriod.EVENING -> "সন্ধ্যা"
            DayPeriod.NIGHT -> "রাত"
        }
    }
)

/** Two products: 2.5 kg of rice at ৳40 = ৳100, and 3 pieces of soap at ৳50 = ৳150. */
val riceLine = ReceiptLine("চাল", 2_500, ProductUnit.KG, 4_000, 10_000)
val soapLine = ReceiptLine("সাবান", 3_000, ProductUnit.PIECE, 5_000, 15_000)

/** A plain receipt: no shop details, no customer, no discount, delivery, change, due or note. */
fun receipt(
    shopName: String? = null,
    shopAddress: String? = null,
    shopPhone: String? = null,
    customerName: String? = null,
    customerPhone: String? = null,
    lines: List<ReceiptLine> = listOf(riceLine, soapLine),
    subtotal: Long = 25_000,
    discount: Long = 0,
    deliveryCharge: Long = 0,
    total: Long = 25_000,
    paid: Long = 25_000,
    method: PaymentMethod = PaymentMethod.CASH,
    change: Long = 0,
    saleDue: Long = 0,
    customerTotalDue: Long? = null,
    note: String? = null,
    cancelledAt: Long? = null,
    cancelReason: String? = null,
    customerBalance: Long? = null
) = ReceiptData(
    shopName = shopName,
    shopAddress = shopAddress,
    shopPhone = shopPhone,
    receiptNo = "261110-001",
    createdAt = SALE_TIME,
    customerName = customerName,
    customerPhone = customerPhone,
    lines = lines,
    subtotal = subtotal,
    discount = discount,
    deliveryCharge = deliveryCharge,
    total = total,
    paid = paid,
    paymentMethod = method,
    change = change,
    saleDue = saleDue,
    customerTotalDue = customerTotalDue,
    note = note,
    saleId = 7,
    customerBalance = customerBalance,
    cancelledAt = cancelledAt,
    cancelReason = cancelReason
)

/** A saved sale as the database would give it. */
fun aSale(
    customerId: Long? = null,
    subtotal: Long = 25_000,
    discount: Long = 0,
    deliveryCharge: Long = 0,
    total: Long = 25_000,
    paid: Long = 25_000,
    method: PaymentMethod = PaymentMethod.CASH,
    due: Long = 0,
    note: String? = null
) = Sale(
    id = 7, receiptNo = "261110-001", customerId = customerId,
    subtotal = subtotal, discount = discount, deliveryCharge = deliveryCharge, total = total,
    paidAmount = paid, paymentMethod = method, dueAmount = due, note = note, createdAt = SALE_TIME
)

val savedItems = listOf(
    SaleItem(1, 7, 11, "চাল", 2_500, 4_000, 3_500, 10_000),
    SaleItem(2, 7, 12, "সাবান", 3_000, 5_000, null, 15_000)
)

val units = mapOf(11L to ProductUnit.KG, 12L to ProductUnit.PIECE)

fun customer(phone: String? = "01811000000") =
    Customer(id = 3, name = "রহিম", phone = phone, createdAt = 0, updatedAt = 0)

fun shop(name: String = "করিম স্টোর", address: String? = "বাজার রোড, ঢাকা", phone: String? = "01711000000") =
    ShopProfile(name = name, ownerName = "করিম", phone = phone, address = address)

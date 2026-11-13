package com.example.dukanhisabnext.receipt

import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.data.entity.SaleCancellation
import com.example.dukanhisabnext.data.entity.SaleItem
import com.example.dukanhisabnext.data.entity.ShopProfile

// A receipt is built from a SAVED sale (never from the cart), so the same receipt can be shown
// right after saving and again later from the sales list. This file is plain Kotlin (no Android)
// so it can be unit-tested on the PC. Money is Long paisa, quantity is Long thousandths.

/** One row of the receipt table. [name] is the product name as it was at the moment of sale. */
data class ReceiptLine(
    val name: String,
    val quantity: Long,       // thousandths
    val unit: ProductUnit?,   // null only if the product row is gone (cannot normally happen)
    val unitPrice: Long,      // paisa for one unit, as sold
    val lineTotal: Long       // paisa
)

/** Everything a receipt shows. Optional parts are null when they must not be shown. */
data class ReceiptData(
    // Shop header. null = not filled in (the receipt then shows a neutral placeholder).
    val shopName: String?,
    val shopAddress: String?,
    val shopPhone: String?,
    val receiptNo: String,
    val createdAt: Long,                // UTC millis: the real moment of saving
    // Customer, only if the sale has one.
    val customerName: String?,
    val customerPhone: String?,
    val lines: List<ReceiptLine>,
    val subtotal: Long,
    val discount: Long,
    val deliveryCharge: Long,
    val total: Long,                    // মোট প্রদেয়
    val paid: Long,
    val paymentMethod: PaymentMethod,   // only has meaning when paid > 0
    /**
     * The change (ফেরত) given back. It is NOT stored in the database: it is only known right
     * after a save (it travels in the route). A receipt opened later has 0 here.
     */
    val change: Long,
    val saleDue: Long,                  // এই বিক্রির বাকি
    /** The customer's CURRENT total due, from the ledger. null unless the sale has a customer who owes money. */
    val customerTotalDue: Long?,
    val note: String?,
    // ---- Needed for cancelling a sale (বিক্রি বাতিল) ----
    val saleId: Long = 0,
    /** The customer's real balance (can be 0 or negative); null when the sale has no customer. */
    val customerBalance: Long? = null,
    /** When the sale was cancelled (UTC millis), or null while it is still active. */
    val cancelledAt: Long? = null,
    val cancelReason: String? = null,
    /** Version 5: the date the sale belongs to (UTC millis, shown in Asia/Dhaka). The receipt shows this date. */
    val saleDate: Long = createdAt
) {
    val isCancelled: Boolean get() = cancelledAt != null
    val showChange: Boolean get() = shouldShowChange(change)
    val showDiscount: Boolean get() = discount > 0
    val showDelivery: Boolean get() = deliveryCharge > 0
    val showSaleDue: Boolean get() = saleDue > 0
    /** The payment method is only meaningful when something was paid (SPEC.md section 4). */
    val showMethod: Boolean get() = paid > 0
}

/** The "ফেরত" line is shown only when there really is change to give back (above 0). */
fun shouldShowChange(change: Long): Boolean = change > 0

/** Blank text means "not filled in". */
private fun String?.orNullIfBlank(): String? = this?.trim()?.ifEmpty { null }

/**
 * Builds the receipt from saved data.
 * [units] gives each product's unit by product id. [customerBalance] is the customer's current
 * balance from the ledger (positive = owes). [change] comes from the route and is 0 when the
 * receipt is opened later; a negative value is treated as 0.
 */
fun buildReceiptData(
    sale: Sale,
    items: List<SaleItem>,
    units: Map<Long, ProductUnit>,
    customer: Customer?,
    shop: ShopProfile?,
    customerBalance: Long?,
    change: Long,
    cancellation: SaleCancellation? = null
): ReceiptData = ReceiptData(
    shopName = shop?.name.orNullIfBlank(),
    shopAddress = shop?.address.orNullIfBlank(),
    shopPhone = shop?.phone.orNullIfBlank(),
    receiptNo = sale.receiptNo,
    createdAt = sale.createdAt,
    saleDate = sale.saleDate,
    customerName = customer?.name.orNullIfBlank(),
    customerPhone = if (customer == null) null else customer.phone.orNullIfBlank(),
    lines = items.map {
        ReceiptLine(
            name = it.productNameSnapshot,
            quantity = it.quantity,
            unit = units[it.productId],
            unitPrice = it.unitPrice,
            lineTotal = it.lineTotal
        )
    },
    subtotal = sale.subtotal,
    discount = sale.discount,
    deliveryCharge = sale.deliveryCharge,
    total = sale.total,
    paid = sale.paidAmount,
    paymentMethod = sale.paymentMethod,
    change = maxOf(change, 0L),
    saleDue = sale.dueAmount,
    // Only for a sale WITH a customer, and only while that customer still owes money.
    customerTotalDue = if (customer != null && customerBalance != null && customerBalance > 0) customerBalance else null,
    note = sale.note.orNullIfBlank(),
    saleId = sale.id,
    customerBalance = if (customer != null) customerBalance else null,
    cancelledAt = cancellation?.createdAt,
    cancelReason = cancellation?.reason.orNullIfBlank()
)

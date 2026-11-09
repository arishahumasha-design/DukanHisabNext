package com.example.dukanhisabnext.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One completed sale. Never edited or deleted after saving.
 * Rules (checked in code before saving, from M4):
 *   total = subtotal - discount + deliveryCharge
 *   dueAmount = total - paidAmount, and paidAmount never exceeds total.
 */
@Entity(
    tableName = "sales",
    foreignKeys = [
        ForeignKey(entity = Customer::class, parentColumns = ["id"], childColumns = ["customerId"])
    ],
    indices = [
        Index(value = ["receiptNo"], unique = true), // two sales can never share a receipt number
        Index("customerId")
    ]
)
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNo: String,
    val customerId: Long? = null,
    val subtotal: Long,        // paisa
    val discount: Long,        // paisa
    val deliveryCharge: Long,  // paisa
    val total: Long,           // paisa
    val paidAmount: Long,      // paisa
    val paymentMethod: PaymentMethod,
    val dueAmount: Long,       // paisa
    val note: String? = null,
    val createdAt: Long
)

/**
 * One line on a sale. The "snapshot" fields copy the product's name and cost
 * at the moment of sale, so old receipts stay correct if the product changes later.
 */
@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(entity = Sale::class, parentColumns = ["id"], childColumns = ["saleId"]),
        ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["productId"])
    ],
    indices = [Index("saleId"), Index("productId")]
)
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val productNameSnapshot: String,
    val quantity: Long,               // thousandths
    val unitPrice: Long,              // paisa per 1 unit
    val costPriceSnapshot: Long? = null, // paisa per 1 unit
    val lineTotal: Long               // paisa
)

package com.example.dukanhisabnext.data.entity

import androidx.room.ColumnInfo
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
        Index("customerId"),
        Index("saleDate") // version 5: every list and report cuts sales by their sale date
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
    /** The real moment the sale was saved (UTC millis). Never editable; used to find a sale again after a crash. */
    val createdAt: Long,
    /**
     * Version 5: the date the sale belongs to (UTC millis, shown in Asia/Dhaka). Today = the moment
     * of saving; an earlier day = that Dhaka day at the time of day of saving (saleDateFor). Every
     * report, list, receipt number, stock movement and due entry of the sale uses this date.
     * Old sales got saleDate = createdAt in MIGRATION_4_5. The SQL default 0 exists only because
     * SQLite needs one to add a NOT NULL column; the app always writes the real value.
     */
    @ColumnInfo(defaultValue = "0")
    val saleDate: Long = createdAt
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
    val lineTotal: Long,              // paisa
    /**
     * Version 5, the warranty of THIS line (a snapshot: a later change to the product never
     * touches it). All three are null (no warranty) or all three are set. [warrantyUntil] is the
     * last day of the warranty, a Dhaka calendar date stored as an epoch day (LocalDate.toEpochDay).
     */
    val warrantyValue: Int? = null,
    val warrantyUnit: WarrantyUnit? = null,
    val warrantyUntil: Long? = null
)

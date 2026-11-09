package com.example.dukanhisabnext.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One line in a customer's baki khata. Never edited or deleted: a mistake is
 * fixed by adding a CORRECTION_PLUS or CORRECTION_MINUS entry.
 */
@Entity(
    tableName = "ledger_entries",
    foreignKeys = [
        ForeignKey(entity = Customer::class, parentColumns = ["id"], childColumns = ["customerId"]),
        ForeignKey(entity = Sale::class, parentColumns = ["id"], childColumns = ["saleId"])
    ],
    indices = [Index("customerId"), Index("saleId")]
)
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val type: LedgerEntryType,
    val amount: Long,                    // paisa, always positive; the type says + or -
    val paymentMethod: PaymentMethod? = null, // only for PAYMENT_RECEIVED
    val saleId: Long? = null,            // only for SALE_DUE
    val note: String? = null,
    val entryDate: Long,  // the date the user picked (default now). Used for history and reports.
    val createdAt: Long   // the real moment the row was saved
)

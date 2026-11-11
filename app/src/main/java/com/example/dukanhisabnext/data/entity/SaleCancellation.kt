package com.example.dukanhisabnext.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A cancelled sale (বিক্রি বাতিল). The sale row itself is NEVER changed: cancelling adds one row
 * here, plus stock coming back and (if there was a due) a ledger entry taking the due away, all
 * in one transaction (SaleRepository.cancelSale).
 *
 * The unique index on [saleId] means a sale can be cancelled only once, even after a double tap
 * or a retry. Rows here are never edited or deleted.
 */
@Entity(
    tableName = "sale_cancellations",
    foreignKeys = [
        ForeignKey(entity = Sale::class, parentColumns = ["id"], childColumns = ["saleId"])
    ],
    indices = [Index(value = ["saleId"], unique = true)]
)
data class SaleCancellation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    /** Optional, trimmed, at most 200 characters. */
    val reason: String? = null,
    /** When the sale was cancelled (UTC millis). */
    val createdAt: Long
)

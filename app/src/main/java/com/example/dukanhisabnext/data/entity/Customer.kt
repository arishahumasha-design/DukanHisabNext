package com.example.dukanhisabnext.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A customer. Their due balance is NOT stored here: it is always computed from the ledger. */
@Entity(
    tableName = "customers",
    indices = [Index("name"), Index("phone")]
)
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

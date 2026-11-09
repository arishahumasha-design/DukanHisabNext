package com.example.dukanhisabnext.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The shop's own details, printed on receipts. There is only ever one row, with id = 1. */
@Entity(tableName = "shop_profile")
data class ShopProfile(
    @PrimaryKey val id: Long = SINGLE_ROW_ID,
    val name: String,
    val ownerName: String? = null,
    val phone: String? = null,
    val address: String? = null
) {
    companion object {
        const val SINGLE_ROW_ID = 1L
    }
}

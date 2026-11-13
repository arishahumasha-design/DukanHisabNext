package com.example.dukanhisabnext.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Reminder: money = Long paisa, quantity = Long thousandths, time = UTC epoch millis.

@Entity(tableName = "categories")
data class Category(
    // id = 0 tells Room "make a new id for me" when inserting.
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            // If a category is ever removed, its products simply lose the category.
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("name"), Index("categoryId")]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long? = null,
    val unit: ProductUnit,
    val sellPrice: Long,           // paisa per 1 unit
    val costPrice: Long? = null,   // paisa per 1 unit; null = unknown
    val lowStockLevel: Long? = null, // thousandths; null = no alert
    val isActive: Boolean = true,  // false = archived (hidden from selling)
    val createdAt: Long,
    val updatedAt: Long,
    /** Version 5: the default warranty for new sales. Both null = no warranty, or both set. */
    val warrantyValue: Int? = null,
    val warrantyUnit: WarrantyUnit? = null
)

/**
 * Every change to stock is one row here. A product's current stock is the
 * SUM of its rows. Rows are never edited or deleted (fix mistakes with ADJUSTMENT).
 */
@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["productId"]),
        ForeignKey(entity = Sale::class, parentColumns = ["id"], childColumns = ["referenceSaleId"])
    ],
    // (productId, quantity) is a "covering" index (version 4): the stock of a product is a SUM of
    // quantity per productId, and SQLite can read both from the index alone, without opening
    // each row. With a year of sales that took the product list from ~240 ms to ~5 ms (PC).
    indices = [Index("productId"), Index("referenceSaleId"), Index(value = ["productId", "quantity"])]
)
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val type: StockMovementType,
    val quantity: Long,            // thousandths; positive = in, negative = out
    val unitCost: Long? = null,    // paisa; filled for PURCHASE
    val referenceSaleId: Long? = null,
    val note: String? = null,
    /**
     * When the change happened (UTC millis). For a SALE row this is the sale's saleDate (version 5),
     * so a back-dated sale shows on its own day in the stock history; the real moment of saving is
     * on the sale (referenceSaleId -> sales.createdAt). Every other type: the moment it was saved.
     */
    val createdAt: Long
)

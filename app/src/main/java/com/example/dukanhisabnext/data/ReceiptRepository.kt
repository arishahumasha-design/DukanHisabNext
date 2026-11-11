package com.example.dukanhisabnext.data

import androidx.room.withTransaction
import com.example.dukanhisabnext.receipt.ReceiptData
import com.example.dukanhisabnext.receipt.buildReceiptData

/**
 * Loads a SAVED sale as a receipt: the sale, its items, the customer, the shop profile and the
 * customer's current balance. It never looks at the cart, so it works right after a save and
 * later from a sales list. All reads happen in one transaction, so they agree with each other.
 */
class ReceiptRepository(private val db: AppDatabase) {

    /**
     * Returns null when there is no sale with this id.
     * [change] is the ফেরত from the route (0 when the receipt is opened later).
     */
    suspend fun load(saleId: Long, change: Long): ReceiptData? = db.withTransaction {
        val sale = db.saleDao().getSale(saleId) ?: return@withTransaction null
        val items = db.saleDao().getItems(saleId)
        val units = db.productDao().getProducts(items.map { it.productId }.distinct())
            .associate { it.id to it.unit }
        val customer = sale.customerId?.let { db.customerDao().getById(it) }
        val balance = customer?.let { db.ledgerDao().getBalance(it.id) }
        val shop = db.shopProfileDao().get()
        val cancellation = db.saleCancellationDao().getForSale(saleId) // null while the sale is active

        buildReceiptData(sale, items, units, customer, shop, balance, change, cancellation)
    }
}

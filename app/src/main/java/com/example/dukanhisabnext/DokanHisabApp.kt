package com.example.dukanhisabnext

import android.app.Application
import com.example.dukanhisabnext.data.AppDatabase
import com.example.dukanhisabnext.data.CustomerRepository
import com.example.dukanhisabnext.data.ProductRepository
import com.example.dukanhisabnext.data.ReceiptRepository
import com.example.dukanhisabnext.data.SaleRepository
import com.example.dukanhisabnext.data.ShopRepository
import com.example.dukanhisabnext.ui.sell.CartStore

/**
 * Created once when the app starts. It owns the database and the repositories,
 * so every screen shares the same ones. "by lazy" means: create it the first
 * time it is needed, not at startup.
 * This is hand-written wiring, so no extra library like Hilt is needed.
 */
class DokanHisabApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val productRepository: ProductRepository by lazy { ProductRepository(database) }
    val customerRepository: CustomerRepository by lazy { CustomerRepository(database) }

    // One shared SaleRepository for the whole app: its lock and double-tap memory only
    // work if every screen uses the same one.
    val saleRepository: SaleRepository by lazy { SaleRepository(database) }

    val shopRepository: ShopRepository by lazy { ShopRepository(database) }
    val receiptRepository: ReceiptRepository by lazy { ReceiptRepository(database) }

    // The cart of the sale in progress. Held here so it survives rotation and tab switching.
    val cartStore: CartStore = CartStore()
}

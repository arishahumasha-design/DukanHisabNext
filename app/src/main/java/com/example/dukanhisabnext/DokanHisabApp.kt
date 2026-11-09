package com.example.dukanhisabnext

import android.app.Application
import com.example.dukanhisabnext.data.AppDatabase
import com.example.dukanhisabnext.data.CustomerRepository
import com.example.dukanhisabnext.data.ProductRepository

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
}

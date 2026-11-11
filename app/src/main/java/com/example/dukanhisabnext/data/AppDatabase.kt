package com.example.dukanhisabnext.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.dukanhisabnext.data.dao.CustomerDao
import com.example.dukanhisabnext.data.dao.ExpenseDao
import com.example.dukanhisabnext.data.dao.LedgerDao
import com.example.dukanhisabnext.data.dao.ProductDao
import com.example.dukanhisabnext.data.dao.SaleCancellationDao
import com.example.dukanhisabnext.data.dao.SaleDao
import com.example.dukanhisabnext.data.dao.ShopProfileDao
import com.example.dukanhisabnext.data.dao.StockMovementDao
import com.example.dukanhisabnext.data.entity.Category
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.data.entity.Expense
import com.example.dukanhisabnext.data.entity.ExpenseCategory
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.data.entity.SaleCancellation
import com.example.dukanhisabnext.data.entity.SaleItem
import com.example.dukanhisabnext.data.entity.ShopProfile
import com.example.dukanhisabnext.data.entity.StockMovement

/**
 * The app's one database file on the phone.
 *
 * "version" must go up by 1 whenever a table changes after real data exists,
 * together with a Migration that converts old data. We deliberately do NOT use
 * fallbackToDestructiveMigration(): that would silently wipe the shop's records.
 */
@Database(
    entities = [
        ShopProfile::class,
        Category::class,
        Product::class,
        StockMovement::class,
        Customer::class,
        Sale::class,
        SaleItem::class,
        LedgerEntry::class,
        ExpenseCategory::class,
        Expense::class,
        SaleCancellation::class // added in version 2 (see Migrations.kt)
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shopProfileDao(): ShopProfileDao
    abstract fun productDao(): ProductDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun customerDao(): CustomerDao
    abstract fun saleDao(): SaleDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun saleCancellationDao(): SaleCancellationDao

    companion object {
        const val FILE_NAME = "dokan_hisab.db"

        // @Volatile makes sure every thread sees the latest value of INSTANCE.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Returns the single shared database, creating it the first time. */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    FILE_NAME
                )
                    // Turns an older database file into the current version, keeping all data.
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

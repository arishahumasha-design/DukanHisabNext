package com.example.dukanhisabnext.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.dukanhisabnext.data.dao.CustomerDao
import com.example.dukanhisabnext.data.dao.ExpenseDao
import com.example.dukanhisabnext.data.dao.LedgerDao
import com.example.dukanhisabnext.data.dao.ProductDao
import com.example.dukanhisabnext.data.dao.SaleCancellationDao
import com.example.dukanhisabnext.data.dao.SaleDao
import com.example.dukanhisabnext.data.dao.ReportDao
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
        SaleCancellation::class // added in version 2; v3 changed the expense tables; v4 added a stock index; v5 sale date + warranty (see Migrations.kt)
    ],
    version = 5,
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
    abstract fun reportDao(): ReportDao // read-only queries for the reports (no table of its own)

    companion object {
        const val FILE_NAME = "dokan_hisab.db"

        // @Volatile makes sure every thread sees the latest value of INSTANCE.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * The shared database of the open shop ([file], from ShopPaths), creating the instance the first time.
         * One process has one open shop: switching shops restarts the app.
         */
        fun getInstance(context: Context, file: File): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: open(context, file).also { INSTANCE = it }
            }

        /**
         * A database of one shop's file (by its full path). Besides the shared instance this is used briefly for a
         * shop that is not open (making a new shop, editing or backing up another shop); the caller closes it.
         * Turns an older database file into the current version, keeping all data.
         */
        fun open(context: Context, file: File): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, file.absolutePath)
                .addMigrations(*ALL_MIGRATIONS)
                .openHelperFactory(KeepDamagedFileFactory())
                .build()

        /**
         * Closes the shared database so its files can be swapped by a restore. After this the app
         * must restart (the repositories still hold the closed one); the next getInstance opens
         * whatever file is then in place.
         */
        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}

/**
 * Android's default reaction to a damaged database file is to DELETE it and start empty. A shop's records must
 * never vanish like that: this factory keeps everything else of the normal behaviour but leaves a damaged file
 * where it is. The app's start-up check then shows the shop as "cannot be opened" (restore a backup or remove it).
 */
class KeepDamagedFileFactory(
    private val inner: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory()
) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val callback = configuration.callback
        val keeping = object : SupportSQLiteOpenHelper.Callback(callback.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = callback.onConfigure(db)
            override fun onCreate(db: SupportSQLiteDatabase) = callback.onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = callback.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = callback.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SupportSQLiteDatabase) = callback.onOpen(db)
            override fun onCorruption(db: SupportSQLiteDatabase) {
                // Deliberately nothing: the file stays (see the class comment).
            }
        }
        return inner.create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(keeping)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
                .build()
        )
    }
}

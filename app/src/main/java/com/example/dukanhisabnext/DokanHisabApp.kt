package com.example.dukanhisabnext

import android.app.Application
import androidx.core.content.pm.PackageInfoCompat
import com.example.dukanhisabnext.backup.BackupEngine
import com.example.dukanhisabnext.backup.BackupPaths
import com.example.dukanhisabnext.backup.RecoveryOutcome
import com.example.dukanhisabnext.data.AppDatabase
import com.example.dukanhisabnext.data.CustomerRepository
import com.example.dukanhisabnext.data.ExpenseRepository
import com.example.dukanhisabnext.data.LedgerRepository
import com.example.dukanhisabnext.data.ProductRepository
import com.example.dukanhisabnext.data.ReportRepository
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
    /**
     * What the start-up check found about a restore that was stopped half-way (a crash, the battery).
     * Set in onCreate, before anything can open the database.
     */
    var startupRecovery: RecoveryOutcome = RecoveryOutcome.NOTHING
        private set

    override fun onCreate() {
        super.onCreate()
        startupRecovery = try {
            BackupEngine.recover(BackupPaths(getDatabasePath(AppDatabase.FILE_NAME), filesDir))
        } catch (e: java.io.IOException) {
            // Files could not be moved back: leave the marker so the next start tries again.
            RecoveryOutcome.NOTHING
        }
    }

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    /** Backups and restores. Uses the same database file as the rest of the app. */
    val backupEngine: BackupEngine by lazy {
        val info = packageManager.getPackageInfo(packageName, 0)
        BackupEngine(
            context = this,
            getLive = { database },
            closeLive = { AppDatabase.closeInstance() },
            appVersionCode = PackageInfoCompat.getLongVersionCode(info),
            appVersionName = info.versionName.orEmpty()
        )
    }
    val productRepository: ProductRepository by lazy { ProductRepository(database) }
    val customerRepository: CustomerRepository by lazy { CustomerRepository(database) }

    // One shared SaleRepository for the whole app: its lock and double-tap memory only
    // work if every screen uses the same one.
    val saleRepository: SaleRepository by lazy { SaleRepository(database) }

    // One shared LedgerRepository too, for the same reason (lock and double-tap memory).
    val ledgerRepository: LedgerRepository by lazy { LedgerRepository(database) }
    val expenseRepository: ExpenseRepository by lazy { ExpenseRepository(database) }
    val reportRepository: ReportRepository by lazy { ReportRepository(database) }

    val shopRepository: ShopRepository by lazy { ShopRepository(database) }
    val receiptRepository: ReceiptRepository by lazy { ReceiptRepository(database) }

    // The cart of the sale in progress. Held here so it survives rotation and tab switching.
    val cartStore: CartStore = CartStore()
}

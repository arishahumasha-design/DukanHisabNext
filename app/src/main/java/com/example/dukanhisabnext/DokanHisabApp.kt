package com.example.dukanhisabnext

import android.app.Application
import android.content.Intent
import androidx.core.content.pm.PackageInfoCompat
import com.example.dukanhisabnext.backup.BackupEngine
import com.example.dukanhisabnext.backup.BackupManager
import com.example.dukanhisabnext.backup.BackupPrefs
import com.example.dukanhisabnext.backup.SUMMARY_TABLES
import com.example.dukanhisabnext.backup.startupNote
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
import com.example.dukanhisabnext.data.FileSaleDraftJournal
import com.example.dukanhisabnext.ui.sell.CartStorage
import com.example.dukanhisabnext.ui.sell.CartStore
import com.example.dukanhisabnext.ui.sell.FileCartStorage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        // A restore finished while the app was closed: the saved cart belongs to the old data
        // (product ids differ), so it goes before anything reads it.
        if (startupRecovery == RecoveryOutcome.FINISHED) cartStorage.delete()
        // The saved cart came back: take out what can no longer be sold (in the background).
        appScope.launch {
            cartStore.dropWhatCannotBeSold(
                sellableIds = { ids -> database.productDao().getProducts(ids).filter { it.isActive }.map { it.id }.toSet() },
                customerExists = { id -> database.customerDao().getById(id) != null }
            )
        }
    }

    /** Small background work that belongs to the app, not to a screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The sale in progress on the disk (app files, not the database and not the backup). */
    private val cartStorage: CartStorage by lazy { FileCartStorage(File(filesDir, "cart/cart.json")) }

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

    /** The small settings file: when the last backup was written. */
    val backupPrefs: BackupPrefs by lazy { BackupPrefs(this) }

    /**
     * Runs the backup and restore jobs for the screens (one at a time, outliving any screen). It also
     * holds the note shown once after a restart: the result of the last restore, or what the start-up
     * check found.
     */
    val backupManager: BackupManager by lazy {
        BackupManager(
            context = this,
            engine = backupEngine,
            prefs = backupPrefs,
            countRows = ::countShopRows,
            isLiveOpen = { database.isOpen },
            restartApp = ::restartProcess,
            // A restore or an undo replaced the data: the cart's product ids no longer fit.
            onDataReplaced = { cartStore.clear() },
            startup = startupNote(backupEngine.readRestoreResult(), startupRecovery, backupPrefs.restoreFailedNote)
        )
    }

    /** The row counts the restore summary and the Home reminder use. */
    private suspend fun countShopRows(): Map<String, Long> = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        SUMMARY_TABLES.associateWith { table ->
            db.query("SELECT COUNT(*) FROM `$table`").use { c -> c.moveToFirst(); c.getLong(0) }
        }
    }

    /** After a restore the repositories still hold the closed database: start the app again from scratch. */
    private fun restartProcess() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    val productRepository: ProductRepository by lazy { ProductRepository(database) }
    val customerRepository: CustomerRepository by lazy { CustomerRepository(database) }

    // One shared SaleRepository for the whole app: its lock and double-tap memory only
    // work if every screen uses the same one.
    // Its journal file remembers each cart's last save attempt, so a cart that comes back after
    // Android closed the app is never saved twice.
    val saleRepository: SaleRepository by lazy {
        SaleRepository(database, journal = FileSaleDraftJournal(File(filesDir, "cart/sale-attempts.txt")))
    }

    // One shared LedgerRepository too, for the same reason (lock and double-tap memory).
    val ledgerRepository: LedgerRepository by lazy { LedgerRepository(database) }
    val expenseRepository: ExpenseRepository by lazy { ExpenseRepository(database) }
    val reportRepository: ReportRepository by lazy { ReportRepository(database) }

    val shopRepository: ShopRepository by lazy { ShopRepository(database) }
    val receiptRepository: ReceiptRepository by lazy { ReceiptRepository(database) }

    // The cart of the sale in progress. Held here so it survives rotation and tab switching, and
    // kept in a file so it survives Android closing the app. Lazy: it is read only after onCreate's
    // start-up check (a finished restore deletes the old cart first).
    val cartStore: CartStore by lazy { CartStore(storage = cartStorage) }
}

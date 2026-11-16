package com.example.dukanhisabnext

import com.example.dukanhisabnext.util.withLanguage
import android.app.Application
import android.content.Intent
import androidx.core.content.pm.PackageInfoCompat
import com.example.dukanhisabnext.backup.BackupEngine
import com.example.dukanhisabnext.backup.BackupManager
import com.example.dukanhisabnext.backup.BackupPrefs
import com.example.dukanhisabnext.backup.SUMMARY_TABLES
import com.example.dukanhisabnext.backup.startupNote
import com.example.dukanhisabnext.shop.ShopEntry
import com.example.dukanhisabnext.shop.ShopHealth
import com.example.dukanhisabnext.shop.ShopPaths
import com.example.dukanhisabnext.shop.ShopRegistry
import com.example.dukanhisabnext.shop.ShopStore
import com.example.dukanhisabnext.shop.markedOpened
import com.example.dukanhisabnext.shop.renamed
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
        displaySettings.resolveAtStart() // before anything formats a number or reads a string
        // The default expense category names of both languages, so an untouched default shows in the current one.
        com.example.dukanhisabnext.expense.DefaultCategoryNames.byLanguage = com.example.dukanhisabnext.util.AppLanguage.entries.associateWith { language ->
            withLanguage(language).resources.getStringArray(R.array.expense_default_categories).toList()
        }
        // The shops: finish a delete that a crash stopped, then the open shop (see shop/ShopStore.kt).
        shopRegistry = try {
            shopStore.finishPendingDeletes()
        } catch (e: java.io.IOException) {
            shopStore.load()
        }
        currentShop = shopRegistry.selected
        shopPaths = shopStore.paths(currentShop.id)
        // Each shop has its own restore marker: a restore stopped by a crash is repaired in that shop only.
        for (shop in shopRegistry.active) {
            val paths = shopStore.paths(shop.id)
            val outcome = try {
                BackupEngine.recover(paths.backupPaths)
            } catch (e: java.io.IOException) {
                // Files could not be moved back: leave the marker so the next start tries again.
                RecoveryOutcome.NOTHING
            }
            // A restore finished while the app was closed: that shop's saved cart belongs to the old data
            // (product ids differ), so it goes before anything reads it.
            if (outcome == RecoveryOutcome.FINISHED) FileCartStorage(paths.cartFile).delete()
            if (shop.id == currentShop.id) startupRecovery = outcome
        }
        shopHealth = checkOpenShop()
        // PDFs made earlier hold names and amounts: none stays on the phone after the app was closed.
        appScope.launch { com.example.dukanhisabnext.pdf.clearPdfFolder(cacheDir) }
        // A shop that cannot be opened: nothing may touch its database (the shop list explains it).
        if (shopHealth != ShopHealth.OK) return
        // The saved cart came back: take out what can no longer be sold (in the background).
        appScope.launch {
            cartStore.dropWhatCannotBeSold(
                sellableIds = { ids -> database.productDao().getProducts(ids).filter { it.isActive }.map { it.id }.toSet() },
                customerExists = { id -> database.customerDao().getById(id) != null }
            )
        }
    }

    /** The shops of this phone (registry, file layout). */
    val shopStore: ShopStore by lazy {
        ShopStore(getDatabasePath(AppDatabase.FILE_NAME).parentFile!!, filesDir, deletePrefs = { name -> deleteSharedPreferences(name) })
    }

    /** The registry as it was at start (it changes only through ShopStore; a switch restarts the app). */
    lateinit var shopRegistry: ShopRegistry
        private set

    /** The open shop. Everything below (database, cart, journal, backup) belongs to it alone. */
    lateinit var currentShop: ShopEntry
        private set

    lateinit var shopPaths: ShopPaths
        private set

    /** What the start-up check found about the open shop's database file. */
    var shopHealth: ShopHealth = ShopHealth.OK
        private set

    /**
     * Opens the open shop's database once at start (this runs the database conversions of an update) and checks
     * it. A file that is gone, cannot be opened or is damaged is reported, never replaced or deleted.
     * Also keeps the registry's copy of the shop name in step with the shop's own profile.
     */
    private fun checkOpenShop(): ShopHealth {
        if (shopStore.fileHealth(currentShop) == ShopHealth.MISSING) return ShopHealth.MISSING
        val ok = try {
            val db = database.openHelper.writableDatabase
            val check = db.query("PRAGMA quick_check").use { c -> if (c.moveToFirst()) c.getString(0) else null }
            val profile = db.query("SELECT name, address FROM shop_profile WHERE id = 1").use { c ->
                if (c.moveToFirst()) (c.getString(0) ?: "") to c.getString(1) else null
            }
            var registry = shopRegistry
            if (!currentShop.opened) registry = registry.markedOpened(currentShop.id)
            if (profile != null && profile.first.isNotBlank() &&
                (profile.first != currentShop.name || profile.second?.ifBlank { null } != currentShop.address)
            ) {
                registry = registry.renamed(currentShop.id, profile.first, profile.second)
            }
            if (registry != shopRegistry) {
                shopStore.save(registry)
                shopRegistry = registry
                currentShop = registry.selected
            }
            check == "ok"
        } catch (e: Exception) {
            false
        }
        if (!ok) AppDatabase.closeInstance()
        return if (ok) ShopHealth.OK else ShopHealth.CANNOT_OPEN
    }

    /**
     * Making, editing, switching and deleting shops. A switch is refused while a backup/restore runs or a sale or
     * payment is being saved; otherwise the choice is saved, the open database closed and the app restarted (it
     * then opens the chosen shop and lands on Home).
     */
    val shopManager: com.example.dukanhisabnext.shop.ShopManager by lazy {
        com.example.dukanhisabnext.shop.ShopManager(
            context = this,
            store = shopStore,
            backupBusy = { backupEngine.isBusy || !com.example.dukanhisabnext.backup.canStartJob(backupManager.state.value) },
            saving = { shopHealth == ShopHealth.OK && (saleRepository.isSaving || ledgerRepository.isSaving) },
            restart = {
                AppDatabase.closeInstance()
                restartProcess()
            },
            saveOpenShopProfile = { profile -> shopRepository.save(profile) }
        )
    }

    /**
     * A backup engine for shop [id] that is NOT open (the delete dialog's "Back up this shop first"): its database
     * is opened only for the backup. Call the returned close function afterwards.
     */
    fun engineForShop(id: Int): Pair<BackupEngine, () -> Unit> {
        val paths = shopStore.paths(id)
        var db: AppDatabase? = null
        val info = packageManager.getPackageInfo(packageName, 0)
        val engine = BackupEngine(
            context = this,
            getLive = { db ?: AppDatabase.open(this, paths.database).also { db = it } },
            closeLive = { db?.close(); db = null },
            appVersionCode = PackageInfoCompat.getLongVersionCode(info),
            appVersionName = info.versionName.orEmpty(),
            displayCurrency = { com.example.dukanhisabnext.util.CurrentCurrency.value.code },
            liveDb = paths.database,
            workRoot = paths.workRoot,
            shopId = id,
            shopName = { shopStore.load().find(id)?.name?.ifBlank { null } }
        )
        return engine to { db?.close(); db = null }
    }

    /** The open shop's name as it is now (an edit in this session changes it; read from the registry). */
    fun currentShopName(): String? = try {
        shopStore.load().find(currentShop.id)?.name?.ifBlank { null }
    } catch (e: java.io.IOException) {
        currentShop.name.ifBlank { null }
    }

    /** Small background work that belongs to the app, not to a screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The sale in progress on the disk (app files, not the database and not the backup). */
    private val cartStorage: CartStorage by lazy { FileCartStorage(shopPaths.cartFile) }

    val database: AppDatabase by lazy { AppDatabase.getInstance(this, shopPaths.database) }

    /** Backups and restores. Uses the same database file as the rest of the app. */
    val backupEngine: BackupEngine by lazy {
        val info = packageManager.getPackageInfo(packageName, 0)
        BackupEngine(
            context = this,
            getLive = { database },
            closeLive = { AppDatabase.closeInstance() },
            appVersionCode = PackageInfoCompat.getLongVersionCode(info),
            appVersionName = info.versionName.orEmpty(),
            displayCurrency = { com.example.dukanhisabnext.util.CurrentCurrency.value.code },
            liveDb = shopPaths.database,
            workRoot = shopPaths.workRoot,
            shopId = currentShop.id,
            shopName = ::currentShopName
        )
    }

    /** The small settings file: when the last backup was written. */
    val backupPrefs: BackupPrefs by lazy { BackupPrefs(this, shopPaths.backupPrefsName) }

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
            // The user chose to show the currency the backup was made with (display only, no amount changes).
            onCurrencyChosen = { code -> com.example.dukanhisabnext.util.AppCurrency.fromCode(code)?.let(displaySettings::chooseCurrency) },
            shopName = ::currentShopName,
            startup = startupNote(backupEngine.readRestoreResult(), startupRecovery, backupPrefs.restoreFailedNote)
        )
    }

    /** The row counts the restore summary and the Home reminder use. */
    private suspend fun countShopRows(): Map<String, Long> = withContext(Dispatchers.IO) {
        // A shop that cannot be opened has nothing to count (and its database must not be opened, or made).
        if (shopHealth != ShopHealth.OK) return@withContext SUMMARY_TABLES.associateWith { 0L }
        val db = database.openHelper.readableDatabase
        SUMMARY_TABLES.associateWith { table ->
            db.query("SELECT COUNT(*) FROM `$table`").use { c -> c.moveToFirst(); c.getLong(0) }
        }
    }

    /** True when the shop already has sales, customers or products (the currency warning uses it). */
    suspend fun hasShopData(): Boolean {
        val counts = countShopRows()
        return com.example.dukanhisabnext.backup.hasShopData(counts["sales"] ?: 0, counts["customers"] ?: 0, counts["products"] ?: 0)
    }

    /** After a restore the repositories still hold the closed database: start the app again from scratch. */
    private fun restartProcess() {
        appLock.beforeRestart() // an unlocked app is not asked again after its own restart (shop switch, restore)
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    /** The optional app lock (Settings). Its file is not in any backup. */
    val appLock: com.example.dukanhisabnext.security.AppLock by lazy { com.example.dukanhisabnext.security.AppLock(this) }

    val productRepository: ProductRepository by lazy { ProductRepository(database) }
    val customerRepository: CustomerRepository by lazy { CustomerRepository(database) }

    // One shared SaleRepository for the whole app: its lock and double-tap memory only
    // work if every screen uses the same one.
    // Its journal file remembers each cart's last save attempt, so a cart that comes back after
    // Android closed the app is never saved twice.
    val saleRepository: SaleRepository by lazy {
        SaleRepository(database, journal = FileSaleDraftJournal(shopPaths.journalFile))
    }

    // One shared LedgerRepository too, for the same reason (lock and double-tap memory).
    val ledgerRepository: LedgerRepository by lazy { LedgerRepository(database) }
    val expenseRepository: ExpenseRepository by lazy { ExpenseRepository(database) }
    val reportRepository: ReportRepository by lazy { ReportRepository(database) }

    val shopRepository: ShopRepository by lazy { ShopRepository(database) }

    /**
     * The app language (Bengali or English). On Android 13+ the system's per-app language setting is read
     * too, because the user may also change it in the phone's own settings. The choice is kept in its own
     * small file (language_prefs), which is not part of any backup.
     */
    val displaySettings: com.example.dukanhisabnext.util.DisplaySettings by lazy {
        com.example.dukanhisabnext.util.DisplaySettings(
            store = com.example.dukanhisabnext.util.SharedPrefsDisplayStore(this),
            systemTag = {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    getSystemService(android.app.LocaleManager::class.java).applicationLocales.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
                } else {
                    null
                }
            }
        )
    }

    /** Makes the PDF documents (read-only) into cache/pdf/. */
    val pdfService: com.example.dukanhisabnext.pdf.PdfExportService by lazy {
        com.example.dukanhisabnext.pdf.PdfExportService(this, shopRepository, ledgerRepository, customerRepository, productRepository)
    }
    val receiptRepository: ReceiptRepository by lazy { ReceiptRepository(database) }

    // The cart of the sale in progress. Held here so it survives rotation and tab switching, and
    // kept in a file so it survives Android closing the app. Lazy: it is read only after onCreate's
    // start-up check (a finished restore deletes the old cart first).
    val cartStore: CartStore by lazy { CartStore(storage = cartStorage) }
}

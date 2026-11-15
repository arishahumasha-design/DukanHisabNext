package com.example.dukanhisabnext.shop

import com.example.dukanhisabnext.backup.BackupPaths
import java.io.File

/**
 * Where the files of shop [id] are. The ONE place that decides it.
 *
 * Shop 1 ([LEGACY_SHOP_ID]) is the shop of an install from before several shops existed: it keeps exactly the
 * old names, so an update neither copies nor renames anything:
 *   databases/dokan_hisab.db, files/cart/cart.json, files/cart/sale-attempts.txt,
 *   files/backup-work/, files/restore-safety/, files/restore.state, files/restore.result,
 *   shared_prefs/backup_prefs.xml
 * Every other shop has its own folders:
 *   databases/shops/shop-<id>.db, files/shops/<id>/ (cart, journal, backup work, restore files),
 *   shared_prefs/backup_prefs_shop_<id>.xml
 */
class ShopPaths(val id: Int, databasesDir: File, private val filesDir: File) {

    val isLegacy: Boolean = id == LEGACY_SHOP_ID

    val database: File =
        if (isLegacy) File(databasesDir, LEGACY_DATABASE_NAME) else File(File(databasesDir, SHOPS_FOLDER), "shop-$id.db")

    /** The folder the backup engine and the cart work in. For shop 1 it is the app's files folder itself. */
    val workRoot: File = if (isLegacy) filesDir else File(File(filesDir, SHOPS_FOLDER), id.toString())

    val cartFile: File = File(workRoot, "cart/cart.json")
    val journalFile: File = File(workRoot, "cart/sale-attempts.txt")
    val backupPaths: BackupPaths = BackupPaths(database, workRoot)

    /** The name of this shop's backup settings file (shared_prefs, without ".xml"). */
    val backupPrefsName: String = if (isLegacy) LEGACY_BACKUP_PREFS else "${LEGACY_BACKUP_PREFS}_shop_$id"

    /**
     * Every file and folder of this shop, for a delete. For shop 1 these are the single old names, NEVER the
     * whole files folder (which holds the other shops and the registry).
     */
    fun ownedFiles(): List<File> {
        val db = listOf(
            database,
            File(database.path + "-wal"), File(database.path + "-shm"), File(database.path + "-journal"),
            backupPaths.prepared, File(backupPaths.prepared.path + "-wal"), File(backupPaths.prepared.path + "-shm")
        )
        val work = if (isLegacy) {
            listOf(File(filesDir, "cart"), backupPaths.workDir, backupPaths.safetyDir, backupPaths.marker, backupPaths.resultFile)
        } else {
            listOf(workRoot)
        }
        return db + work
    }

    companion object {
        const val LEGACY_DATABASE_NAME = "dokan_hisab.db"
        const val LEGACY_BACKUP_PREFS = "backup_prefs"
        const val SHOPS_FOLDER = "shops"
    }
}

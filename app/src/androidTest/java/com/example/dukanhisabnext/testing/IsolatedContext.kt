package com.example.dukanhisabnext.testing

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * The context EVERY phone test that needs files, a database file or settings must use.
 *
 * It looks like the app's context (so Room, the backup engine and the resources work), but every
 * folder it hands out (files, cache, databases, no-backup, external files) is inside a temporary
 * root, cache/isolated-phone-tests/<label>, a folder the app never uses for anything: not its
 * databases, shared_prefs or files folders. (The test process runs with the APP's user id, so it
 * cannot write into the test package's own folder; the app's cache folder is the place it may write
 * to without touching real data.) So a test can neither read nor change the shop's real database,
 * its SharedPreferences, the cart file or the sale-attempts file, even by mistake or when it crashes
 * halfway: the root is deleted by [cleanUp] and, if a crash skipped that, by the next test's [create]
 * (each label has one fixed folder).
 *
 * SharedPreferences are NOT available here: asking for them throws, because the only ones an app test
 * could reach by name (backup_prefs, ...) are the app's real settings. A test that needs a settings
 * file passes its own in memory.
 *
 * PhoneTestIsolationGuardTest (PC) fails if a phone test uses the real app context directly.
 */
class IsolatedContext private constructor(base: Context, val root: File) : ContextWrapper(base) {

    companion object {
        /** The folder (inside the app's cache folder) that holds every isolated root. The app never uses this name. */
        const val FOLDER = "isolated-phone-tests"

        /**
         * A fresh isolated context for [label] (one folder per label, emptied first). Call [cleanUp] in
         * an @After; if a crash skips it, the next [create] with the same label starts clean.
         */
        fun create(base: Context, label: String): IsolatedContext {
            val root = File(File(base.cacheDir, FOLDER), label)
            root.deleteRecursively()
            root.mkdirs()
            return IsolatedContext(base, root)
        }
    }

    private fun dir(name: String): File = File(root, name).also { it.mkdirs() }

    /** Deletes everything this context made. Safe to call twice. */
    fun cleanUp() {
        root.deleteRecursively()
    }

    // ---- the folders: all inside [root] ----

    override fun getFilesDir(): File = dir("files")
    override fun getCacheDir(): File = dir("cache")
    override fun getCodeCacheDir(): File = dir("code_cache")
    override fun getNoBackupFilesDir(): File = dir("no_backup")
    override fun getDataDir(): File = root
    override fun getDir(name: String, mode: Int): File = dir("app_$name")
    override fun getExternalFilesDir(type: String?): File = dir("external_files")
    override fun getExternalCacheDir(): File = dir("external_cache")

    // ---- databases ----

    override fun getDatabasePath(name: String): File {
        val file = File(dir("databases"), name)
        file.parentFile?.mkdirs()
        return file
    }

    override fun databaseList(): Array<String> = dir("databases").list() ?: emptyArray()

    override fun deleteDatabase(name: String): Boolean = SQLiteDatabase.deleteDatabase(getDatabasePath(name))

    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
        errorHandler: DatabaseErrorHandler?
    ): SQLiteDatabase = SQLiteDatabase.openDatabase(
        getDatabasePath(name).path, factory, SQLiteDatabase.CREATE_IF_NECESSARY, errorHandler
    )

    // ---- settings: refused ----

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        throw UnsupportedOperationException(
            "An isolated test context has no SharedPreferences (\"$name\" would be the app's real settings). Use an in-memory fake."
        )

    // Room, WorkManager and friends ask for the application context: it must be this one, not the real app.
    override fun getApplicationContext(): Context = this
}

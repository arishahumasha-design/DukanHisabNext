package com.example.dukanhisabnext.shop

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** What the start-up check found about a shop's database file. */
enum class ShopHealth {
    OK,

    /** The shop was opened before, but its database file is gone. It is NOT replaced by an empty one. */
    MISSING,

    /** The file is there but cannot be opened (damaged, or an update could not convert it). */
    CANNOT_OPEN
}

/**
 * The registry on disk: files/shop-registry/shops.properties, written atomically (a temporary file, then a
 * rename) with the previous version kept as shops.properties.bak. Plain file work (no Android), so the phone
 * tests can point it at an isolated folder.
 *
 * [deletePrefs] removes a shop's backup settings file (Context.deleteSharedPreferences in the app).
 */
class ShopStore(
    val databasesDir: File,
    val filesDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val deletePrefs: (String) -> Unit = {}
) {
    val registryDir = File(filesDir, REGISTRY_FOLDER)
    val registryFile = File(registryDir, "shops.properties")
    val backupFile = File(registryDir, "shops.properties.bak")

    fun paths(id: Int): ShopPaths = ShopPaths(id, databasesDir, filesDir)

    /**
     * The registry. The first start after the update (no registry yet) registers the one existing database as
     * shop 1 WITHOUT opening, copying or changing it. A damaged registry is read from its .bak copy; if both are
     * unusable it is rebuilt from the database files that exist (names come back when each shop is opened).
     */
    @Synchronized
    fun load(): ShopRegistry {
        read(registryFile)?.let { return it }
        read(backupFile)?.let { save(it); return it }
        val rebuilt = rebuild()
        save(rebuilt)
        return rebuilt
    }

    private fun read(file: File): ShopRegistry? = try {
        if (file.isFile) parseRegistry(file.readText(Charsets.UTF_8)) else null
    } catch (e: IOException) {
        null
    }

    /** Shop 1 (always: an old install, or a new one whose file is made at its first start) plus every shops/shop-<n>.db found. */
    private fun rebuild(): ShopRegistry {
        val now = clock()
        val legacy = ShopRegistry.legacy(now, opened = paths(LEGACY_SHOP_ID).database.exists())
        val found = File(databasesDir, ShopPaths.SHOPS_FOLDER).listFiles().orEmpty()
            .mapNotNull { f -> Regex("shop-(\\d+)\\.db").matchEntire(f.name)?.groupValues?.get(1)?.toIntOrNull() }
            .filter { it > LEGACY_SHOP_ID }
            .sorted()
            .take(MAX_SHOPS - 1)
        if (found.isEmpty()) return legacy
        val others = found.map { id -> ShopEntry(id, name = "", createdAt = now, opened = true) }
        return legacy.copy(shops = legacy.shops + others, nextId = found.max() + 1)
    }

    /** Writes [registry]: temporary file (synced), the old file becomes the .bak, then the rename. */
    @Synchronized
    fun save(registry: ShopRegistry) {
        if (!registryDir.isDirectory && !registryDir.mkdirs()) throw IOException("cannot make ${registryDir.name}")
        val tmp = File(registryDir, "shops.properties.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(writeRegistry(registry).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (registryFile.exists()) registryFile.copyTo(backupFile, overwrite = true)
        if (!tmp.renameTo(registryFile)) {
            tmp.copyTo(registryFile, overwrite = true)
            tmp.delete()
        }
    }

    /** MISSING when the shop was opened before and its database file is gone; otherwise OK (opening it is checked by the app). */
    fun fileHealth(entry: ShopEntry): ShopHealth =
        if (entry.opened && !paths(entry.id).database.exists()) ShopHealth.MISSING else ShopHealth.OK

    /**
     * Deletes shop [id] for good: the entry is marked first, then its files go, then the entry. A crash in the
     * middle is finished by [finishPendingDeletes] at the next start. The caller has checked [checkDelete].
     */
    @Synchronized
    fun delete(id: Int): ShopRegistry {
        val registry = load()
        checkDelete(registry, id)?.let { throw IllegalStateException("cannot delete shop $id: $it") }
        save(registry.markedDeleting(id))
        return finishPendingDeletes()
    }

    /** Finishes every delete that was started (run at each start). Returns the registry afterwards. */
    @Synchronized
    fun finishPendingDeletes(): ShopRegistry {
        var registry = load()
        for (entry in registry.shops.filter { it.state == ShopState.DELETING }) {
            if (entry.id == registry.selectedId) continue // never the open shop (parseRegistry already moved away from it)
            val paths = paths(entry.id)
            for (file in paths.ownedFiles()) {
                if (file.exists() && !file.deleteRecursively()) throw IOException("cannot delete ${file.name}")
            }
            deletePrefs(paths.backupPrefsName)
            registry = registry.without(entry.id)
            save(registry)
        }
        return registry
    }

    companion object {
        const val REGISTRY_FOLDER = "shop-registry"
    }
}

package com.example.dukanhisabnext.shop

import android.content.Context
import com.example.dukanhisabnext.data.AppDatabase
import com.example.dukanhisabnext.data.entity.ShopProfile
import java.io.File

/** Why a switch to another shop cannot happen now. */
enum class SwitchBlock {
    /** A backup or restore is running: it belongs to the open shop and must finish first. */
    BACKUP_RUNNING,

    /** A sale, payment or due is being saved right now. */
    SAVING,

    /** It is already the open shop. */
    ALREADY_OPEN,
    NOT_FOUND
}

/** null = the app may switch to shop [targetId] now. Plain rule, tested on the PC. */
fun switchBlock(registry: ShopRegistry, targetId: Int, backupBusy: Boolean, saving: Boolean): SwitchBlock? = when {
    registry.active.none { it.id == targetId } -> SwitchBlock.NOT_FOUND
    targetId == registry.selectedId -> SwitchBlock.ALREADY_OPEN
    backupBusy -> SwitchBlock.BACKUP_RUNNING
    saving -> SwitchBlock.SAVING
    else -> null
}

/** What making or renaming a shop can run into. */
sealed interface ShopChange {
    data class Done(val shop: ShopEntry) : ShopChange
    data class BadName(val problem: ShopNameProblem) : ShopChange
    data object TooMany : ShopChange
}

/** The details of a shop as typed in the shop form (already checked by validateShopForm). */
data class ShopDetails(val name: String, val address: String?, val ownerName: String?, val phone: String?)

/**
 * Making, editing, switching and deleting shops. The open shop's database belongs to the app (one per process);
 * a shop that is not open is opened only briefly here (and closed again) to write its profile.
 *
 * [isBusy] says whether a backup/restore runs or something is being saved; [restart] closes the open database
 * and starts the app again (it then opens the newly selected shop and lands on Home).
 */
class ShopManager(
    private val context: Context,
    private val store: ShopStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val backupBusy: () -> Boolean = { false },
    private val saving: () -> Boolean = { false },
    private val restart: () -> Unit = {},
    /** Writes the profile of the OPEN shop through the app's own database (null: open the file like any other shop). */
    private val saveOpenShopProfile: (suspend (ShopProfile) -> Unit)? = null
) {
    fun registry(): ShopRegistry = store.load()

    /** Makes a new shop: its own database file with its profile, then the registry entry. It is not opened. */
    suspend fun create(details: ShopDetails): ShopChange {
        val registry = store.load()
        if (!canAddShop(registry)) return ShopChange.TooMany
        checkShopName(registry, details.name)?.let { return ShopChange.BadName(it) }
        val paths = store.paths(registry.nextId)
        check(!paths.isLegacy) { "a new shop never uses the old file names" }
        // Left-overs of a make that a crash stopped (the id was never registered): they are not a shop.
        for (f in paths.ownedFiles()) f.deleteRecursively()
        writeProfile(paths.database, details)
        val (after, entry) = registry.withNewShop(details.name, details.address, clock())
        store.save(after)
        return ShopChange.Done(entry)
    }

    /** Changes a shop's details: its own profile and the registry's copy of the name and address. */
    suspend fun edit(id: Int, details: ShopDetails): ShopChange {
        val registry = store.load()
        val entry = registry.active.firstOrNull { it.id == id } ?: return ShopChange.BadName(ShopNameProblem.EMPTY)
        checkShopName(registry, details.name, exceptId = id)?.let { return ShopChange.BadName(it) }
        val profile = profileOf(details)
        if (id == registry.selectedId && saveOpenShopProfile != null) {
            saveOpenShopProfile.invoke(profile)
        } else {
            writeProfile(store.paths(id).database, details)
        }
        val after = registry.renamed(id, details.name, details.address)
        store.save(after)
        return ShopChange.Done(after.find(id) ?: entry)
    }

    /** The details of shop [id] (the form is filled with them). Reads the open shop's profile through [readOpen]. */
    suspend fun details(id: Int, readOpen: suspend () -> ShopProfile?): ShopDetails? {
        val registry = store.load()
        val entry = registry.active.firstOrNull { it.id == id } ?: return null
        val profile = if (id == registry.selectedId) readOpen() else readProfile(store.paths(id).database)
        return ShopDetails(
            name = profile?.name?.ifBlank { null } ?: entry.name,
            address = profile?.address ?: entry.address,
            ownerName = profile?.ownerName,
            phone = profile?.phone
        )
    }

    /**
     * Switches to shop [id]: refused while something of the open shop is still running. Otherwise the choice is
     * written and the app restarts into that shop.
     */
    fun switchTo(id: Int): SwitchBlock? {
        val registry = store.load()
        switchBlock(registry, id, backupBusy(), saving())?.let { return it }
        store.save(registry.selecting(id))
        restart()
        return null
    }

    /** Deletes a shop that is not open (crash-safe, see ShopStore.delete). */
    fun delete(id: Int): DeleteProblem? {
        checkDelete(store.load(), id)?.let { return it }
        store.delete(id)
        return null
    }

    private fun profileOf(d: ShopDetails) = ShopProfile(
        name = cleanShopName(d.name),
        ownerName = d.ownerName?.trim()?.ifEmpty { null },
        phone = d.phone?.trim()?.ifEmpty { null },
        address = d.address?.trim()?.ifEmpty { null }
    )

    private suspend fun writeProfile(file: File, details: ShopDetails) {
        val db = AppDatabase.open(context, file)
        try {
            db.shopProfileDao().save(profileOf(details))
        } finally {
            db.close()
        }
    }

    private suspend fun readProfile(file: File): ShopProfile? {
        if (!file.exists()) return null
        val db = AppDatabase.open(context, file)
        return try {
            db.shopProfileDao().get()
        } finally {
            db.close()
        }
    }
}

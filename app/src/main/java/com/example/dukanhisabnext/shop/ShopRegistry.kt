package com.example.dukanhisabnext.shop

import java.io.StringReader
import java.time.Instant
import java.util.Locale
import java.util.Properties
import com.example.dukanhisabnext.util.DhakaTime

// The list of shops of this phone (the "shop registry"). Every shop has its OWN database file and its own
// files (cart, sale journal, backup work, backup settings): nothing is shared between shops. The registry only
// says which shops exist, what they are called and which one is open. Plain Kotlin (no Android), so every rule
// here is tested on the PC. The file work is in ShopStore.kt; where each shop's files are is in ShopPaths.kt.

/** At most this many shops on one phone. */
const val MAX_SHOPS = 20

/** The shop that existed before there were several shops: it keeps its old file names (see ShopPaths). */
const val LEGACY_SHOP_ID = 1

/** The longest shop name the registry keeps (a longer one is cut). */
const val MAX_SHOP_NAME = 80

enum class ShopState {
    /** A normal shop. */
    OK,

    /** Being deleted: its files go, then the entry. A delete stopped by a crash is finished at the next start. */
    DELETING
}

data class ShopEntry(
    /** Never reused, even after a delete (a new shop always gets new files). */
    val id: Int,
    /** The name as typed (trimmed). Empty only for shop 1 of an install where no shop name was given yet. */
    val name: String,
    val address: String? = null,
    val createdAt: Long,
    /**
     * True once the shop's database file has been made. A shop that was opened before but has no file any more
     * is "missing" (never silently replaced by an empty database).
     */
    val opened: Boolean,
    val state: ShopState = ShopState.OK
)

data class ShopRegistry(
    val shops: List<ShopEntry>,
    /** The shop that is open. */
    val selectedId: Int,
    /** The id the next new shop gets. */
    val nextId: Int
) {
    /** The shops the user sees (not the ones being deleted), in the order they were made. */
    val active: List<ShopEntry> get() = shops.filter { it.state == ShopState.OK }.sortedBy { it.id }

    fun find(id: Int): ShopEntry? = shops.firstOrNull { it.id == id }

    /** The open shop. */
    val selected: ShopEntry get() = find(selectedId) ?: active.first()

    companion object {
        /**
         * The registry of an install from before several shops existed: its one database becomes shop 1, with
         * its old file names, without copying or changing the file. [opened] is true when that file exists.
         */
        fun legacy(createdAt: Long, opened: Boolean) = ShopRegistry(
            shops = listOf(ShopEntry(LEGACY_SHOP_ID, name = "", createdAt = createdAt, opened = opened)),
            selectedId = LEGACY_SHOP_ID,
            nextId = LEGACY_SHOP_ID + 1
        )
    }
}

// =========================================================================================
// Names
// =========================================================================================

/** A shop name as kept: trimmed, inner runs of spaces made one, line breaks removed, at most [MAX_SHOP_NAME] characters. */
fun cleanShopName(raw: String): String =
    raw.replace(Regex("[\\r\\n\\t]"), " ").trim().replace(Regex(" {2,}"), " ").take(MAX_SHOP_NAME).trim()

/** The key two names are compared with: case and spacing do not matter ("Taqwa Tech" = "taqwa  tech"). */
fun shopNameKey(name: String): String = cleanShopName(name).lowercase(Locale.ROOT)

/** True when two shop names differ (case and spacing ignored). An empty or missing name never differs. */
fun shopNamesDiffer(a: String?, b: String?): Boolean {
    if (a.isNullOrBlank() || b.isNullOrBlank()) return false
    return shopNameKey(a) != shopNameKey(b)
}

enum class ShopNameProblem { EMPTY, DUPLICATE }

/** null = the name can be used for shop [exceptId] (or for a new shop when [exceptId] is null). */
fun checkShopName(registry: ShopRegistry, name: String, exceptId: Int? = null): ShopNameProblem? {
    val key = shopNameKey(name)
    if (key.isEmpty()) return ShopNameProblem.EMPTY
    val taken = registry.active.any { it.id != exceptId && shopNameKey(it.name) == key }
    return if (taken) ShopNameProblem.DUPLICATE else null
}

/** True when one more shop may be made. */
fun canAddShop(registry: ShopRegistry): Boolean = registry.active.size < MAX_SHOPS

// =========================================================================================
// Changes (each returns a new registry; ShopStore writes it)
// =========================================================================================

/** Adds a new shop (its database file is already made). The caller has checked the name and the limit. */
fun ShopRegistry.withNewShop(name: String, address: String?, createdAt: Long): Pair<ShopRegistry, ShopEntry> {
    val entry = ShopEntry(nextId, cleanShopName(name), address?.trim()?.ifEmpty { null }, createdAt, opened = true)
    return copy(shops = shops + entry, nextId = nextId + 1) to entry
}

fun ShopRegistry.renamed(id: Int, name: String, address: String?): ShopRegistry =
    copy(shops = shops.map { if (it.id == id) it.copy(name = cleanShopName(name), address = address?.trim()?.ifEmpty { null }) else it })

fun ShopRegistry.markedOpened(id: Int): ShopRegistry =
    copy(shops = shops.map { if (it.id == id) it.copy(opened = true) else it })

fun ShopRegistry.selecting(id: Int): ShopRegistry {
    require(active.any { it.id == id }) { "no shop $id" }
    return copy(selectedId = id)
}

fun ShopRegistry.markedDeleting(id: Int): ShopRegistry =
    copy(shops = shops.map { if (it.id == id) it.copy(state = ShopState.DELETING) else it })

fun ShopRegistry.without(id: Int): ShopRegistry = copy(shops = shops.filterNot { it.id == id })

enum class DeleteProblem {
    /** Only a shop that is not open can be deleted (open another one first). */
    IS_OPEN,

    /** The last shop cannot be deleted. */
    LAST_SHOP,
    NOT_FOUND
}

/** null = shop [id] may be deleted. */
fun checkDelete(registry: ShopRegistry, id: Int): DeleteProblem? = when {
    registry.active.none { it.id == id } -> DeleteProblem.NOT_FOUND
    registry.selectedId == id -> DeleteProblem.IS_OPEN
    registry.active.size <= 1 -> DeleteProblem.LAST_SHOP
    else -> null
}

/** True when what was typed in the delete dialog is the shop's name (case and spacing ignored). */
fun deleteNameMatches(typed: String, shopName: String): Boolean =
    shopNameKey(typed).isNotEmpty() && shopNameKey(typed) == shopNameKey(shopName)

/**
 * The delete button of the delete dialog is enabled only when the name was typed AND either a backup of this
 * shop was made in the dialog or the user ticked "I do not want a backup".
 */
fun deleteAllowed(typedName: String, shopName: String, backedUp: Boolean, noBackupTicked: Boolean): Boolean =
    deleteNameMatches(typedName, shopName) && (backedUp || noBackupTicked)

// =========================================================================================
// The registry file (key=value lines, like the backup manifest)
// =========================================================================================

const val REGISTRY_FORMAT = "dokanhisab-shops"
const val REGISTRY_VERSION = 1

private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")

fun writeRegistry(r: ShopRegistry): String = buildString {
    fun line(key: String, value: Any) = append(key).append('=').append(escape(value.toString())).append('\n')
    line("format", REGISTRY_FORMAT)
    line("version", REGISTRY_VERSION)
    line("selectedId", r.selectedId)
    line("nextId", r.nextId)
    line("ids", r.shops.sortedBy { it.id }.joinToString(",") { it.id.toString() })
    for (s in r.shops.sortedBy { it.id }) {
        line("shop.${s.id}.name", s.name)
        s.address?.let { line("shop.${s.id}.address", it) }
        line("shop.${s.id}.createdAt", s.createdAt)
        line("shop.${s.id}.opened", s.opened)
        line("shop.${s.id}.state", s.state.name)
    }
}

/** The registry from its file, or null when the text is not a usable registry (the caller then uses the .bak copy or rebuilds it). */
fun parseRegistry(text: String): ShopRegistry? {
    val p = try {
        Properties().apply { load(StringReader(text)) }
    } catch (e: IllegalArgumentException) {
        return null
    }
    if (p.getProperty("format") != REGISTRY_FORMAT) return null
    val version = p.getProperty("version")?.toIntOrNull() ?: return null
    if (version != REGISTRY_VERSION) return null
    val ids = p.getProperty("ids")?.split(',')?.filter { it.isNotBlank() }?.map { it.trim().toIntOrNull() ?: return null } ?: return null
    if (ids.isEmpty() || ids.toSet().size != ids.size || ids.any { it < 1 }) return null
    val shops = ids.map { id ->
        ShopEntry(
            id = id,
            name = cleanShopName(p.getProperty("shop.$id.name") ?: return null),
            address = p.getProperty("shop.$id.address")?.trim()?.ifEmpty { null },
            createdAt = p.getProperty("shop.$id.createdAt")?.toLongOrNull() ?: return null,
            opened = p.getProperty("shop.$id.opened")?.toBooleanStrictOrNull() ?: return null,
            state = ShopState.entries.firstOrNull { it.name == p.getProperty("shop.$id.state") } ?: return null
        )
    }
    val nextId = p.getProperty("nextId")?.toIntOrNull() ?: return null
    if (nextId <= ids.max()) return null
    val selectedId = p.getProperty("selectedId")?.toIntOrNull() ?: return null
    val registry = ShopRegistry(shops, selectedId, nextId)
    if (registry.active.isEmpty()) return null
    // An open shop that is not (or no longer) a normal one: open the first normal shop instead.
    return if (registry.active.none { it.id == selectedId }) registry.copy(selectedId = registry.active.first().id) else registry
}

// =========================================================================================
// Backup file names
// =========================================================================================

/**
 * The shop's name as it can stand in a file name: only the ASCII letters and digits are kept, everything else
 * becomes a single "-", at most 40 characters. A name without any ASCII letter (for example a Bengali name)
 * gives "Shop-<id>": the full name is in the backup's manifest anyway.
 */
fun asciiShopSlug(name: String?, shopId: Int): String {
    val slug = (name ?: "").map { c -> if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9') c else '-' }
        .joinToString("").replace(Regex("-{2,}"), "-").trim('-').take(40).trim('-')
    return if (slug.any { it in 'a'..'z' || it in 'A'..'Z' }) slug else "Shop-$shopId"
}

/** "Taqwa-Tech-backup-2026-11-15-1209.zip": the shop, then the Dhaka date and time in Latin digits. */
fun shopBackupFileName(shopName: String?, shopId: Int, createdAt: Long): String {
    val t = Instant.ofEpochMilli(createdAt).atZone(DhakaTime.ZONE)
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${asciiShopSlug(shopName, shopId)}-backup-${t.year}-${two(t.monthValue)}-${two(t.dayOfMonth)}-${two(t.hour)}${two(t.minute)}.zip"
}

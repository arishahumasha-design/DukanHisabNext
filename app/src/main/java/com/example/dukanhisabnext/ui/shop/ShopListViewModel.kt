package com.example.dukanhisabnext.ui.shop

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.DokanHisabApp
import com.example.dukanhisabnext.backup.BackupPrefs
import com.example.dukanhisabnext.backup.CreateResult
import com.example.dukanhisabnext.shop.DeleteProblem
import com.example.dukanhisabnext.shop.ShopEntry
import com.example.dukanhisabnext.shop.ShopHealth
import com.example.dukanhisabnext.shop.SwitchBlock
import com.example.dukanhisabnext.shop.canAddShop
import com.example.dukanhisabnext.shop.checkDelete
import com.example.dukanhisabnext.shop.deleteAllowed
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One card of the shop list. */
data class ShopCard(
    val shop: ShopEntry,
    val isOpen: Boolean,
    val health: ShopHealth,
    /** Only a shop that is not open (and not the last one) can be deleted. */
    val canDelete: Boolean
)

/** The delete dialog of one shop. */
data class DeleteDialog(
    val shop: ShopEntry,
    /** The name that must be typed (the shop's name, or "দোকান ২" for a shop without one). */
    val confirmName: String = shop.name,
    /** The shop's file is gone: there is nothing to back up. */
    val fileMissing: Boolean = false,
    val typedName: String = "",
    val noBackupTicked: Boolean = false,
    val backedUp: Boolean = false,
    val backupRunning: Boolean = false,
    val backupFailed: Boolean = false,
    /** A backup zip waiting for the user to pick where it is saved (its suggested name). */
    val pendingSave: File? = null
) {
    val deleteEnabled: Boolean get() = !backupRunning && deleteAllowed(typedName, confirmName, backedUp, noBackupTicked)
}

data class ShopListUi(
    val cards: List<ShopCard> = emptyList(),
    val canAdd: Boolean = true,
    /** "Open this shop?" for this shop. */
    val confirmSwitch: ShopEntry? = null,
    /** The switch is happening: the screen is covered ("Opening …") until the app restarts. */
    val switching: ShopEntry? = null,
    val switchBlocked: SwitchBlock? = null,
    val delete: DeleteDialog? = null,
    val deletedMessage: Boolean = false
)

/** The shop list: open (switch to), edit, delete, and the backup a delete offers first. */
class ShopListViewModel(private val app: DokanHisabApp) : ViewModel() {

    private val _ui = MutableStateFlow(ShopListUi())
    val ui: StateFlow<ShopListUi> = _ui.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val registry = withContext(Dispatchers.IO) { app.shopManager.registry() }
            val cards = registry.active.map { shop ->
                val isOpen = shop.id == registry.selectedId
                ShopCard(
                    shop = shop,
                    isOpen = isOpen,
                    health = if (isOpen) app.shopHealth else app.shopStore.fileHealth(shop),
                    canDelete = checkDelete(registry, shop.id) == null
                )
            }
            _ui.update { it.copy(cards = cards, canAdd = canAddShop(registry)) }
        }
    }

    // ---- switching ----

    fun askSwitch(shop: ShopEntry) = _ui.update { it.copy(confirmSwitch = shop, switchBlocked = null) }

    fun cancelSwitch() = _ui.update { it.copy(confirmSwitch = null) }

    fun dismissBlocked() = _ui.update { it.copy(switchBlocked = null) }

    /** Covers the screen, then asks the manager; on success the app restarts into the chosen shop (on Home). */
    fun confirmSwitch() {
        val shop = _ui.value.confirmSwitch ?: return
        _ui.update { it.copy(confirmSwitch = null, switching = shop) }
        viewModelScope.launch {
            delay(SWITCH_COVER_MS) // the cover is seen before the restart
            val blocked = withContext(Dispatchers.IO) { app.shopManager.switchTo(shop.id) }
            if (blocked != null) _ui.update { it.copy(switching = null, switchBlocked = blocked) }
        }
    }

    // ---- deleting ----

    fun askDelete(card: ShopCard, displayName: String) =
        _ui.update { it.copy(delete = DeleteDialog(card.shop, confirmName = displayName, fileMissing = card.health == ShopHealth.MISSING)) }

    fun cancelDelete() {
        _ui.value.delete?.pendingSave?.delete()
        _ui.update { it.copy(delete = null) }
    }

    fun onTypedName(text: String) = updateDelete { it.copy(typedName = text) }

    fun onNoBackupTicked(ticked: Boolean) = updateDelete { it.copy(noBackupTicked = ticked) }

    /** "Back up this shop first": the backup of the (closed) shop is made; the screen then asks where to save it. */
    fun backupFirst() {
        val dialog = _ui.value.delete ?: return
        if (dialog.backupRunning || dialog.fileMissing) return
        updateDelete { it.copy(backupRunning = true, backupFailed = false) }
        viewModelScope.launch {
            val zip = withContext(Dispatchers.IO) {
                val (engine, close) = app.engineForShop(dialog.shop.id)
                try {
                    (engine.createBackup() as? CreateResult.Created)?.zip
                } finally {
                    close()
                }
            }
            updateDelete { it.copy(backupRunning = zip != null, backupFailed = zip == null, pendingSave = zip) }
        }
    }

    /** The user picked where to save the backup (null: cancelled). Only a backup really written counts. */
    fun onBackupTarget(uri: Uri?) {
        val dialog = _ui.value.delete ?: return
        val zip = dialog.pendingSave ?: return
        if (uri == null) {
            zip.delete()
            updateDelete { it.copy(backupRunning = false, pendingSave = null) }
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    app.contentResolver.openOutputStream(uri, "w")?.use { out -> zip.inputStream().use { it.copyTo(out) } } != null
                } catch (e: Exception) {
                    false
                } finally {
                    zip.delete()
                }
            }
            if (ok) BackupPrefs(app, app.shopStore.paths(dialog.shop.id).backupPrefsName).markBackedUp(System.currentTimeMillis())
            updateDelete { it.copy(backupRunning = false, pendingSave = null, backedUp = it.backedUp || ok, backupFailed = !ok) }
        }
    }

    fun confirmDelete() {
        val dialog = _ui.value.delete ?: return
        if (!dialog.deleteEnabled) return
        viewModelScope.launch {
            val problem: DeleteProblem? = withContext(Dispatchers.IO) { app.shopManager.delete(dialog.shop.id) }
            _ui.update { it.copy(delete = null, deletedMessage = problem == null) }
            reload()
        }
    }

    fun dismissDeleted() = _ui.update { it.copy(deletedMessage = false) }

    private fun updateDelete(change: (DeleteDialog) -> DeleteDialog) =
        _ui.update { ui -> ui.copy(delete = ui.delete?.let(change)) }

    private companion object {
        const val SWITCH_COVER_MS = 350L
    }
}

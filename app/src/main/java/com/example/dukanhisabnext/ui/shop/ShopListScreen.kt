package com.example.dukanhisabnext.ui.shop

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.shop.MAX_SHOPS
import com.example.dukanhisabnext.shop.ShopEntry
import com.example.dukanhisabnext.shop.ShopHealth
import com.example.dukanhisabnext.shop.SwitchBlock
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.AppAlertDialog
import com.example.dukanhisabnext.ui.components.AppTextField
import com.example.dukanhisabnext.ui.components.DestructiveTextButton
import com.example.dukanhisabnext.ui.components.DialogTextButton
import com.example.dukanhisabnext.ui.components.InfoCard
import com.example.dukanhisabnext.ui.components.PillVariant
import com.example.dukanhisabnext.ui.components.PrimaryButton
import com.example.dukanhisabnext.ui.components.SmallOutlinedButton
import com.example.dukanhisabnext.ui.components.StatusPill
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.components.stringResource
import com.example.dukanhisabnext.ui.theme.AppCard
import com.example.dukanhisabnext.ui.theme.Spacing
import com.example.dukanhisabnext.util.localDigits

/** A shop's name for the screen: its own name, or "দোকান ১" while it has none. */
@Composable
fun shopDisplayName(shop: ShopEntry): String =
    shop.name.ifBlank { stringResource(R.string.shop_unnamed, localDigits(shop.id.toString())) }

/**
 * "দোকান বাছাই করুন": every shop of this phone as a card (name, address, "খোলা আছে" for the open one, "খোলা
 * যাচ্ছে না" for one whose file is gone), with Open, Edit and Delete, and "নতুন দোকান". Opening another shop
 * restarts the app into that shop (on Home). [createdId]: a shop that was just made; the screen offers to open it.
 */
@Composable
fun ShopListScreen(
    onBack: () -> Unit,
    onNewShop: () -> Unit,
    onEditShop: (Int) -> Unit,
    createdId: Int?,
    onCreatedHandled: () -> Unit,
    viewModel: ShopListViewModel = appViewModel { app, _ -> ShopListViewModel(app) }
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    // Back from the form: the list may have changed.
    LaunchedEffect(Unit) { viewModel.reload() }
    LaunchedEffect(createdId, ui.cards) {
        val made = ui.cards.firstOrNull { it.shop.id == createdId } ?: return@LaunchedEffect
        viewModel.askSwitch(made.shop)
        onCreatedHandled()
    }

    Box(Modifier.fillMaxSize()) {
        SubScreenScaffold(title = stringResource(R.string.title_shops), onBack = onBack) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                if (ui.deletedMessage) InfoCard(text = stringResource(R.string.shop_deleted), variant = PillVariant.Paid)
                Text(
                    stringResource(R.string.shop_list_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                for (card in ui.cards) {
                    val displayName = shopDisplayName(card.shop)
                    ShopCardView(
                        card = card,
                        onOpen = { viewModel.askSwitch(card.shop) },
                        onEdit = { onEditShop(card.shop.id) },
                        onDelete = { viewModel.askDelete(card, displayName) }
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.btn_new_shop),
                    onClick = onNewShop,
                    enabled = ui.canAdd,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!ui.canAdd) {
                    Text(
                        stringResource(R.string.shop_limit_reached, localDigits(MAX_SHOPS.toString())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        ui.switching?.let { SwitchingCover(shopDisplayName(it)) }
    }

    ui.confirmSwitch?.let { shop ->
        val name = shopDisplayName(shop)
        AppAlertDialog(
            onDismissRequest = viewModel::cancelSwitch,
            title = { Text(stringResource(R.string.switch_confirm_title, name)) },
            text = { Text(stringResource(R.string.switch_confirm_body), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { DialogTextButton(stringResource(R.string.btn_open_now), viewModel::confirmSwitch) },
            dismissButton = { DialogTextButton(stringResource(R.string.btn_cancel), viewModel::cancelSwitch) }
        )
    }

    ui.switchBlocked?.let { block ->
        val text = when (block) {
            SwitchBlock.BACKUP_RUNNING -> stringResource(R.string.switch_blocked_backup)
            else -> stringResource(R.string.switch_blocked_saving)
        }
        AppAlertDialog(
            onDismissRequest = viewModel::dismissBlocked,
            text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { DialogTextButton(stringResource(R.string.backup_ok), viewModel::dismissBlocked) }
        )
    }

    ui.delete?.let { DeleteShopDialog(it, viewModel) }
}

@Composable
private fun ShopCardView(card: ShopCard, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_shop),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(Spacing.m))
                Text(
                    shopDisplayName(card.shop),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (card.isOpen) {
                    Spacer(Modifier.width(Spacing.s))
                    StatusPill(stringResource(R.string.shop_open_now), PillVariant.Paid)
                }
            }
            card.shop.address?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (card.health != ShopHealth.OK) StatusPill(stringResource(R.string.shop_cannot_open), PillVariant.Cancelled)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (!card.isOpen && card.health == ShopHealth.OK) {
                    SmallOutlinedButton(stringResource(R.string.btn_open_shop), onOpen)
                }
                if (card.health == ShopHealth.OK) SmallOutlinedButton(stringResource(R.string.btn_edit_shop), onEdit)
                if (card.canDelete) SmallOutlinedButton(stringResource(R.string.btn_delete_shop), onDelete)
            }
        }
    }
}

/** The whole screen while the app switches: plain background, a spinner, "… খোলা হচ্ছে". Taps do nothing. */
@Composable
private fun SwitchingCover(name: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Text(stringResource(R.string.switch_opening, name), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Deleting a shop for good. "Back up this shop first" is the main action and gets the focus; the delete button
 * works only when the name was typed AND a backup was saved here or "I do not want a backup" is ticked.
 */
@Composable
private fun DeleteShopDialog(dialog: DeleteDialog, viewModel: ShopListViewModel) {
    val name = shopDisplayName(dialog.shop)
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        viewModel.onBackupTarget(uri)
    }
    LaunchedEffect(dialog.pendingSave) {
        dialog.pendingSave?.let { saveLauncher.launch(it.name) }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    AppAlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = viewModel::cancelDelete,
        title = { Text(stringResource(R.string.delete_shop_title, name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                Text(
                    stringResource(R.string.delete_shop_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                if (!dialog.fileMissing) {
                    PrimaryButton(
                        text = stringResource(R.string.btn_backup_shop_first),
                        onClick = viewModel::backupFirst,
                        enabled = !dialog.backupRunning && !dialog.backedUp,
                        icon = R.drawable.ic_backup,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                if (dialog.backupRunning) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                if (dialog.backedUp) InfoCard(text = stringResource(R.string.delete_backup_done), variant = PillVariant.Paid)
                if (dialog.backupFailed) InfoCard(text = stringResource(R.string.delete_backup_failed), variant = PillVariant.Due)
                if (!dialog.backedUp) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.onNoBackupTicked(!dialog.noBackupTicked) }
                    ) {
                        Checkbox(checked = dialog.noBackupTicked, onCheckedChange = null)
                        Spacer(Modifier.width(Spacing.s))
                        Text(stringResource(R.string.delete_no_backup), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                AppTextField(
                    value = dialog.typedName,
                    onValueChange = viewModel::onTypedName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.delete_type_name, name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
            }
        },
        confirmButton = {
            DestructiveTextButton(
                text = stringResource(R.string.btn_delete_forever),
                onClick = viewModel::confirmDelete,
                enabled = dialog.deleteEnabled
            )
        },
        dismissButton = { DialogTextButton(stringResource(R.string.btn_cancel), viewModel::cancelDelete) }
    )
}


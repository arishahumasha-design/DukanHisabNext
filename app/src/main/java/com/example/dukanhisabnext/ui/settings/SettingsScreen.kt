package com.example.dukanhisabnext.ui.settings

import com.example.dukanhisabnext.ui.theme.AppCard
import com.example.dukanhisabnext.ui.components.LoadingIndicator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.DokanHisabApp
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.backup.lastBackupText
import com.example.dukanhisabnext.ui.backup.PLACEHOLDER
import com.example.dukanhisabnext.ui.backup.periodWords
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.components.PrimaryButton
import com.example.dukanhisabnext.ui.components.SecondaryButton

/**
 * Settings: an owner card (avatar, name, "মালিক", mobile, "এডিট করুন") and a big shop button.
 * Nothing else yet. [shopSavedMessage] is true right after "দোকানের তথ্য" was saved and the app
 * came back here.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEditOwner: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenBackup: () -> Unit,
    shopSavedMessage: Boolean,
    viewModel: SettingsViewModel = appViewModel { app, _ -> SettingsViewModel(app.shopRepository) }
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    SubScreenScaffold(title = stringResource(R.string.title_settings), onBack = onBack) { padding ->
        val current = ui
        if (current == null) {
            LoadingIndicator(Modifier.padding(padding).fillMaxSize()) // still loading
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (shopSavedMessage) SavedMessage(stringResource(R.string.shop_saved))

                OwnerCard(ui = current, onEdit = onEditOwner)
                ShopNameCard(shopName = current.shopName)
                BackupRow(onClick = onOpenBackup)
                ShopButton(shopName = current.shopName, onClick = onOpenShop)
            }
        }
    }
}

/** A clear green "saved" message. */
@Composable
fun SavedMessage(text: String) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * The owner: a round avatar with the first letter of the name (a person icon if there is no
 * name), the name (or a neutral placeholder), the small label "মালিক", and the mobile.
 */
@Composable
private fun OwnerCard(ui: SettingsUi, onEdit: () -> Unit) {
    val letter = avatarLetter(ui.ownerName)
    val name = ownerDisplayName(ui.ownerName, stringResource(R.string.owner_name_placeholder))
    val phone = ownerPhoneDisplay(ui.ownerPhone)

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (letter != null) {
                        Text(
                            text = letter,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(
                            painterResource(R.drawable.ic_contacts),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.owner_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    phone?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                }
            }
            SecondaryButton(text = stringResource(R.string.btn_edit_owner), onClick = onEdit, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * The shop name: a small label "দোকানের নাম" with the name under it (only when a shop exists).
 * It sits above the Backup card; the button that edits it ([ShopButton]) sits below that card.
 */
@Composable
private fun ShopNameCard(shopName: String?) {
    if (shopButtonKind(shopName) != ShopButtonKind.CHANGE) return
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.field_shop_name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = shopName.orEmpty().trim(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The shop button. No shop name yet: "দোকান যুক্ত করুন" (the main button). A shop exists: "দোকান
 * পরিবর্তন করুন" as a quiet outlined button. Both open "দোকানের তথ্য".
 */
@Composable
private fun ShopButton(shopName: String?, onClick: () -> Unit) {
    when (shopButtonKind(shopName)) {
        ShopButtonKind.ADD -> PrimaryButton(
            text = stringResource(R.string.btn_add_shop),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        )
        ShopButtonKind.CHANGE -> SecondaryButton(
            text = stringResource(R.string.btn_change_shop),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


/**
 * "ব্যাকআপ ও রিস্টোর": the last backup time (Dhaka time) or "এখনও ব্যাকআপ নেওয়া হয়নি", and opens the backup screen.
 * The time is kept in the small settings file and set only after a backup file was really written.
 */
@Composable
private fun BackupRow(onClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = (context.applicationContext as DokanHisabApp).backupPrefs
    val lastBackupAt by prefs.lastBackupAt.collectAsStateWithLifecycle()
    val lastTemplate = stringResource(R.string.backup_last_at, PLACEHOLDER)
    val subtitle = lastBackupText(
        lastBackupAt = lastBackupAt,
        neverText = stringResource(R.string.backup_never),
        labelFormat = { time -> lastTemplate.replace(PLACEHOLDER, time) },
        periodWord = periodWords()
    )
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.title_backup), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}

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
import com.example.dukanhisabnext.ui.components.stringResource
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
import com.example.dukanhisabnext.ui.components.SegmentedControl
import com.example.dukanhisabnext.MainActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import com.example.dukanhisabnext.ui.components.AppAlertDialog
import com.example.dukanhisabnext.ui.components.DialogTextButton
import com.example.dukanhisabnext.util.AppCurrency
import com.example.dukanhisabnext.util.CurrentCurrency
import com.example.dukanhisabnext.util.sampleMoney
import kotlinx.coroutines.launch
import com.example.dukanhisabnext.util.AppLanguage
import com.example.dukanhisabnext.util.CurrentLanguage

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
                LanguageCard()
                CurrencyCard()
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

/**
 * "Language / ভাষা": the app language, বাংলা or English. The title and the two names are the same in both
 * languages, so they can be read whichever one is on. Choosing one redraws the whole app at once
 * (MainActivity.switchLanguage); the choice is kept in its own small file and is not part of a backup.
 */
@Composable
private fun LanguageCard() {
    val context = LocalContext.current
    val languages = listOf(AppLanguage.BENGALI, AppLanguage.ENGLISH)
    val labels = listOf(stringResource(R.string.language_bengali), stringResource(R.string.language_english))
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_language_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SegmentedControl(
                labels = labels,
                selectedIndex = languages.indexOf(CurrentLanguage.value),
                onSelect = { index -> context.findMainActivity()?.switchLanguage(languages[index]) }
            )
        }
    }
}

/** The MainActivity behind this context (a Compose context may be wrapped). */
private tailrec fun android.content.Context.findMainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is android.content.ContextWrapper -> baseContext.findMainActivity()
    else -> null
}

/**
 * "Currency / মুদ্রা": the symbol and number style the app shows. It only relabels: no amount is ever converted
 * and the database is not touched. Tapping the card opens the list; if the shop already has records, a
 * warning says so before the change is made.
 */
@Composable
private fun CurrencyCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listOpen by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf<AppCurrency?>(null) }
    val current = CurrentCurrency.value

    AppCard(modifier = Modifier.fillMaxWidth().clickable { listOpen = true }) {
        Row(
            modifier = Modifier.padding(16.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_currency_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    currencyLabel(current),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }

    if (listOpen) {
        AppAlertDialog(
            onDismissRequest = { listOpen = false },
            title = { Text(stringResource(R.string.currency_dialog_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.currency_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        items(AppCurrency.entries) { currency ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(selected = currency == current, role = Role.RadioButton, onClick = {
                                        listOpen = false
                                        if (currency == current) return@selectable
                                        // Records already exist? Then warn first: amounts are relabelled, never converted.
                                        scope.launch {
                                            val app = context.applicationContext as DokanHisabApp
                                            if (app.hasShopData()) asking = currency else context.findMainActivity()?.switchCurrency(currency)
                                        }
                                    }),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = currency == current, onClick = null)
                                Text(
                                    currencyLabel(currency),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { DialogTextButton(stringResource(R.string.btn_close_dialog), onClick = { listOpen = false }) }
        )
    }

    asking?.let { target ->
        AppAlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.currency_warning_title)) },
            text = {
                Text(
                    stringResource(R.string.currency_warning_text, sampleMoney(current), sampleMoney(target)),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                DialogTextButton(stringResource(R.string.currency_warning_yes), onClick = {
                    asking = null
                    context.findMainActivity()?.switchCurrency(target)
                })
            },
            dismissButton = { DialogTextButton(stringResource(R.string.btn_cancel), onClick = { asking = null }) }
        )
    }
}

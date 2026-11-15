package com.example.dukanhisabnext.ui.backup

import com.example.dukanhisabnext.util.CurrentCurrency
import com.example.dukanhisabnext.ui.settings.currencyLabel
import com.example.dukanhisabnext.backup.restoreCurrencyCase
import com.example.dukanhisabnext.backup.RestoreCurrencyCase
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import com.example.dukanhisabnext.ui.theme.AppCard
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.dukanhisabnext.ui.components.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.dukanhisabnext.ui.components.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.DokanHisabApp
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.backup.BackupJobState
import com.example.dukanhisabnext.backup.RestoreSummary
import com.example.dukanhisabnext.backup.backupDateTimeText
import com.example.dukanhisabnext.backup.backupScreenUi
import com.example.dukanhisabnext.backup.lastBackupText
import com.example.dukanhisabnext.backup.saveFileName
import com.example.dukanhisabnext.backup.summaryRows
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.components.DestructiveTextButton
import com.example.dukanhisabnext.ui.components.DialogTextButton
import com.example.dukanhisabnext.ui.components.InfoCard
import com.example.dukanhisabnext.ui.components.PillVariant
import com.example.dukanhisabnext.ui.components.PrimaryButton
import com.example.dukanhisabnext.ui.components.SecondaryButton
import com.example.dukanhisabnext.ui.theme.AppTheme
import com.example.dukanhisabnext.ui.theme.Spacing
import com.example.dukanhisabnext.backup.shouldRemindBackup
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.example.dukanhisabnext.ui.settings.SavedMessage
import com.example.dukanhisabnext.util.localDigits
import java.io.File

/** Which question dialog is open. Saved with rememberSaveable so a rotation keeps it. */
private enum class Ask { NONE, SAVE_WARNING, SHARE_WARNING, UNDO }

/**
 * "ব্যাকআপ ও রিস্টোর". The screen only WATCHES the BackupManager's job, so rotating the phone or
 * pressing Back never cancels the work. A second start while a job runs is refused (the buttons
 * are off, and the manager refuses too). Cancelling a picker is not an error.
 */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = (context.applicationContext as DokanHisabApp).backupManager
    val state by manager.state.collectAsStateWithLifecycle()
    val hasSafetyCopy by manager.hasSafetyCopy.collectAsStateWithLifecycle()
    val lastBackupAt by manager.lastBackupAt.collectAsStateWithLifecycle()
    val ui = backupScreenUi(state, hasSafetyCopy)
    var ask by rememberSaveable { mutableStateOf(Ask.NONE) }

    // The system pickers. A null result means the user cancelled: nothing happens.
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) manager.startSave(uri)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) manager.startRestore(uri)
    }

    // The zip is ready in the share folder: open the share sheet once.
    val shareTitle = stringResource(R.string.backup_share_title)
    LaunchedEffect(state) {
        val s = state
        if (s is BackupJobState.ShareReady) {
            openShareSheet(context, s.file, shareTitle)
            manager.shareHandled()
        }
    }
    // Leaving the screen while a share is waiting drops it (it would pop up later, out of context).
    DisposableEffect(Unit) { onDispose { manager.shareHandled() } }

    val periodWord = periodWords()
    val lastTemplate = stringResource(R.string.backup_last_at, PLACEHOLDER)
    val lastText = lastBackupText(
        lastBackupAt = lastBackupAt,
        neverText = stringResource(R.string.backup_never),
        labelFormat = { time -> lastTemplate.replace(PLACEHOLDER, time) },
        periodWord = periodWord
    )

    SubScreenScaffold(title = stringResource(R.string.title_backup), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- the status: a green check when the last backup is recent, an amber warning after 7 days (or never) ----
            val overdue = shouldRemindBackup(System.currentTimeMillis(), lastBackupAt, true)
            val colors = AppTheme.colors
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (overdue) colors.amberContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (overdue) colors.amberText else MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = null
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.l).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m)
                ) {
                    Icon(
                        painterResource(if (overdue) R.drawable.ic_warning else R.drawable.ic_check),
                        contentDescription = null,
                        tint = if (overdue) colors.amber else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(lastText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
            }

            // ---- what is happening, or how it ended ----
            val current = state
            if (current is BackupJobState.Working) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text(stringResource(current.step.labelRes()), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (ui.showDone) {
                SavedMessage(stringResource(R.string.backup_saved))
                DialogTextButton(stringResource(R.string.backup_ok), manager::dismiss)
            }
            ui.failure?.let { problem ->
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(problem.messageRes()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        DialogTextButton(stringResource(R.string.backup_ok), manager::dismiss)
                    }
                }
            }

            // ---- make a backup ----
            PrimaryButton(
                text = stringResource(R.string.btn_backup_save),
                onClick = { ask = Ask.SAVE_WARNING },
                enabled = ui.buttonsEnabled,
                modifier = Modifier.fillMaxWidth()
            )
            SecondaryButton(
                text = stringResource(R.string.btn_backup_share),
                onClick = { ask = Ask.SHARE_WARNING },
                enabled = ui.buttonsEnabled,
                modifier = Modifier.fillMaxWidth()
            )
            InfoCard(text = stringResource(R.string.backup_privacy_warning), variant = PillVariant.Due)

            // ---- restore ----
            SecondaryButton(
                text = stringResource(R.string.btn_backup_restore),
                // "*/*": a renamed or wrong file must be pickable so the checks can refuse it with a clear message.
                onClick = { openLauncher.launch(arrayOf("*/*")) },
                enabled = ui.buttonsEnabled,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(R.string.backup_restore_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ---- undo (only while the safety copy exists): its own card, apart from the rest ----
            if (ui.showUndo) {
                Spacer(Modifier.height(Spacing.s))
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        SecondaryButton(
                            text = stringResource(R.string.btn_backup_undo),
                            onClick = { ask = Ask.UNDO },
                            enabled = ui.buttonsEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            stringResource(R.string.backup_undo_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // ---- dialogs ----

    if (ask == Ask.SAVE_WARNING || ask == Ask.SHARE_WARNING) {
        val saving = ask == Ask.SAVE_WARNING
        AppAlertDialog(
            // Not closed by a tap outside; Back or "বাতিল" cancels.
            properties = DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { ask = Ask.NONE },
            title = { Text(stringResource(R.string.backup_warning_title)) },
            text = { Text(stringResource(R.string.backup_privacy_warning), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                DialogTextButton(stringResource(R.string.backup_warning_continue), {
                    ask = Ask.NONE
                    if (saving) saveLauncher.launch(saveFileName(System.currentTimeMillis())) else manager.startShare()
                })
            },
            dismissButton = { DialogTextButton(stringResource(R.string.btn_cancel), { ask = Ask.NONE }) }
        )
    }

    if (ask == Ask.UNDO) {
        AppAlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { ask = Ask.NONE },
            title = { Text(stringResource(R.string.undo_confirm_title)) },
            text = { Text(stringResource(R.string.undo_confirm_body), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                DestructiveTextButton(stringResource(R.string.btn_undo_confirm), {
                    ask = Ask.NONE
                    manager.startUndo()
                })
            },
            dismissButton = { DialogTextButton(stringResource(R.string.btn_cancel), { ask = Ask.NONE }) }
        )
    }

    val awaiting = state as? BackupJobState.AwaitingConfirm
    if (ui.showSummary && awaiting != null) {
        RestoreSummaryDialog(
            summary = awaiting.summary,
            periodWord = periodWord,
            onConfirm = { code -> manager.confirmRestore(code) },
            onCancel = manager::cancelRestore
        )
    }
}

/** What is in the backup next to what is here now, and the promise that the current data is kept. */
@Composable
private fun RestoreSummaryDialog(
    summary: RestoreSummary,
    periodWord: (com.example.dukanhisabnext.util.DayPeriod) -> String,
    onConfirm: (currencyCode: String?) -> Unit,
    onCancel: () -> Unit
) {
    val currencyCase = restoreCurrencyCase(summary.backupCurrency, CurrentCurrency.value)
    // Another currency in the backup: the user must choose one before the restore can start.
    var useBackupCurrency by rememberSaveable { mutableStateOf<Boolean?>(null) }
    AppAlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.restore_confirm_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                summary.backupCreatedAt?.let {
                    Text(
                        stringResource(R.string.restore_confirm_date, backupDateTimeText(it, periodWord)),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("", modifier = Modifier.weight(1.2f))
                    Text(stringResource(R.string.restore_col_backup), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.restore_col_now), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                }
                for (row in summaryRows(summary)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(summaryTableLabelRes(row.table)), modifier = Modifier.weight(1.2f))
                        Text(localDigits(row.inBackup.toString()), modifier = Modifier.weight(1f))
                        Text(localDigits(row.now.toString()), modifier = Modifier.weight(1f))
                    }
                }
                RestoreCurrencyPart(currencyCase, useBackupCurrency) { useBackupCurrency = it }
                Text(
                    stringResource(R.string.restore_safety_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            DestructiveTextButton(
                text = stringResource(R.string.btn_restore_confirm),
                onClick = {
                    val code = (currencyCase as? RestoreCurrencyCase.Different)?.takeIf { useBackupCurrency == true }?.backup?.code
                    onConfirm(code)
                },
                enabled = currencyCase !is RestoreCurrencyCase.Different || useBackupCurrency != null
            )
        },
        dismissButton = { DialogTextButton(stringResource(R.string.btn_cancel), onCancel) }
    )
}

/** Opens the system share sheet for the backup zip, through the FileProvider (no permission needed). */
private fun openShareSheet(context: Context, file: File, title: String) {
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, title))
    } catch (e: ActivityNotFoundException) {
        // No app can take it: nothing to do (the save button still works).
    }
}

/**
 * The currency of the backup: one line when it is the same or not given, or (another currency) the question
 * which one to show after the restore, as two choices. Display only: no amount is ever converted.
 */
@Composable
private fun RestoreCurrencyPart(case: RestoreCurrencyCase, useBackup: Boolean?, onChoose: (Boolean) -> Unit) {
    when (case) {
        is RestoreCurrencyCase.Same -> Text(stringResource(R.string.restore_currency_same, currencyLabel(case.currency)), style = MaterialTheme.typography.bodyMedium)
        is RestoreCurrencyCase.NotInBackup -> Text(
            stringResource(R.string.restore_currency_none, currencyLabel(case.current)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        is RestoreCurrencyCase.Different -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.restore_currency_differs, currencyLabel(case.backup), currencyLabel(case.current)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            for ((choice, text) in listOf(
                true to stringResource(R.string.restore_currency_use_backup, currencyLabel(case.backup)),
                false to stringResource(R.string.restore_currency_keep, currencyLabel(case.current))
            )) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = useBackup == choice, role = Role.RadioButton, onClick = { onChoose(choice) }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = useBackup == choice, onClick = null)
                    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

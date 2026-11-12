package com.example.dukanhisabnext.ui.backup

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.DokanHisabApp
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.backup.RestoreSummary
import com.example.dukanhisabnext.backup.StartupNote
import com.example.dukanhisabnext.backup.backupDateTimeText
import com.example.dukanhisabnext.backup.blocksApp
import com.example.dukanhisabnext.backup.summaryRows
import com.example.dukanhisabnext.util.toBengaliDigits

/**
 * Two full-screen layers above the whole app (the bottom bar too):
 *  1. while a restore or undo swaps the files (and the app restarts): "রিস্টোর হচ্ছে…", Back does nothing;
 *  2. once after the restart: what was restored, or what the start-up check found.
 */
@Composable
fun BackupOverlays() {
    val manager = (LocalContext.current.applicationContext as DokanHisabApp).backupManager
    val state by manager.state.collectAsStateWithLifecycle()
    val note by manager.startupNote.collectAsStateWithLifecycle()

    if (blocksApp(state)) {
        RestoringScreen()
    } else {
        note?.let { StartupNoteScreen(it, onDone = manager::dismissStartupNote) }
    }
}

@Composable
private fun RestoringScreen() {
    BackHandler(enabled = true) { /* cannot be left while the files are being swapped */ }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(modifier = Modifier.size(56.dp))
            Text(stringResource(R.string.restoring_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.restoring_hint),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Shown once, after the restart. */
@Composable
private fun StartupNoteScreen(note: StartupNote, onDone: () -> Unit) {
    BackHandler(enabled = true, onBack = onDone)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (note) {
                is StartupNote.Restored -> RestoredBody(note)
                is StartupNote.Recovery -> Text(
                    stringResource(note.outcome.messageRes() ?: R.string.backup_recovered_finished),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                StartupNote.Failed -> Text(
                    stringResource(R.string.restore_failed_note),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.backup_ok), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun RestoredBody(note: StartupNote.Restored) {
    Text(
        stringResource(if (note.isUndo) R.string.undo_done_title else R.string.restore_done_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    note.backupCreatedAt?.let {
        Text(stringResource(R.string.restore_done_date, backupDateTimeText(it, periodWords())), style = MaterialTheme.typography.bodyLarge)
    }
    Text(stringResource(R.string.restore_done_counts), style = MaterialTheme.typography.titleMedium)
    // Same rows as the confirm dialog; only the restored numbers.
    for (row in summaryRows(RestoreSummary(note.backupCreatedAt, note.counts, emptyMap()))) {
        Row(modifier = Modifier.fillMaxWidth(0.7f), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(summaryTableLabelRes(row.table)), style = MaterialTheme.typography.bodyLarge)
            Text(toBengaliDigits(row.inBackup.toString()), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}

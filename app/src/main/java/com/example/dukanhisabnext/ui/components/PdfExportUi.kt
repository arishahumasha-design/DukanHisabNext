package com.example.dukanhisabnext.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.pdf.EmptyReason
import com.example.dukanhisabnext.pdf.PdfResult
import com.example.dukanhisabnext.pdf.sharePdf
import kotlinx.coroutines.launch

/** The small "PDF" button of a screen's top bar (own icon, 48dp high). */
@Composable
fun PdfTopBarButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(painterResource(R.drawable.ic_pdf), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(6.dp))
        Text(stringResource(R.string.pdf_button), style = MaterialTheme.typography.labelLarge)
    }
}

private fun EmptyReason.messageRes(): Int = when (this) {
    EmptyReason.NO_DUE -> R.string.pdf_empty_due
    EmptyReason.NO_PRODUCTS -> R.string.pdf_empty_products
    EmptyReason.NO_REPORT_DATA -> R.string.pdf_empty_report
    EmptyReason.PICK_DATES -> R.string.pdf_empty_pick_dates
    EmptyReason.NO_ENTRIES -> R.string.pdf_empty_statement
}

/**
 * The whole PDF flow of a screen: when [open], the confirm dialog (with the privacy text, and for the product
 * list the "ক্রয়মূল্য দেখান" switch); on "PDF তৈরি করুন" a loading dialog while [make] builds the file in the
 * background; then the share sheet, or a short message when there was nothing to export or it failed.
 * [onClose] is called when the confirm dialog goes away.
 */
@Composable
fun PdfExportDialogs(
    open: Boolean,
    onClose: () -> Unit,
    make: suspend (showCost: Boolean) -> PdfResult,
    modifier: Modifier = Modifier,
    textRes: Int = R.string.pdf_privacy,
    withCostSwitch: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCost by rememberSaveable { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    val shareTitle = stringResource(R.string.pdf_share_title)

    if (open) {
        AppAlertDialog(
            modifier = modifier,
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.pdf_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(textRes), style = MaterialTheme.typography.bodyMedium)
                    if (withCostSwitch) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .toggleable(value = showCost, role = Role.Switch, onValueChange = { showCost = it }),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.pdf_show_cost), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(checked = showCost, onCheckedChange = null)
                        }
                    }
                }
            },
            confirmButton = {
                DialogTextButton(stringResource(R.string.pdf_create), onClick = {
                    onClose()
                    working = true
                    val cost = withCostSwitch && showCost
                    scope.launch {
                        val result = try {
                            make(cost)
                        } catch (e: Exception) {
                            PdfResult.Failed
                        }
                        working = false
                        when (result) {
                            is PdfResult.Done -> sharePdf(context, result.file, shareTitle)
                            is PdfResult.Empty -> message = result.reason.messageRes()
                            PdfResult.Failed -> message = R.string.pdf_failed
                        }
                    }
                })
            },
            dismissButton = { DialogTextButton(stringResource(R.string.btn_cancel), onClick = onClose) }
        )
    }

    if (working) {
        AppAlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                    Text(stringResource(R.string.pdf_creating), style = MaterialTheme.typography.bodyLarge)
                }
            }
        )
    }

    message?.let { res ->
        AppAlertDialog(
            onDismissRequest = { message = null },
            text = { Text(stringResource(res), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { DialogTextButton(stringResource(R.string.btn_close_dialog), onClick = { message = null }) }
        )
    }
}

package com.example.dukanhisabnext.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.dukanhisabnext.R

/**
 * "Is this amount right?" for a big amount (see ConfirmRules.kt). [amountText] is the amount in big
 * Bengali digits, for example "৳ ৫০,০০০" or "১,০০০ পিস". A tap outside does not close it:
 * "সংশোধন করুন" goes back to the form, "হ্যাঁ, সংরক্ষণ করুন" saves.
 */
@Composable
fun ConfirmAmountDialog(
    amountText: String,
    onFix: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onFix,
        title = { Text(stringResource(R.string.confirm_amount_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = amountText,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.confirm_amount_question),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.btn_confirm_save)) } },
        dismissButton = { TextButton(onClick = onFix) { Text(stringResource(R.string.btn_fix_amount)) } }
    )
}

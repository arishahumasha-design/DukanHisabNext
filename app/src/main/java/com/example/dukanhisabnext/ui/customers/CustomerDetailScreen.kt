package com.example.dukanhisabnext.ui.customers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.util.toBengaliDigits

/** A simple customer page: the details, the balance, and an edit button. */
@Composable
fun CustomerDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: CustomerDetailViewModel = appViewModel { app, args -> CustomerDetailViewModel(app.customerRepository, args) }
) {
    val customer by viewModel.customer.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SubScreenScaffold(title = stringResource(R.string.title_customer_detail), onBack = onBack) { padding ->
        val c = customer
        if (c != null) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(c.name, style = MaterialTheme.typography.headlineMedium)

                // Balance: always computed from the ledger, never stored on the customer.
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.label_balance), style = MaterialTheme.typography.labelLarge)
                        BalanceText(balance = balance ?: 0L, style = MaterialTheme.typography.headlineMedium)
                    }
                }

                // Tapping the number opens the phone dialer with the number filled in.
                // It does NOT call by itself and needs no permission.
                c.phone?.let { phone ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openDialer(context, phone) }
                    ) {
                        Text(stringResource(R.string.label_phone), style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = toBengaliDigits(phone),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                        Text(
                            text = stringResource(R.string.tap_to_call),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                c.address?.let { InfoRow(stringResource(R.string.label_address), it) }
                c.note?.let { InfoRow(stringResource(R.string.label_note), it) }

                Button(onClick = { onEdit(c.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_edit))
                }

                // M5: the due and payment history will be listed here.
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Opens the phone's dialer with [phone] typed in. ACTION_DIAL only SHOWS the dialer;
 * the user still has to press the call button. (ACTION_CALL would call at once and
 * needs a permission, so we do not use it.)
 */
private fun openDialer(context: Context, phone: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // A device without a dialer app (for example some tablets): do nothing.
    }
}

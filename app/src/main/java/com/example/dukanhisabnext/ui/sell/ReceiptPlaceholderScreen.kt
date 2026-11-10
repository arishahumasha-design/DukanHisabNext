package com.example.dukanhisabnext.ui.sell

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.toBengaliDigits

/**
 * A temporary receipt page shown right after a sale is saved: it proves the sale was saved and
 * shows its numbers. The real receipt (items, date, customer, sharing) is the next step.
 * [onNewSale] goes back to the product picker (the cart is empty by now).
 */
@Composable
fun ReceiptPlaceholderScreen(
    onNewSale: () -> Unit,
    viewModel: ReceiptPlaceholderViewModel = appViewModel { app, args ->
        ReceiptPlaceholderViewModel(app.saleRepository, args)
    }
) {
    val sale by viewModel.sale.collectAsStateWithLifecycle()

    SubScreenScaffold(title = stringResource(R.string.title_receipt), onBack = onNewSale) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Text(
                text = stringResource(R.string.sale_saved),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            sale?.let { s ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Line(stringResource(R.string.label_receipt_no), toBengaliDigits(s.receiptNo))
                        Line(stringResource(R.string.label_grand_total), money(s.total))
                        Line(stringResource(R.string.label_paid), money(s.paidAmount))
                        if (s.dueAmount > 0) Line(stringResource(R.string.label_due), money(s.dueAmount))
                        if (viewModel.change > 0) Line(stringResource(R.string.label_change), money(viewModel.change))
                    }
                }
            }

            Text(
                text = stringResource(R.string.receipt_coming_soon),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(onClick = onNewSale, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.btn_new_sale), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun money(paisa: Long): String = stringResource(R.string.price_format, formatTaka(paisa))

@Composable
private fun Line(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

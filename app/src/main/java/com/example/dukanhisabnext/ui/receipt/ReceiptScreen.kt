package com.example.dukanhisabnext.ui.receipt

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.receipt.ReceiptData
import com.example.dukanhisabnext.receipt.ReceiptLabels
import com.example.dukanhisabnext.receipt.buildReceiptText
import com.example.dukanhisabnext.receipt.receiptDateTime
import com.example.dukanhisabnext.receipt.receiptMoney
import com.example.dukanhisabnext.receipt.receiptPaidLabel
import com.example.dukanhisabnext.receipt.receiptQuantity
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.util.toBengaliDigits

/**
 * The receipt of a SAVED sale (loaded by the id in the route, never from the cart).
 * [onBack] is the back arrow (the same as the system Back button). [onNewSale] goes to the Sell tab.
 */
@Composable
fun ReceiptScreen(
    onBack: () -> Unit,
    onNewSale: () -> Unit,
    viewModel: ReceiptViewModel = appViewModel { app, args -> ReceiptViewModel(app.receiptRepository, args) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val labels = receiptLabels()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.share_chooser_title)

    SubScreenScaffold(title = stringResource(R.string.title_receipt), onBack = onBack) { padding ->
        when (val current = state) {
            ReceiptUiState.Loading -> Box(modifier = Modifier.padding(padding).fillMaxSize())

            ReceiptUiState.NotFound -> NotFoundContent(
                onNewSale = onNewSale,
                modifier = Modifier.padding(padding).fillMaxSize()
            )

            is ReceiptUiState.Loaded -> Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    ReceiptBody(data = current.data, labels = labels)
                }
                ActionBar(
                    onShare = { shareText(context, chooserTitle, buildReceiptText(current.data, labels)) },
                    onNewSale = onNewSale
                )
            }
        }
    }
}

/** A calm message when there is no such sale, with a way back to the Sell tab. */
@Composable
private fun NotFoundContent(onNewSale: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.receipt_not_found),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onNewSale,
            modifier = Modifier.padding(top = 24.dp).fillMaxWidth().heightIn(min = 56.dp)
        ) {
            Text(stringResource(R.string.btn_back_to_sell), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The two buttons at the bottom: শেয়ার করুন (share as text) and নতুন বিক্রয়. */
@Composable
private fun ActionBar(onShare: () -> Unit, onNewSale: () -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.btn_share_receipt), style = MaterialTheme.typography.titleMedium)
            }
            Button(onClick = onNewSale, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.btn_new_sale), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * Sends the receipt text to Android's share sheet (ACTION_SEND, text/plain). The system sheet
 * does the sharing, so the app needs no permission.
 */
private fun shareText(context: Context, title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(send, title))
    } catch (e: ActivityNotFoundException) {
        // No app can take text: nothing to do.
    }
}

// =========================================================================================
// The receipt itself
// =========================================================================================

/**
 * In this order: shop name, address, mobile; receipt number; date and time; customer (only if the
 * sale has one); the product table; subtotal; discount and delivery (only if above 0); মোট প্রদেয়;
 * পরিশোধ with the method; ফেরত (only right after a save); এই বিক্রির বাকি (only if above 0); the
 * customer's current total due (only if above 0); the note.
 */
@Composable
private fun ReceiptBody(data: ReceiptData, labels: ReceiptLabels) {
    fun money(paisa: Long) = receiptMoney(paisa, labels)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // ---- Shop ----
            Text(
                text = data.shopName ?: labels.shopPlaceholder,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            data.shopAddress?.let {
                Text(it, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            data.shopPhone?.let {
                Text(
                    text = "${labels.mobile}: ${toBengaliDigits(it)}",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ---- This sale ----
            KeyValue(labels.receiptNo, toBengaliDigits(data.receiptNo))
            KeyValue(labels.date, receiptDateTime(data.createdAt, labels))
            data.customerName?.let { name ->
                KeyValue(labels.customer, name)
                data.customerPhone?.let { KeyValue(labels.mobile, toBengaliDigits(it)) }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ---- Product table: name (as sold), quantity with unit, unit price, line total ----
            TableRow(
                stringResource(R.string.receipt_col_product),
                stringResource(R.string.receipt_col_quantity),
                stringResource(R.string.receipt_col_price),
                stringResource(R.string.receipt_col_total),
                header = true
            )
            data.lines.forEach { line ->
                TableRow(
                    line.name,
                    receiptQuantity(line, labels),
                    money(line.unitPrice),
                    money(line.lineTotal)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ---- Totals ----
            KeyValue(labels.subtotal, money(data.subtotal))
            if (data.showDiscount) KeyValue(labels.discount, "-" + money(data.discount))
            if (data.showDelivery) KeyValue(labels.delivery, money(data.deliveryCharge))
            KeyValue(labels.grandTotal, money(data.total), bold = true, large = true)
            KeyValue(receiptPaidLabel(data, labels), money(data.paid))
            if (data.showChange) KeyValue(labels.change, money(data.change))
            if (data.showSaleDue) KeyValue(labels.saleDue, money(data.saleDue), bold = true)
            data.customerTotalDue?.let { KeyValue(labels.customerTotalDue, money(it), bold = true) }

            data.note?.let {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                KeyValue(labels.note, it)
            }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String, bold: Boolean = false, large: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End
        )
    }
}

/** One table row. A long product name simply wraps onto more lines. */
@Composable
private fun TableRow(name: String, quantity: String, price: String, total: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
    val weight = if (header) FontWeight.Bold else FontWeight.Normal
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(name, modifier = Modifier.weight(1.7f), style = style, fontWeight = weight)
        Text(quantity, modifier = Modifier.weight(1.1f), style = style, fontWeight = weight, textAlign = TextAlign.End)
        Text(price, modifier = Modifier.weight(1.1f), style = style, fontWeight = weight, textAlign = TextAlign.End)
        Text(total, modifier = Modifier.weight(1.2f), style = style, fontWeight = weight, textAlign = TextAlign.End)
    }
}

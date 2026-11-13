package com.example.dukanhisabnext.ui.receipt

import com.example.dukanhisabnext.ui.theme.AppCard
import com.example.dukanhisabnext.ui.components.LoadingIndicator
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
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.dukanhisabnext.ui.components.AppAlertDialog
import androidx.compose.material3.ButtonDefaults
import com.example.dukanhisabnext.ui.components.AppTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import com.example.dukanhisabnext.cancel.CancelFeedback
import com.example.dukanhisabnext.cancel.CancelSummaryLine
import com.example.dukanhisabnext.cancel.MAX_CANCEL_REASON_LENGTH
import com.example.dukanhisabnext.cancel.cancelButtonEnabled
import com.example.dukanhisabnext.cancel.cancelSummaryFor
import com.example.dukanhisabnext.cancel.reasonLength
import com.example.dukanhisabnext.cancel.showCancelButton
import com.example.dukanhisabnext.receipt.CancelNotice
import com.example.dukanhisabnext.receipt.cancelNotice
import androidx.compose.material3.Button
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
import com.example.dukanhisabnext.ui.components.DashedDivider
import com.example.dukanhisabnext.ui.components.DestructiveOutlinedButton
import com.example.dukanhisabnext.ui.components.DestructiveTextButton
import com.example.dukanhisabnext.ui.components.DialogTextButton
import com.example.dukanhisabnext.ui.components.PrimaryButton
import com.example.dukanhisabnext.ui.components.SecondaryButton
import com.example.dukanhisabnext.ui.theme.AppCard
import com.example.dukanhisabnext.ui.theme.Spacing
import androidx.compose.material3.CardDefaults
import com.example.dukanhisabnext.util.toBengaliDigits

/**
 * The receipt of a SAVED sale (loaded by the id in the route, never from the cart).
 * [onBack] is the back arrow (the same as the system Back button). [onNewSale] goes to the Sell tab.
 */
@Composable
fun ReceiptScreen(
    onBack: () -> Unit,
    onNewSale: () -> Unit,
    viewModel: ReceiptViewModel = appViewModel { app, args ->
        ReceiptViewModel(app.receiptRepository, app.saleRepository, args)
    }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cancelState by viewModel.cancel.collectAsStateWithLifecycle()
    val labels = receiptLabels()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.share_chooser_title)

    SubScreenScaffold(title = stringResource(R.string.title_receipt), onBack = onBack) { padding ->
        when (val current = state) {
            ReceiptUiState.Loading -> LoadingIndicator(Modifier.padding(padding).fillMaxSize())

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
                    // The red banner when this sale was cancelled (date, time and reason).
                    cancelNotice(current.data, labels)?.let { CancelBanner(it) }
                    ReceiptBody(data = current.data, labels = labels)

                    // A calm message when a cancel did not go through (nothing was changed).
                    cancelState.feedback?.let { feedback ->
                        Text(
                            text = stringResource(feedbackText(feedback)),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // Hidden once cancelled; disabled while a cancel is running.
                    if (showCancelButton(current.data)) {
                        DestructiveOutlinedButton(
                            text = stringResource(R.string.btn_cancel_sale),
                            onClick = viewModel::onCancelClick,
                            enabled = cancelButtonEnabled(cancelState.cancelling),
                            modifier = Modifier.padding(top = Spacing.l).fillMaxWidth()
                        )
                    }
                }
                if (cancelState.dialogOpen) {
                    CancelDialog(
                        summary = cancelSummaryFor(current.data, cancelDialogLabels(), labels),
                        state = cancelState,
                        onReasonChange = viewModel::onReasonChange,
                        onConfirm = viewModel::onConfirm,
                        onDismiss = viewModel::onDismissDialog
                    )
                }
                ActionBar(
                    onShare = { shareText(context, chooserTitle, buildReceiptText(current.data, labels)) },
                    onNewSale = onNewSale
                )
            }
        }
    }
}

private fun feedbackText(feedback: CancelFeedback): Int = when (feedback) {
    CancelFeedback.SALE_NOT_FOUND -> R.string.cancel_err_not_found
    CancelFeedback.REASON_TOO_LONG -> R.string.cancel_err_reason_long
    CancelFeedback.UNEXPECTED -> R.string.cancel_err_unexpected
}

/** The red "বাতিল করা হয়েছে" banner with the Dhaka date and time and the reason (if any). */
@Composable
private fun CancelBanner(notice: CancelNotice) {
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.m),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        border = null
    ) {
        Column(modifier = Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = notice.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(notice.dateTime, color = MaterialTheme.colorScheme.onErrorContainer)
            notice.reason?.let { Text(it, color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

/** The confirm dialog: a plain summary, an optional reason (at most 200 characters) and two buttons. */
@Composable
private fun CancelDialog(
    summary: List<CancelSummaryLine>,
    state: CancelUiState,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cancel_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                summary.forEach { line ->
                    Text(
                        text = "• " + line.text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (line.isWarning) FontWeight.Bold else FontWeight.Normal,
                        color = if (line.isWarning) MaterialTheme.colorScheme.error else Color.Unspecified
                    )
                }
                AppTextField(
                    value = state.reason,
                    onValueChange = onReasonChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text(stringResource(R.string.cancel_reason_label)) },
                    supportingText = {
                        Text(
                            stringResource(
                                R.string.cancel_reason_counter,
                                toBengaliDigits(reasonLength(state.reason).toString()),
                                toBengaliDigits(MAX_CANCEL_REASON_LENGTH.toString())
                            )
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !state.cancelling
                )
                state.feedback?.let {
                    Text(stringResource(feedbackText(it)), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            DestructiveTextButton(
                text = stringResource(if (state.cancelling) R.string.cancel_in_progress else R.string.cancel_yes),
                onClick = onConfirm,
                enabled = !state.cancelling
            )
        },
        dismissButton = {
            DialogTextButton(stringResource(R.string.cancel_no), onDismiss, enabled = !state.cancelling)
        }
    )
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
        PrimaryButton(
            text = stringResource(R.string.btn_back_to_sell),
            onClick = onNewSale,
            modifier = Modifier.padding(top = Spacing.xl).fillMaxWidth()
        )
    }
}

/** The two buttons at the bottom: শেয়ার করুন (share as text) and নতুন বিক্রয়. */
@Composable
private fun ActionBar(onShare: () -> Unit, onNewSale: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            SecondaryButton(text = stringResource(R.string.btn_share_receipt), onClick = onShare, modifier = Modifier.weight(1f))
            PrimaryButton(text = stringResource(R.string.btn_new_sale), onClick = onNewSale, modifier = Modifier.weight(1f))
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

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // ---- Shop ----
            Text(
                text = data.shopName ?: labels.shopPlaceholder,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineMedium,
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
            DashedDivider(modifier = Modifier.padding(vertical = Spacing.xs))

            // ---- This sale ----
            KeyValue(labels.receiptNo, toBengaliDigits(data.receiptNo))
            KeyValue(labels.date, receiptDateTime(data.saleDate, labels))
            data.customerName?.let { name ->
                KeyValue(labels.customer, name)
                data.customerPhone?.let { KeyValue(labels.mobile, toBengaliDigits(it)) }
            }
            DashedDivider(modifier = Modifier.padding(vertical = Spacing.xs))

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
            DashedDivider(modifier = Modifier.padding(vertical = Spacing.xs))

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
                DashedDivider(modifier = Modifier.padding(vertical = Spacing.xs))
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

package com.example.dukanhisabnext.ui.customers

import com.example.dukanhisabnext.ui.theme.AppCard
import com.example.dukanhisabnext.ui.components.LoadingIndicator
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import com.example.dukanhisabnext.ui.components.InitialAvatar
import com.example.dukanhisabnext.ui.components.PillVariant
import com.example.dukanhisabnext.ui.components.PrimaryButton
import com.example.dukanhisabnext.ui.components.SecondaryButton
import com.example.dukanhisabnext.ui.components.SectionHeader
import com.example.dukanhisabnext.ui.components.StatusPill
import com.example.dukanhisabnext.ui.components.InfoCard
import com.example.dukanhisabnext.ui.theme.AppTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.dao.LedgerHistoryRow
import com.example.dukanhisabnext.ledger.HistoryDay
import com.example.dukanhisabnext.ledger.HistoryLabels
import com.example.dukanhisabnext.ledger.LedgerAction
import com.example.dukanhisabnext.ledger.addsToDue
import com.example.dukanhisabnext.ledger.historyAmountText
import com.example.dukanhisabnext.ledger.historyNote
import com.example.dukanhisabnext.ledger.historyTitle
import com.example.dukanhisabnext.ledger.isHighlighted
import com.example.dukanhisabnext.ledger.opensReceipt
import com.example.dukanhisabnext.ledger.showsCancelledTag
import com.example.dukanhisabnext.ledger.showsTime
import com.example.dukanhisabnext.ledger.writtenOnText
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.sales.dayHeaderText
import com.example.dukanhisabnext.ui.sales.saleTimeText
import com.example.dukanhisabnext.util.DayPeriod
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.toBengaliDigits
import kotlinx.coroutines.delay

/**
 * The customer page: details, the balance, the action buttons, and the full history (the baki
 * khata of this customer) grouped by Dhaka day, newest first. The history is a lazy list, so
 * thousands of entries stay fast.
 *
 * [onAction] opens the entry form. [onOpenReceipt] opens the receipt of a sale row.
 * [justSaved] is true right after an entry was saved (a short green message; [onSavedShown] clears it).
 */
@Composable
fun CustomerDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onAction: (customerId: Long, action: LedgerAction) -> Unit,
    onOpenReceipt: (saleId: Long) -> Unit,
    justSaved: Boolean,
    onSavedShown: () -> Unit,
    viewModel: CustomerDetailViewModel = appViewModel { app, args ->
        CustomerDetailViewModel(app.customerRepository, app.ledgerRepository, args)
    }
) {
    val customer by viewModel.customer.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The words from strings.xml, handed to the plain functions.
    val labels = historyLabels()
    val methods = methodLabels()
    val monthNames = stringArrayResource(R.array.months_bn).toList()
    val periods = mapOf(
        DayPeriod.MORNING to stringResource(R.string.period_morning),
        DayPeriod.NOON to stringResource(R.string.period_noon),
        DayPeriod.AFTERNOON to stringResource(R.string.period_afternoon),
        DayPeriod.EVENING to stringResource(R.string.period_evening),
        DayPeriod.NIGHT to stringResource(R.string.period_night)
    )
    val writtenOnTemplate = stringResource(R.string.ledger_written_on, "{date}")

    // The "saved" message goes away by itself after a few seconds.
    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(3_000)
            onSavedShown()
        }
    }

    SubScreenScaffold(title = stringResource(R.string.title_customer_detail), onBack = onBack) { padding ->
        val c = customer ?: run {
            LoadingIndicator(Modifier.padding(padding).fillMaxSize())
            return@SubScreenScaffold
        }

        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            // ---- The details ----
            item(key = "info") {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // The balance hero card: who, and how much is due now. Always computed from the
                    // ledger, never stored on the customer. Amber while they owe, soft green otherwise.
                    val status = balanceStatus(balance ?: 0L)
                    val colors = AppTheme.colors
                    val heroContainer = if (status == BalanceStatus.OWES) colors.amberContainer else MaterialTheme.colorScheme.primaryContainer
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = heroContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                        border = null
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                InitialAvatar(name = c.name, size = 48.dp)
                                Text(c.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.label_balance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            BalanceText(balance = balance ?: 0L, style = MaterialTheme.typography.headlineLarge)
                        }
                    }

                    // The contact details in one card. Tapping the number opens the phone dialer with the
                    // number filled in. It does NOT call by itself and needs no permission.
                    if (c.phone != null || c.address != null || c.note != null) {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                c.phone?.let { phone ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { openDialer(context, phone) },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(painterResource(R.drawable.ic_phone), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                        Column {
                                            Text(
                                                text = toBengaliDigits(phone),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = stringResource(R.string.tap_to_call),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                c.address?.let { InfoRow(stringResource(R.string.label_address), it) }
                                c.note?.let { InfoRow(stringResource(R.string.label_note), it) }
                            }
                        }
                    }

                    SecondaryButton(text = stringResource(R.string.btn_edit), onClick = { onEdit(c.id) }, modifier = Modifier.fillMaxWidth())
                }
            }

            // ---- The short "saved" message ----
            if (justSaved) {
                item(key = "saved") {
                    InfoCard(
                        text = stringResource(R.string.ledger_saved),
                        variant = PillVariant.Paid,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }
            }

            // ---- The action buttons: a 2x2 grid ----
            item(key = "actions") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    actions.chunked(2).forEachIndexed { rowIndex, pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEachIndexed { i, action ->
                                val modifier = Modifier.weight(1f)
                                // The first button (টাকা পেলাম) is the main one.
                                if (rowIndex == 0 && i == 0) {
                                    PrimaryButton(text = stringResource(action.buttonRes()), onClick = { onAction(c.id, action) }, modifier = modifier)
                                } else {
                                    SecondaryButton(text = stringResource(action.buttonRes()), onClick = { onAction(c.id, action) }, modifier = modifier)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // ---- The history ----
            item(key = "history-title") {
                SectionHeader(
                    text = stringResource(R.string.ledger_history_title),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )
            }

            val days = history
            if (days != null && days.isEmpty()) {
                item(key = "history-empty") {
                    Text(
                        text = stringResource(R.string.ledger_history_empty),
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            days?.forEach { day ->
                item(key = "day-${day.date.toEpochDay()}") {
                    DayHeader(dayHeaderText(day.date, monthNames))
                }
                items(day.rows, key = { "entry-${it.entry.id}" }) { row ->
                    HistoryRow(
                        row = row,
                        labels = labels,
                        methodName = { methods.getValue(it) },
                        timeText = { saleTimeText(it) { period -> periods.getValue(period) } },
                        writtenOn = { entry -> writtenOnText(entry) { writtenOnTemplate.replace("{date}", it) } },
                        onOpenReceipt = onOpenReceipt
                    )
                }
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

@Composable
private fun DayHeader(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(text = text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One history row: the type (with receipt number, method or note), the amount with + or −, the
 * note, the time (or "লেখা হয়েছে <date>" for a back-dated entry). A sale row and a cancel-reversal
 * row open the receipt; a cancelled sale shows a grey "বাতিল" tag. The opening due is highlighted.
 */
@Composable
internal fun HistoryRow(
    row: LedgerHistoryRow,
    labels: HistoryLabels,
    methodName: (com.example.dukanhisabnext.data.entity.PaymentMethod) -> String,
    timeText: (Long) -> String,
    writtenOn: (com.example.dukanhisabnext.data.entity.LedgerEntry) -> String?,
    onOpenReceipt: (Long) -> Unit,
    // Used by the Khata time chips: the customer's name is the first line, and a tap can open the customer.
    customerName: String? = null,
    onClick: (() -> Unit)? = null
) {
    val entry = row.entry
    val clickable = opensReceipt(row)
    val saleId = entry.saleId

    ListItem(
        modifier = when {
            onClick != null -> Modifier.clickable(onClick = onClick)
            clickable && saleId != null -> Modifier.clickable { onOpenReceipt(saleId) }
            else -> Modifier
        },
        colors = if (isHighlighted(entry)) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        } else {
            ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
        },
        headlineContent = {
            Text(customerName ?: historyTitle(row, labels, methodName), style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = {
            Column {
                if (customerName != null) {
                    Text(historyTitle(row, labels, methodName), style = MaterialTheme.typography.bodyMedium)
                }
                historyNote(row)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                val small = MaterialTheme.typography.bodySmall
                val grey = MaterialTheme.colorScheme.onSurfaceVariant
                if (showsTime(entry)) {
                    Text(timeText(entry.entryDate), style = small, color = grey)
                }
                writtenOn(entry)?.let { Text(it, style = small, color = grey) }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = historyAmountText(entry, formatTaka(entry.amount), labels.taka),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    // Amber when it adds to the due, green when it takes away (the same colours as the balance).
                    color = if (addsToDue(entry)) {
                        BalanceStatus.OWES.color()
                    } else {
                        BalanceStatus.ADVANCE.color()
                    }
                )
                if (showsCancelledTag(row)) {
                    StatusPill(stringResource(R.string.sale_cancelled_tag), PillVariant.Cancelled)
                }
            }
        }
    )
    HorizontalDivider()
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

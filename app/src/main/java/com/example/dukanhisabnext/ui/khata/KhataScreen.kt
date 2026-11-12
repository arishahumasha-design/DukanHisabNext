package com.example.dukanhisabnext.ui.khata

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.dao.KhataCustomerRow
import com.example.dukanhisabnext.ledger.KhataAllSummary
import com.example.dukanhisabnext.ledger.KhataPeriod
import com.example.dukanhisabnext.ledger.KhataSort
import com.example.dukanhisabnext.ledger.PeriodSummary
import com.example.dukanhisabnext.ledger.SummaryLine
import com.example.dukanhisabnext.ledger.lastEntryText
import com.example.dukanhisabnext.ledger.opensReceipt
import com.example.dukanhisabnext.ledger.shownSummaryLines
import com.example.dukanhisabnext.ledger.showsDueBreakdown
import com.example.dukanhisabnext.ledger.writtenOnText
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.DateRangePickerFlow
import com.example.dukanhisabnext.ui.components.customRangeText
import com.example.dukanhisabnext.ui.customers.BalanceStatus
import com.example.dukanhisabnext.ui.customers.BalanceText
import com.example.dukanhisabnext.ui.customers.HistoryRow
import com.example.dukanhisabnext.ui.customers.color
import com.example.dukanhisabnext.ui.customers.historyLabels
import com.example.dukanhisabnext.ui.customers.methodLabels
import com.example.dukanhisabnext.ui.sales.dayHeaderText
import com.example.dukanhisabnext.ui.sales.saleTimeText
import com.example.dukanhisabnext.util.DayPeriod
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.toBengaliDigits
import kotlin.math.abs

/**
 * "বাকির খাতা", the Khata tab: one screen with a row of chips. "সব" shows the customers (the due
 * list); the other chips show the ledger entries of all customers in that time. The chip, the
 * custom days, the sort, the toggle and the search text live in the ViewModel (SavedStateHandle).
 *
 * [onNewDue] gets the customer chosen for "নতুন বাকি"; [onAddCustomer] opens the add-customer form
 * from the picker; [onOpenCustomer] and [onOpenReceipt] open a customer or a sale's receipt.
 */
@Composable
fun KhataScreen(
    onOpenCustomer: (Long) -> Unit,
    onOpenReceipt: (Long) -> Unit,
    onNewDue: (Long) -> Unit,
    onAddCustomer: () -> Unit,
    viewModel: KhataViewModel = appViewModel { app, args -> KhataViewModel(app.ledgerRepository, args) }
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val customers by viewModel.allCustomers.collectAsStateWithLifecycle()

    // Dialog and picker steps survive rotation too.
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var rangePickerOpen by rememberSaveable { mutableStateOf(false) } // the two-step "তারিখ বাছুন" picker

    // The words from strings.xml, handed to the plain functions.
    val monthNames = stringArrayResource(R.array.months_bn).toList()
    val untilTemplate = stringResource(R.string.khata_until, "{date}")
    val labels = historyLabels()
    val methods = methodLabels()
    val periods = mapOf(
        DayPeriod.MORNING to stringResource(R.string.period_morning),
        DayPeriod.NOON to stringResource(R.string.period_noon),
        DayPeriod.AFTERNOON to stringResource(R.string.period_afternoon),
        DayPeriod.EVENING to stringResource(R.string.period_evening),
        DayPeriod.NIGHT to stringResource(R.string.period_night)
    )
    val writtenOnTemplate = stringResource(R.string.ledger_written_on, "{date}")

    // This tab sits inside the app frame, which already keeps clear of the system bars.
    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                text = stringResource(R.string.khata_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            val current = ui
            val controls = current?.controls ?: KhataControls()

            // ---- The chips (they scroll sideways) ----
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KhataPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = controls.period == period,
                        onClick = {
                            if (period == KhataPeriod.CUSTOM) rangePickerOpen = true else viewModel.onPeriodChange(period)
                        },
                        label = {
                            val text = if (period == KhataPeriod.CUSTOM) {
                                customRangeText(controls.customFrom, controls.customTo)
                            } else {
                                null
                            }
                            Text(text ?: stringResource(period.labelRes()), style = MaterialTheme.typography.titleSmall)
                        }
                    )
                }
            }

            if (current == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) // still loading
            } else {
                // ---- The summary box ----
                when (val body = current.body) {
                    is KhataBody.All -> AllSummaryCard(body.summary)
                    is KhataBody.Period -> if (body.rangeReady) PeriodSummaryCard(body.summary)
                }

                // ---- Search (works in every chip) ----
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_customers)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                            }
                        }
                    }
                )

                // ---- "সব" only: the "যাদের বাকি আছে" toggle and the sort ----
                if (controls.isAll) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = controls.onlyOwing,
                            onClick = { viewModel.onOnlyOwingChange(!controls.onlyOwing) },
                            label = { Text(stringResource(R.string.khata_filter_owing), style = MaterialTheme.typography.titleSmall) }
                        )
                        AssistChip(
                            onClick = { sortOpen = true },
                            label = { Text(stringResource(R.string.khata_sort_button)) },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
                        )
                    }
                }
                HorizontalDivider()

                // ---- The list ----
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (val body = current.body) {
                        is KhataBody.All -> {
                            val empty = khataEmptyKind(
                                KhataPeriod.ALL, rangeReady = true, searching = controls.searching,
                                totalCustomers = body.totalCustomers, windowCount = 0, shownCount = body.rows.size
                            )
                            if (empty != null) {
                                EmptyMessage(empty, period = KhataPeriod.ALL)
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(body.rows, key = { it.customer.id }) { row ->
                                        CustomerDueRow(
                                            row = row,
                                            lastText = lastEntryText(row.lastEntryDate, monthNames) { untilTemplate.replace("{date}", it) },
                                            onClick = { onOpenCustomer(row.customer.id) }
                                        )
                                    }
                                }
                            }
                        }

                        is KhataBody.Period -> {
                            val empty = khataEmptyKind(
                                controls.period, body.rangeReady, controls.searching,
                                totalCustomers = current.totalCustomers, windowCount = body.windowCount, shownCount = body.shownCount
                            )
                            if (empty != null) {
                                EmptyMessage(empty, period = controls.period)
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    body.days.forEach { day ->
                                        item(key = "day-${day.date.toEpochDay()}") {
                                            DayHeader(dayHeaderText(day.date, monthNames))
                                        }
                                        items(day.rows, key = { "entry-${it.entry.id}" }) { entryRow ->
                                            val history = entryRow.asHistoryRow()
                                            HistoryRow(
                                                row = history,
                                                labels = labels,
                                                methodName = { methods.getValue(it) },
                                                timeText = { saleTimeText(it) { period -> periods.getValue(period) } },
                                                writtenOn = { e -> writtenOnText(e) { writtenOnTemplate.replace("{date}", it) } },
                                                onOpenReceipt = onOpenReceipt,
                                                customerName = entryRow.customerName,
                                                // A sale row opens its receipt; every other row opens the customer.
                                                onClick = {
                                                    val saleId = entryRow.entry.saleId
                                                    if (opensReceipt(history) && saleId != null) {
                                                        onOpenReceipt(saleId)
                                                    } else {
                                                        onOpenCustomer(entryRow.entry.customerId)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- The bottom button: নতুন বাকি ----
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { pickerOpen = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().heightIn(min = 56.dp)
                ) {
                    Text(stringResource(R.string.khata_new_due), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    // ---- Sort dialog ----
    if (sortOpen) {
        SortDialog(
            selected = ui?.controls?.sort ?: KhataSort.entries.first(),
            onSelect = {
                viewModel.onSortChange(it)
                sortOpen = false
            },
            onDismiss = { sortOpen = false }
        )
    }

    // ---- The customer picker for "নতুন বাকি" ----
    if (pickerOpen) {
        CustomerPickDialog(
            customers = customers,
            onPick = {
                pickerOpen = false
                onNewDue(it)
            },
            onAddCustomer = {
                pickerOpen = false
                onAddCustomer()
            },
            onDismiss = { pickerOpen = false }
        )
    }

    // ---- "তারিখ বাছুন": the first picker, then the second (shared with the expense list) ----
    if (rangePickerOpen) {
        DateRangePickerFlow(
            today = viewModel.today(),
            initialFrom = ui?.controls?.customFrom,
            initialTo = ui?.controls?.customTo,
            onConfirm = { from, to ->
                viewModel.onCustomRange(from, to)
                rangePickerOpen = false
            },
            onDismiss = { rangePickerOpen = false }
        )
    }
}

private fun KhataPeriod.labelRes(): Int = when (this) {
    KhataPeriod.ALL -> R.string.khata_chip_all
    KhataPeriod.TODAY -> R.string.khata_chip_today
    KhataPeriod.LAST_7_DAYS -> R.string.khata_chip_7days
    KhataPeriod.THIS_MONTH -> R.string.khata_chip_month
    KhataPeriod.THIS_YEAR -> R.string.khata_chip_year
    KhataPeriod.CUSTOM -> R.string.khata_chip_custom
}

private fun KhataSort.labelRes(): Int = when (this) {
    KhataSort.LAST_ENTRY_NEWEST -> R.string.khata_sort_newest
    KhataSort.LAST_ENTRY_OLDEST -> R.string.khata_sort_oldest
    KhataSort.AMOUNT_LOW_TO_HIGH -> R.string.khata_sort_low
    KhataSort.AMOUNT_HIGH_TO_LOW -> R.string.khata_sort_high
}

private fun SummaryLine.labelRes(): Int = when (this) {
    SummaryLine.DUE_GIVEN -> R.string.khata_line_due_given
    SummaryLine.RECEIVED -> R.string.khata_line_received
    SummaryLine.OPENING_DUE -> R.string.khata_line_opening
    SummaryLine.ADVANCE_RETURNED -> R.string.khata_line_advance_returned
    SummaryLine.CORRECTION -> R.string.khata_line_correction
    SummaryLine.SALE_CANCEL -> R.string.khata_line_sale_cancel
    SummaryLine.NET -> R.string.khata_line_net
}

// =========================================================================================
// The summary boxes
// =========================================================================================

/** "সব": পাবো, অগ্রিম and নিট for the whole shop. */
@Composable
private fun AllSummaryCard(summary: KhataAllSummary) {
    Card(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryColumn(stringResource(R.string.khata_sum_receivable), summary.receivable, BalanceStatus.OWES, Modifier.weight(1f))
            SummaryColumn(stringResource(R.string.khata_sum_advance), summary.advance, BalanceStatus.ADVANCE, Modifier.weight(1f))
            val netStatus = when {
                summary.net > 0 -> BalanceStatus.OWES
                summary.net < 0 -> BalanceStatus.ADVANCE
                else -> BalanceStatus.CLEAR
            }
            SummaryColumn(stringResource(R.string.khata_sum_net), summary.net, netStatus, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryColumn(label: String, amount: Long, status: BalanceStatus, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            text = stringResource(R.string.price_format, formatTaka(abs(amount))),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = status.color()
        )
    }
}

/** A period: বাকি দিলাম (with its small breakdown), টাকা পেলাম, the other lines only when not 0, and নিট পরিবর্তন. */
@Composable
private fun PeriodSummaryCard(summary: PeriodSummary) {
    val taka = stringResource(R.string.unit_taka)
    Card(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            shownSummaryLines(summary).forEach { line ->
                if (line == SummaryLine.NET) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                val amount = summaryAmount(summary, line)
                val bold = line == SummaryLine.NET || line == SummaryLine.DUE_GIVEN || line == SummaryLine.RECEIVED
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(line.labelRes()),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = summaryAmountText(line, amount, taka, formatTaka(abs(amount))),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
                    )
                }
                // The small breakdown under বাকি দিলাম: "বিক্রিতে ৳X · হাতে ৳Y".
                if (line == SummaryLine.DUE_GIVEN && showsDueBreakdown(summary)) {
                    Text(
                        text = stringResource(
                            R.string.khata_line_due_breakdown,
                            stringResource(R.string.price_format, formatTaka(summary.dueOnSales)),
                            stringResource(R.string.price_format, formatTaka(summary.dueByHand))
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// =========================================================================================
// Rows, headers, empty states
// =========================================================================================

/** One customer of the "সব" list: name, mobile, the "… পর্যন্ত" line, and the balance. */
@Composable
private fun CustomerDueRow(row: KhataCustomerRow, lastText: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(row.customer.name, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Column {
                row.customer.phone?.let { Text(toBengaliDigits(it), style = MaterialTheme.typography.bodyMedium) }
                lastText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        trailingContent = { BalanceText(balance = row.balance, style = MaterialTheme.typography.titleMedium) }
    )
    HorizontalDivider()
}

@Composable
private fun DayHeader(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyMessage(kind: KhataEmpty, period: KhataPeriod) {
    val message = when (kind) {
        KhataEmpty.NO_CUSTOMERS -> R.string.khata_empty_customers
        KhataEmpty.NO_MATCH -> if (period == KhataPeriod.ALL) R.string.empty_customer_search else R.string.khata_empty_entries_match
        KhataEmpty.NO_ENTRIES -> R.string.khata_empty_entries
        KhataEmpty.PICK_DATES -> R.string.khata_empty_pick_dates
    }
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
    }
}

// =========================================================================================
// Dialogs
// =========================================================================================

/** The 4 sort options with a radio button each. Choosing one applies it and closes. */
@Composable
private fun SortDialog(selected: KhataSort, onSelect: (KhataSort) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.khata_sort_title)) },
        text = {
            Column {
                KhataSort.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(
                            text = stringResource(option.labelRes()),
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close_dialog)) }
        }
    )
}

/**
 * "কাস্টমার তালিকা" for "নতুন বাকি": a search box, every customer A to Z (name and mobile), and the
 * buttons "বন্ধ করুন" and "কাস্টমার যুক্ত করুন". Tapping a customer picks them.
 */
@Composable
private fun CustomerPickDialog(
    customers: List<KhataCustomerRow>?,
    onPick: (Long) -> Unit,
    onAddCustomer: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = customers?.let { searchedCustomersAtoZ(it, query) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.82f)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dialog_customer_list_title), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_customers)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                            }
                        }
                    }
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        shown == null -> Unit
                        shown.isEmpty() -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            val message = if (query.isNotBlank()) R.string.empty_customer_search else R.string.empty_customers
                            Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
                        }
                        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(shown, key = { it.customer.id }) { row ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 56.dp)
                                        .clickable { onPick(row.customer.id) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(row.customer.name, style = MaterialTheme.typography.titleMedium)
                                    row.customer.phone?.let {
                                        Text(toBengaliDigits(it), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.btn_close_dialog))
                    }
                    Button(onClick = onAddCustomer, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.btn_add_customer_dialog), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

package com.example.dukanhisabnext.ui.report

import com.example.dukanhisabnext.ui.theme.AppCard
import com.example.dukanhisabnext.ui.components.LoadingIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.dao.KhataCustomerRow
import com.example.dukanhisabnext.data.dao.SaleListRow
import com.example.dukanhisabnext.report.InfoLineKind
import com.example.dukanhisabnext.report.ProfitLineKind
import com.example.dukanhisabnext.report.ProfitReport
import com.example.dukanhisabnext.report.SalesReport
import com.example.dukanhisabnext.report.approximateNote
import com.example.dukanhisabnext.report.profitInfoLines
import com.example.dukanhisabnext.report.profitLines
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.DateRangePickerFlow
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.components.customRangeText
import com.example.dukanhisabnext.ui.customers.BalanceText
import com.example.dukanhisabnext.ui.expense.SummaryCard
import com.example.dukanhisabnext.ui.expense.labelRes
import com.example.dukanhisabnext.ui.khata.AllSummaryCard
import com.example.dukanhisabnext.ui.khata.PeriodSummaryCard
import com.example.dukanhisabnext.ui.sales.customerLabel
import com.example.dukanhisabnext.ui.sales.dayHeaderText
import com.example.dukanhisabnext.ui.sales.groupByDhakaDay
import com.example.dukanhisabnext.ui.sales.saleTimeText
import com.example.dukanhisabnext.ui.components.EmptyState
import com.example.dukanhisabnext.ui.components.IconTile
import com.example.dukanhisabnext.ui.components.InfoCard
import com.example.dukanhisabnext.ui.components.InitialAvatar
import com.example.dukanhisabnext.ui.components.PillVariant
import com.example.dukanhisabnext.ui.components.SectionHeader
import com.example.dukanhisabnext.ui.components.StatusPill
import com.example.dukanhisabnext.ui.theme.Spacing
import com.example.dukanhisabnext.ui.theme.AppTheme
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import com.example.dukanhisabnext.ui.sales.showDueTag
import com.example.dukanhisabnext.util.DayPeriod
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.toBengaliDigits
import kotlin.math.abs

/**
 * "রিপোর্ট": four tabs (বিক্রি | বাকি | খরচ | লাভ-ক্ষতি) and one row of time chips (আজ | ৭ দিন | এই মাস |
 * এই বছর | তারিখ বাছুন) shared by all of them. The tab, the chip and the custom days are kept in the
 * ViewModel (SavedStateHandle). Every number is the same as on the screen that already shows it.
 * [onOpenReceipt] opens a sale's receipt, [onOpenCustomer] a customer.
 */
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    onOpenReceipt: (Long) -> Unit,
    onOpenCustomer: (Long) -> Unit,
    viewModel: ReportViewModel = appViewModel { app, args ->
        ReportViewModel(app.reportRepository, app.ledgerRepository, app.expenseRepository, app.saleRepository, args)
    }
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var rangePickerOpen by rememberSaveable { mutableStateOf(false) }
    val controls = ui?.controls ?: ReportControls()

    SubScreenScaffold(title = stringResource(R.string.title_reports), onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // ---- The four tabs ----
            PrimaryTabRow(
                selectedTabIndex = controls.tab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                ReportTab.entries.forEach { tab ->
                    Tab(
                        selected = controls.tab == tab,
                        onClick = { viewModel.onTabChange(tab) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                stringResource(tab.labelRes()),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (controls.tab == tab) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // ---- The time chips (they scroll sideways) ----
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = controls.period == period,
                        onClick = {
                            if (period == ReportPeriod.CUSTOM) rangePickerOpen = true else viewModel.onPeriodChange(period)
                        },
                        label = {
                            val text = if (period == ReportPeriod.CUSTOM) customRangeText(controls.customFrom, controls.customTo) else null
                            Text(text ?: stringResource(period.labelRes()), style = MaterialTheme.typography.titleSmall)
                        }
                    )
                }
            }
            HorizontalDivider()

            // ---- The body of the shown tab ----
            val current = ui
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (current == null) {
                    LoadingIndicator(Modifier.fillMaxSize())
                } else {
                    when (val body = current.body) {
                        ReportBody.PickDates -> Message(R.string.report_pick_dates)
                        is ReportBody.Sales -> SalesBody(body, onOpenReceipt)
                        is ReportBody.Due -> DueBody(body, current.rangeReady, onOpenCustomer)
                        is ReportBody.Expenses -> ExpensesBody(body)
                        is ReportBody.Profit -> ProfitBody(body.report)
                    }
                }
            }
        }
    }

    // ---- "তারিখ বাছুন": the two-step picker shared with the Khata tab and the expense list ----
    if (rangePickerOpen) {
        DateRangePickerFlow(
            today = viewModel.today(),
            initialFrom = controls.customFrom,
            initialTo = controls.customTo,
            onConfirm = { from, to ->
                viewModel.onCustomRange(from, to)
                rangePickerOpen = false
            },
            onDismiss = { rangePickerOpen = false }
        )
    }
}

private fun ReportTab.labelRes(): Int = when (this) {
    ReportTab.SALES -> R.string.report_tab_sales
    ReportTab.DUE -> R.string.report_tab_due
    ReportTab.EXPENSES -> R.string.report_tab_expenses
    ReportTab.PROFIT -> R.string.report_tab_profit
}

@Composable
private fun Message(resId: Int) {
    EmptyState(icon = R.drawable.ic_report, text = stringResource(resId))
}

@Composable
private fun SectionTitle(text: String) {
    SectionHeader(text = text, modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.s))
}

// =========================================================================================
// বিক্রি
// =========================================================================================

/** The sales box (count, net sales, delivery, total, paid, due), the cancelled note, and the period's sales by day. */
@Composable
private fun SalesBody(body: ReportBody.Sales, onOpenReceipt: (Long) -> Unit) {
    val monthNames = stringArrayResource(R.array.months_bn).toList()
    val walkIn = stringResource(R.string.walk_in_customer)
    val periods = mapOf(
        DayPeriod.MORNING to stringResource(R.string.period_morning),
        DayPeriod.NOON to stringResource(R.string.period_noon),
        DayPeriod.AFTERNOON to stringResource(R.string.period_afternoon),
        DayPeriod.EVENING to stringResource(R.string.period_evening),
        DayPeriod.NIGHT to stringResource(R.string.period_night)
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "box") { SalesBox(body.report) }

        item(key = "title") { SectionTitle(stringResource(R.string.report_sales_list_title)) }
        if (salesEmpty(body.report)) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.report_empty_sales),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        groupByDhakaDay(body.rows).forEach { group ->
            item(key = "day-${group.date.toEpochDay()}") {
                Text(
                    text = dayHeaderText(group.date, monthNames),
                    modifier = Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.l, top = Spacing.l, bottom = Spacing.s),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            group.rows.forEach { row ->
                item(key = "sale-${row.id}") {
                    SaleRow(row, saleTimeText(row.saleDate) { periods.getValue(it) }, customerLabel(row.customerName, walkIn)) { onOpenReceipt(row.id) }
                }
            }
        }
    }
}

@Composable
private fun SalesBox(report: SalesReport) {
    val taka = stringResource(R.string.unit_taka)
    AppCard(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SalesLine.entries.forEach { line ->
                if (line == SalesLine.TOTAL) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                val amount = salesLineAmount(report, line)
                val bold = isBoldSalesLine(line)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(line.labelRes()),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = if (isMoneyLine(line)) "$taka ${formatTaka(amount)}" else stringResource(R.string.report_sales_count_value, toBengaliDigits(amount.toString())),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            if (showsCancelledNote(report)) {
                Text(
                    text = stringResource(R.string.report_cancelled_note, toBengaliDigits(report.cancelledCount.toString())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun SalesLine.labelRes(): Int = when (this) {
    SalesLine.COUNT -> R.string.report_sales_count
    SalesLine.NET_SALES -> R.string.report_sales_net
    SalesLine.DELIVERY -> R.string.report_sales_delivery
    SalesLine.TOTAL -> R.string.report_sales_total
    SalesLine.PAID -> R.string.report_sales_paid
    SalesLine.DUE -> R.string.report_sales_due
}

/** One sale of the period: receipt number and time, the customer, the total, and a red "বাকি" tag when it left a due. */
@Composable
private fun SaleRow(row: SaleListRow, timeText: String, customer: String, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { IconTile(R.drawable.ic_receipt) },
        headlineContent = { Text(toBengaliDigits(row.receiptNo) + "  ·  " + timeText, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(customer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.price_format, formatTaka(row.total)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (showDueTag(row)) {
                    StatusPill(stringResource(R.string.sale_due_tag, formatTaka(row.dueAmount)), PillVariant.Due)
                }
            }
        }
    )
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
}

// =========================================================================================
// বাকি
// =========================================================================================

/** "এখন কত বাকি" (the whole shop now), the customers who owe, and the period's movement. */
@Composable
private fun DueBody(body: ReportBody.Due, rangeReady: Boolean, onOpenCustomer: (Long) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "now-title") { SectionTitle(stringResource(R.string.report_due_now_title)) }
        item(key = "now") { AllSummaryCard(body.now) }

        item(key = "owing-title") { SectionTitle(stringResource(R.string.report_due_owing_title)) }
        if (body.owing.isEmpty()) {
            item(key = "owing-empty") {
                Text(
                    text = stringResource(R.string.report_empty_owing),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        body.owing.forEach { row ->
            item(key = "owing-${row.customer.id}") { OwingRow(row) { onOpenCustomer(row.customer.id) } }
        }

        item(key = "period-title") { SectionTitle(stringResource(R.string.report_due_period_title)) }
        item(key = "period") {
            val period = body.period
            if (showsPeriodBox(rangeReady) && period != null) {
                PeriodSummaryCard(period)
            } else {
                Text(
                    text = stringResource(R.string.report_pick_dates),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OwingRow(row: KhataCustomerRow, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { InitialAvatar(name = row.customer.name) },
        headlineContent = { Text(row.customer.name, style = MaterialTheme.typography.titleMedium) },
        supportingContent = row.customer.phone?.let { phone -> { Text(toBengaliDigits(phone), style = MaterialTheme.typography.bodyMedium) } },
        trailingContent = { BalanceText(balance = row.balance, style = MaterialTheme.typography.titleMedium) }
    )
    HorizontalDivider()
}

// =========================================================================================
// খরচ
// =========================================================================================

/** The same summary box as the খরচ screen (দোকানের খরচ, ব্যক্তিগত, মোট, each category). */
@Composable
private fun ExpensesBody(body: ReportBody.Expenses) {
    val taka = stringResource(R.string.unit_taka)
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "summary") {
            Column(modifier = Modifier.padding(top = 12.dp)) { SummaryCard(body.summary, taka) }
        }
        if (expensesEmpty(body.count)) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.report_empty_expenses),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// =========================================================================================
// লাভ-ক্ষতি
// =========================================================================================

/**
 * নিট বিক্রি − পণ্যের ক্রয়মূল্য = মোট লাভ; − দোকানের খরচ = নিট লাভ (or নিট ক্ষতি, in red). Under the
 * box: the delivery charge and the personal expenses (information only), and the "আনুমানিক" note.
 */
@Composable
private fun ProfitBody(report: ProfitReport) {
    val taka = stringResource(R.string.unit_taka)
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "box") {
            val allLines = profitLines(report)
            Column(
                modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                // The steps up to the gross profit and the shop expenses.
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        allLines.filter { it.kind != ProfitLineKind.NET_PROFIT }.forEach { line ->
                            val bold = isBoldProfitLine(line)
                            val color = if (isRedProfitLine(line)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            val label = when (line.kind) {
                                ProfitLineKind.NET_SALES -> R.string.report_profit_net_sales
                                ProfitLineKind.COST_OF_GOODS -> R.string.report_profit_cost
                                ProfitLineKind.GROSS_PROFIT ->
                                    if (line.amount < 0) R.string.report_profit_gross_loss else R.string.report_profit_gross
                                else -> R.string.report_profit_shop_expenses
                            }
                            if (line.kind == ProfitLineKind.GROSS_PROFIT) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    stringResource(label),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                                    color = color
                                )
                                Text(
                                    profitAmountText(line, taka, formatTaka(abs(line.amount))),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                                    color = color
                                )
                            }
                        }
                    }
                }

                // The result: a hero-style card, green for a profit and red for a loss.
                allLines.firstOrNull { it.kind == ProfitLineKind.NET_PROFIT }?.let { line ->
                    val loss = netResultWord(report) == ResultWord.LOSS
                    val container = if (loss) MaterialTheme.colorScheme.error else AppTheme.colors.hero
                    val content = if (loss) MaterialTheme.colorScheme.onError else AppTheme.colors.onHero
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
                        border = null
                    ) {
                        Column(modifier = Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(
                                stringResource(if (loss) R.string.report_profit_net_loss else R.string.report_profit_net),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                profitAmountText(line, taka, formatTaka(abs(line.amount))),
                                style = MaterialTheme.typography.headlineLarge
                            )
                        }
                    }
                }
            }
        }

        // ---- Information lines: not part of the profit ----
        profitInfoLines(report).forEach { info ->
            item(key = "info-${info.kind}") {
                val text = when (info.kind) {
                    InfoLineKind.DELIVERY_CHARGE -> stringResource(R.string.report_profit_delivery_info, "$taka ${formatTaka(info.amount)}")
                    InfoLineKind.PERSONAL_EXPENSES -> stringResource(R.string.report_profit_personal_info, "$taka ${formatTaka(info.amount)}")
                }
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---- "আনুমানিক": some sold lines had no cost price ----
        approximateNote(report)?.let { note ->
            item(key = "approx") {
                InfoCard(
                    text = stringResource(
                        R.string.report_approximate,
                        toBengaliDigits(note.missingCostLines.toString()),
                        "$taka ${formatTaka(note.missingCostSales)}"
                    ),
                    modifier = Modifier.fillMaxWidth().padding(Spacing.l)
                )
            }
        }
    }
}

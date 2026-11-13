package com.example.dukanhisabnext.ui.screens

import com.example.dukanhisabnext.ui.components.LoadingIndicator
import androidx.compose.foundation.clickable
import com.example.dukanhisabnext.ui.theme.Spacing
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.dukanhisabnext.data.ShopRepository
import com.example.dukanhisabnext.ui.components.StatTile
import com.example.dukanhisabnext.ui.sales.dayHeaderText
import kotlinx.coroutines.flow.flowOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.ReportRepository
import com.example.dukanhisabnext.report.Dashboard
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.customers.BalanceStatus
import com.example.dukanhisabnext.ui.customers.color
import com.example.dukanhisabnext.ui.report.DashboardCard
import com.example.dukanhisabnext.ui.report.DashboardTarget
import com.example.dukanhisabnext.ui.report.LowStockState
import com.example.dukanhisabnext.ui.report.lowStockState
import com.example.dukanhisabnext.ui.report.showsExpenseSplit
import com.example.dukanhisabnext.ui.report.showsSalesCount
import com.example.dukanhisabnext.ui.report.targetOf
import com.example.dukanhisabnext.util.DhakaTime
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.toBengaliDigits
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.DialogProperties
import com.example.dukanhisabnext.backup.BackupPrefs
import com.example.dukanhisabnext.backup.hasShopData
import com.example.dukanhisabnext.backup.shouldShowBackupNudge
import kotlinx.coroutines.launch
import com.example.dukanhisabnext.backup.shouldRemindBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Logic of the Home dashboard: today's numbers, live. The Dhaka day is looked at again every
 * minute, so a phone left open over midnight moves on to the new day by itself.
 */
class HomeViewModel(
    reports: ReportRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val lastBackupAt: StateFlow<Long?> = MutableStateFlow(null),
    private val rowCounts: suspend () -> Map<String, Long> = { emptyMap() },
    private val prefs: BackupPrefs? = null,
    shops: ShopRepository? = null
) : ViewModel() {

    private val _showBackupNudge = MutableStateFlow(false)

    /** The one-time "ব্যাকআপ নিন" dialog (after 5 sales and no backup ever). Decided once when Home opens. */
    val showBackupNudge: StateFlow<Boolean> = _showBackupNudge

    init {
        val p = prefs
        if (p != null) {
            viewModelScope.launch {
                val sales = rowCounts()["sales"] ?: 0
                if (shouldShowBackupNudge(sales, lastBackupAt.value, p.nudgeUsed, p.nudgeShownCount)) {
                    p.nudgeShownCount += 1 // counted when shown, so a rotation never counts twice (the ViewModel stays)
                    _showBackupNudge.value = true
                }
            }
        }
    }

    /** "ব্যাকআপ নিন": the dialog never comes back. */
    fun onNudgeBackupNow() {
        prefs?.nudgeUsed = true
        _showBackupNudge.value = false
    }

    /** "পরে": closes it; it may come once more. */
    fun onNudgeLater() {
        _showBackupNudge.value = false
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    /** The shop name for the small top line (null while there is none). */
    val shopName: StateFlow<String?> = (shops?.observe() ?: flowOf(null))
        .map { profile -> profile?.name?.trim()?.ifEmpty { null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboard: StateFlow<Dashboard?> = flow {
        while (true) {
            emit(DhakaTime.today(clock()))
            delay(60_000)
        }
    }
        .distinctUntilChanged()
        .flatMapLatest { today -> reports.observeDashboard(today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The small "no backup for more than 7 days" line. It is checked again whenever the dashboard
     * changes (a sale, a customer...) and when a backup is made. Only when there is data to lose.
     */
    val showBackupReminder: StateFlow<Boolean> = combine(dashboard.filterNotNull(), lastBackupAt) { _, last -> last }
        .map { last ->
            val counts = rowCounts()
            val hasData = hasShopData(counts["sales"] ?: 0, counts["customers"] ?: 0, counts["products"] ?: 0)
            shouldRemindBackup(clock(), last, hasData)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}

/**
 * The Home screen: four cards that update live. Each opens the matching screen:
 * আজকের বিক্রি → the sales list (আজ), আজকের খরচ → the expense list (আজ), মোট পাবো → the Khata tab,
 * কম স্টক → the product list with the "কম স্টক" filter on.
 */
@Composable
fun HomeScreen(
    onOpen: (DashboardTarget) -> Unit,
    onOpenBackup: () -> Unit,
    onNewSale: () -> Unit,
    onReceiveMoney: () -> Unit,
    onNewExpense: () -> Unit,
    onNewProduct: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = appViewModel { app, _ ->
        HomeViewModel(
            app.reportRepository,
            lastBackupAt = app.backupPrefs.lastBackupAt,
            rowCounts = app.backupManager::currentCounts,
            prefs = app.backupPrefs,
            shops = app.shopRepository
        )
    }
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val showBackupReminder by viewModel.showBackupReminder.collectAsStateWithLifecycle()
    val showBackupNudge by viewModel.showBackupNudge.collectAsStateWithLifecycle()

    if (showBackupNudge) {
        AlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = viewModel::onNudgeLater,
            title = { Text(stringResource(R.string.backup_nudge_title)) },
            text = { Text(stringResource(R.string.backup_nudge_text), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onNudgeBackupNow()
                    onOpenBackup()
                }) { Text(stringResource(R.string.btn_backup_now)) }
            },
            dismissButton = { TextButton(onClick = viewModel::onNudgeLater) { Text(stringResource(R.string.btn_later)) } }
        )
    }
    val taka = stringResource(R.string.unit_taka)
    val shopName by viewModel.shopName.collectAsStateWithLifecycle()
    val monthNames = stringArrayResource(R.array.months_bn).toList()
    val todayText = remember { dayHeaderText(DhakaTime.localDate(System.currentTimeMillis()), monthNames) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m)
    ) {
        // The small top line: the shop's name (or the page name while there is none) and today's date.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = shopName ?: stringResource(R.string.home_title),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Spacing.m))
            Text(todayText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val d = dashboard
        if (d == null) {
            LoadingIndicator(Modifier.fillMaxWidth().padding(32.dp)) // still loading
            return@Column
        }

        // 1. Today's sales: the green hero card.
        HeroSalesCard(
            title = stringResource(R.string.home_card_sales),
            amount = "$taka ${formatTaka(d.todaySalesTotal)}",
            hint = if (showsSalesCount(d)) {
                stringResource(R.string.home_sales_count, toBengaliDigits(d.todaySalesCount.toString()))
            } else {
                stringResource(R.string.home_sales_none)
            },
            onClick = { onOpen(targetOf(DashboardCard.TODAY_SALES)) }
        )

        // 2. Today's expense and the total receivable: two stat tiles side by side (stacked when the
        //    phone's font is very large, so a big number never gets squeezed).
        val expenseTile: @Composable (Modifier) -> Unit = { m ->
            StatTile(
                label = stringResource(R.string.home_card_expense),
                value = "$taka ${formatTaka(d.todayExpenseTotal)}",
                icon = R.drawable.ic_expense,
                hint = if (showsExpenseSplit(d)) {
                    stringResource(
                        R.string.home_expense_split,
                        "$taka ${formatTaka(d.todayShopExpense)}",
                        "$taka ${formatTaka(d.todayPersonalExpense)}"
                    )
                } else {
                    null
                },
                onClick = { onOpen(targetOf(DashboardCard.TODAY_EXPENSE)) },
                modifier = m
            )
        }
        val receivableTile: @Composable (Modifier) -> Unit = { m ->
            StatTile(
                label = stringResource(R.string.home_card_receivable),
                value = "$taka ${formatTaka(d.totalReceivable)}",
                valueColor = BalanceStatus.OWES.color(),
                icon = R.drawable.ic_contacts,
                onClick = { onOpen(targetOf(DashboardCard.RECEIVABLE)) },
                modifier = m
            )
        }
        if (LocalDensity.current.fontScale > 1.3f) {
            expenseTile(Modifier.fillMaxWidth())
            receivableTile(Modifier.fillMaxWidth())
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                expenseTile(Modifier.weight(1f).fillMaxHeight())
                receivableTile(Modifier.weight(1f).fillMaxHeight())
            }
        }

        // 3. Low stock: amber when something needs attention, a calm green when all is well.
        val low = lowStockState(d)
        LowStockTile(
            title = stringResource(R.string.home_card_low_stock),
            value = when (low) {
                LowStockState.NEEDS_ATTENTION -> stringResource(R.string.home_low_stock_count, toBengaliDigits(d.lowStockCount.toString()))
                LowStockState.ALL_GOOD -> stringResource(R.string.home_low_stock_ok)
            },
            needsAttention = low == LowStockState.NEEDS_ATTENTION,
            onClick = { onOpen(targetOf(DashboardCard.LOW_STOCK)) }
        )

        // 4. Four quick actions. They only open screens that already exist.
        QuickActionsRow(
            onNewSale = onNewSale,
            onReceiveMoney = onReceiveMoney,
            onNewExpense = onNewExpense,
            onNewProduct = onNewProduct
        )

        // A tappable card, not a notification: no backup for more than 7 days (or ever) and there is data.
        if (showBackupReminder) {
            BackupReminderCard(text = stringResource(R.string.home_backup_reminder), onClick = onOpenBackup)
        }
    }
}

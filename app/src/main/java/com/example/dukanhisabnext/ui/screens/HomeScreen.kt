package com.example.dukanhisabnext.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.flow.SharingStarted
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
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

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
}

/**
 * The Home screen: four cards that update live. Each opens the matching screen:
 * আজকের বিক্রি → the sales list (আজ), আজকের খরচ → the expense list (আজ), মোট পাবো → the Khata tab,
 * কম স্টক → the product list with the "কম স্টক" filter on.
 */
@Composable
fun HomeScreen(
    onOpen: (DashboardTarget) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = appViewModel { app, _ -> HomeViewModel(app.reportRepository) }
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val taka = stringResource(R.string.unit_taka)

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)

        val d = dashboard
        if (d == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp)) // still loading
            return@Column
        }

        DashboardCard.entries.forEach { card ->
            when (card) {
                DashboardCard.TODAY_SALES -> StatCard(
                    title = stringResource(R.string.home_card_sales),
                    value = "$taka ${formatTaka(d.todaySalesTotal)}",
                    hint = if (showsSalesCount(d)) {
                        stringResource(R.string.home_sales_count, toBengaliDigits(d.todaySalesCount.toString()))
                    } else {
                        stringResource(R.string.home_sales_none)
                    },
                    valueColor = Color.Unspecified,
                    onClick = { onOpen(targetOf(card)) }
                )

                DashboardCard.TODAY_EXPENSE -> StatCard(
                    title = stringResource(R.string.home_card_expense),
                    value = "$taka ${formatTaka(d.todayExpenseTotal)}",
                    hint = if (showsExpenseSplit(d)) {
                        stringResource(
                            R.string.home_expense_split,
                            "$taka ${formatTaka(d.todayShopExpense)}",
                            "$taka ${formatTaka(d.todayPersonalExpense)}"
                        )
                    } else {
                        null
                    },
                    valueColor = Color.Unspecified,
                    onClick = { onOpen(targetOf(card)) }
                )

                DashboardCard.RECEIVABLE -> StatCard(
                    title = stringResource(R.string.home_card_receivable),
                    value = "$taka ${formatTaka(d.totalReceivable)}",
                    hint = null,
                    valueColor = BalanceStatus.OWES.color(),
                    onClick = { onOpen(targetOf(card)) }
                )

                DashboardCard.LOW_STOCK -> StatCard(
                    title = stringResource(R.string.home_card_low_stock),
                    value = when (lowStockState(d)) {
                        LowStockState.NEEDS_ATTENTION -> stringResource(R.string.home_low_stock_count, toBengaliDigits(d.lowStockCount.toString()))
                        LowStockState.ALL_GOOD -> stringResource(R.string.home_low_stock_ok)
                    },
                    hint = null,
                    valueColor = when (lowStockState(d)) {
                        LowStockState.NEEDS_ATTENTION -> BalanceStatus.OWES.color()
                        LowStockState.ALL_GOOD -> BalanceStatus.ADVANCE.color()
                    },
                    onClick = { onOpen(targetOf(card)) }
                )
            }
        }
    }
}

/** One dashboard card: a label, a big value and an optional small line. A tap opens the matching screen. */
@Composable
private fun StatCard(title: String, value: String, hint: String?, valueColor: Color, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = valueColor)
            hint?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

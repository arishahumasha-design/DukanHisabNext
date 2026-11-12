package com.example.dukanhisabnext.ui.expense

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.dukanhisabnext.data.dao.ExpenseRow
import com.example.dukanhisabnext.expense.ExpenseSummary
import com.example.dukanhisabnext.expense.UNDO_SECONDS
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.DateRangePickerFlow
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.components.customRangeText
import com.example.dukanhisabnext.ui.sales.dayHeaderText
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.toBengaliDigits
import kotlinx.coroutines.delay

/** The id that means "no expense was just deleted". */
const val NO_DELETED_EXPENSE = -1L

/**
 * "খরচ": the expenses of a chosen period. Chips আজ (default) / ৭ দিন / এই মাস / এই বছর / তারিখ
 * বাছুন; a summary of the whole period (দোকানের খরচ, ব্যক্তিগত, মোট, and each category); a search box
 * that filters only the rows; the rows grouped by Dhaka day; and the buttons "নতুন খরচ" and "ক্যাটাগরি".
 * [deletedId] is the expense just deleted in the form: the "ফিরিয়ে আনুন" undo bar shows for
 * [UNDO_SECONDS] seconds ([onDeletedShown] clears it).
 */
@Composable
fun ExpenseListScreen(
    onBack: () -> Unit,
    onNewExpense: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onOpenCategories: () -> Unit,
    deletedId: Long,
    onDeletedShown: () -> Unit,
    viewModel: ExpenseListViewModel = appViewModel { app, args -> ExpenseListViewModel(app.expenseRepository, args) }
) {
    EnsureDefaultCategories()

    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var rangePickerOpen by rememberSaveable { mutableStateOf(false) }

    val monthNames = stringArrayResource(R.array.months_bn).toList()
    val writtenOnTemplate = stringResource(R.string.ledger_written_on, "{date}")
    val taka = stringResource(R.string.unit_taka)

    // The undo bar goes away by itself after a few seconds.
    LaunchedEffect(deletedId) {
        if (deletedId != NO_DELETED_EXPENSE) {
            delay(UNDO_SECONDS * 1_000L)
            onDeletedShown()
        }
    }

    SubScreenScaffold(title = stringResource(R.string.title_expenses), onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            val current = ui
            val controls = current?.controls ?: ExpenseControls()

            // ---- The chips (they scroll sideways) ----
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExpensePeriod.entries.forEach { period ->
                    FilterChip(
                        selected = controls.period == period,
                        onClick = {
                            if (period == ExpensePeriod.CUSTOM) rangePickerOpen = true else viewModel.onPeriodChange(period)
                        },
                        label = {
                            val text = if (period == ExpensePeriod.CUSTOM) customRangeText(controls.customFrom, controls.customTo) else null
                            Text(text ?: stringResource(period.labelRes()), style = MaterialTheme.typography.titleSmall)
                        }
                    )
                }
            }

            if (current == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) // still loading
            } else {
                // ---- The summary of the whole period ----
                if (current.rangeReady) SummaryCard(current.summary, taka)

                // ---- Search: filters only the rows below ----
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.expense_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                            }
                        }
                    }
                )
                HorizontalDivider()

                // ---- The rows, grouped by Dhaka day ----
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val empty = expenseEmptyKind(current.rangeReady, controls.searching, current.windowCount, current.shownCount)
                    if (empty != null) {
                        val message = when (empty) {
                            ExpenseEmpty.NO_EXPENSES -> R.string.expense_empty
                            ExpenseEmpty.NO_MATCH -> R.string.expense_empty_match
                            ExpenseEmpty.PICK_DATES -> R.string.khata_empty_pick_dates
                        }
                        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            current.days.forEach { day ->
                                item(key = "day-${day.date.toEpochDay()}") {
                                    DayHeader(dayHeaderText(day.date, monthNames))
                                }
                                items(day.rows, key = { "expense-${it.expense.id}" }) { row ->
                                    ExpenseRowItem(
                                        row = row,
                                        taka = taka,
                                        writtenOn = writtenOnText(row.expense) { writtenOnTemplate.replace("{date}", it) },
                                        onClick = { onEditExpense(row.expense.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- The undo bar after a delete ----
            if (deletedId != NO_DELETED_EXPENSE) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.expense_deleted),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.inverseOnSurface
                        )
                        TextButton(onClick = {
                            viewModel.undoDelete(deletedId)
                            onDeletedShown()
                        }) {
                            Text(stringResource(R.string.expense_undo), color = MaterialTheme.colorScheme.inversePrimary)
                        }
                    }
                }
            }

            // ---- The bottom buttons ----
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onOpenCategories, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.expense_btn_categories), style = MaterialTheme.typography.titleMedium)
                    }
                    Button(onClick = onNewExpense, modifier = Modifier.weight(1.4f).heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.expense_btn_new), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    // ---- "তারিখ বাছুন": the two-step picker shared with the Khata tab ----
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

/** দোকানের খরচ, ব্যক্তিগত and মোট of the whole period, then every category (largest first, personal ones marked). */
@Composable
internal fun SummaryCard(summary: ExpenseSummary, taka: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (line in expenseSummaryLines()) {
                val (label, amount) = when (line) {
                    ExpenseSummaryLine.SHOP -> R.string.expense_sum_shop to summary.shop
                    ExpenseSummaryLine.PERSONAL -> R.string.expense_sum_personal to summary.personal
                    ExpenseSummaryLine.TOTAL -> R.string.expense_sum_total to summary.total
                }
                val bold = line == ExpenseSummaryLine.TOTAL
                if (bold) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(label),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        moneyText(taka, formatTaka(amount)),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            if (summary.byCategory.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    stringResource(R.string.expense_by_category),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                summary.byCategory.forEach { category ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(category.name, style = MaterialTheme.typography.bodyMedium)
                        if (showsPersonalMark(category.isPersonal)) PersonalTag()
                        Text(
                            stringResource(R.string.expense_count, toBengaliDigits(category.count.toString())),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(moneyText(taka, formatTaka(category.total)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalTag() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = stringResource(R.string.expense_personal_tag),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
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

/** One expense: the category (with a "ব্যক্তিগত" mark), the reason, "লেখা হয়েছে <date>" if back-dated, and the amount. Tap = edit. */
@Composable
private fun ExpenseRowItem(row: ExpenseRow, taka: String, writtenOn: String?, onClick: () -> Unit) {
    val expense = row.expense
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(row.categoryName, style = MaterialTheme.typography.titleMedium)
                if (showsPersonalMark(row.categoryIsPersonal)) PersonalTag()
            }
        },
        supportingContent = {
            Column {
                expense.reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                writtenOn?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        trailingContent = {
            Text(
                text = moneyText(taka, formatTaka(expense.amount)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    )
    HorizontalDivider()
}

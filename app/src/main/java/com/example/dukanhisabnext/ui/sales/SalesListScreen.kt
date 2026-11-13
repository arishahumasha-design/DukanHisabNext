package com.example.dukanhisabnext.ui.sales

import com.example.dukanhisabnext.ui.components.LoadingIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.example.dukanhisabnext.ui.components.AppTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.dao.SaleListRow
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.components.EmptyState
import com.example.dukanhisabnext.ui.components.IconTile
import com.example.dukanhisabnext.ui.components.PillVariant
import com.example.dukanhisabnext.ui.components.SecondaryButton
import com.example.dukanhisabnext.ui.components.StatusPill
import com.example.dukanhisabnext.ui.theme.Spacing
import androidx.compose.material3.ListItemDefaults
import com.example.dukanhisabnext.util.DayPeriod
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.toBengaliDigits

/**
 * "বিক্রির তালিকা": the saved sales, newest first, grouped by Dhaka day. Read-only: tapping a sale
 * opens its receipt ([onOpenSale] gets the sale id only, so the receipt has no change line).
 * The list is lazy, so thousands of sales stay fast.
 */
@Composable
fun SalesListScreen(
    onBack: () -> Unit,
    onOpenSale: (Long) -> Unit,
    viewModel: SalesListViewModel = appViewModel { app, _ -> SalesListViewModel(app.saleRepository) }
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    // The words from strings.xml, handed to the plain functions.
    val monthNames = stringArrayResource(R.array.months_bn).toList()
    val walkIn = stringResource(R.string.walk_in_customer)
    val summaryTemplate = stringResource(R.string.sales_summary, "{count}", "{total}")
    val excludedTemplate = stringResource(R.string.sales_summary_cancelled_note, "{count}")
    val periodLabels = mapOf(
        DayPeriod.MORNING to stringResource(R.string.period_morning),
        DayPeriod.NOON to stringResource(R.string.period_noon),
        DayPeriod.AFTERNOON to stringResource(R.string.period_afternoon),
        DayPeriod.EVENING to stringResource(R.string.period_evening),
        DayPeriod.NIGHT to stringResource(R.string.period_night)
    )

    SubScreenScaffold(title = stringResource(R.string.title_sales), onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // ---- Search ----
            AppTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_sales)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                }
            )

            // ---- Filter chips: আজ, এই মাস, সব ----
            // While searching, ALL sales are searched: the chips are greyed out and a note says so.
            // Clearing the search brings the chosen chip back.
            val searching = isSearching(query)
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterOption(SalesFilter.TODAY, R.string.filter_today, filter, !searching, viewModel)
                FilterOption(SalesFilter.THIS_MONTH, R.string.filter_month, filter, !searching, viewModel)
                FilterOption(SalesFilter.ALL, R.string.filter_all, filter, !searching, viewModel)
            }
            if (searching) {
                Text(
                    text = stringResource(R.string.search_all_note),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))

            val current = ui
            if (current == null) {
                LoadingIndicator(Modifier.fillMaxSize()) // still loading
            } else {
                // ---- Count and total of the filtered sales ----
                Text(
                    text = salesSummaryLine(
                        current.summary,
                        summaryText = { count, total -> summaryTemplate.replace("{count}", count).replace("{total}", total) },
                        excludedText = { count -> excludedTemplate.replace("{count}", count) }
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()

                val empty = current.empty
                if (empty != null) {
                    EmptyMessage(empty)
                } else {
                    // A lazy list: only the rows on the screen are made.
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(current.items, key = { it.key }) { item ->
                            when (item) {
                                is SalesListItem.Header ->
                                    DayHeader(dayHeaderText(item.date, monthNames))

                                is SalesListItem.Entry -> SaleRow(
                                    row = item.row,
                                    timeText = saleTimeText(item.row.createdAt) { periodLabels.getValue(it) },
                                    customer = customerLabel(item.row.customerName, walkIn),
                                    onClick = { onOpenSale(item.row.id) }
                                )
                            }
                        }
                        // Only the newest sales are loaded; older ones come one page at a time.
                        if (current.hasMore) {
                            item(key = "show-more") {
                                SecondaryButton(
                                    text = stringResource(R.string.sales_show_more),
                                    onClick = viewModel::onShowMore,
                                    modifier = Modifier.fillMaxWidth().padding(Spacing.l)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterOption(
    option: SalesFilter,
    label: Int,
    selected: SalesFilter,
    enabled: Boolean,
    viewModel: SalesListViewModel
) {
    FilterChip(
        selected = selected == option,
        enabled = enabled,
        onClick = { viewModel.onFilterChange(option) },
        label = { Text(stringResource(label), style = MaterialTheme.typography.titleSmall) }
    )
}

/** The day header, for example "১০ নভেম্বর ২০২৬". */
@Composable
private fun DayHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.l, top = Spacing.l, bottom = Spacing.s),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * One sale: receipt number and time, the customer (or "সাধারণ কাস্টমার"), the total, and a small
 * red "বাকি ৳ X" tag when the sale has a due. A cancelled sale gets a small grey "বাতিল" tag instead
 * (no due tag), and its total is grey and crossed out.
 */
@Composable
private fun SaleRow(row: SaleListRow, timeText: String, customer: String, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { IconTile(R.drawable.ic_receipt) },
        headlineContent = {
            Text(
                text = toBengaliDigits(row.receiptNo) + "  ·  " + timeText,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = { Text(customer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.price_format, formatTaka(row.total)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    // A cancelled sale is not money any more: grey and crossed out.
                    color = if (row.isCancelled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    textDecoration = if (row.isCancelled) TextDecoration.LineThrough else null
                )
                if (showCancelledTag(row)) {
                    StatusPill(stringResource(R.string.sale_cancelled_tag), PillVariant.Cancelled)
                }
                if (showDueTag(row)) {
                    StatusPill(stringResource(R.string.sale_due_tag, formatTaka(row.dueAmount)), PillVariant.Due)
                }
            }
        }
    )
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
}

@Composable
private fun EmptyMessage(kind: EmptyKind) {
    val message = when (kind) {
        EmptyKind.NO_SALES_AT_ALL -> R.string.empty_sales_none
        EmptyKind.NO_MATCH -> R.string.empty_sales_match
    }
    EmptyState(icon = R.drawable.ic_receipt, text = stringResource(message))
}

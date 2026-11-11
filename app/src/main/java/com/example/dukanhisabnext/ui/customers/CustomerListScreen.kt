package com.example.dukanhisabnext.ui.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.dao.CustomerWithBalance
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.toBengaliDigits

/** The খাতা tab: total receivable, search, and every customer with what they owe. */
@Composable
fun CustomerListScreen(
    onAddCustomer: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    viewModel: CustomerListViewModel = appViewModel { app, _ -> CustomerListViewModel(app.customerRepository) }
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val onlyOwing by viewModel.onlyOwing.collectAsStateWithLifecycle()
    val totalReceivable by viewModel.totalReceivable.collectAsStateWithLifecycle()

    // This tab sits inside the app frame, which already keeps clear of the system bars.
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCustomer) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_customer))
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                text = stringResource(R.string.title_khata),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )

            // Summary: total receivable (pabo), computed from the ledger
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.total_receivable),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = stringResource(R.string.price_format, formatTaka(totalReceivable)),
                        style = MaterialTheme.typography.headlineMedium,
                        color = balanceStatus(totalReceivable).color()
                    )
                }
            }

            // Search box (name or mobile)
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
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
            // Filter chips: everybody, or only the customers who owe money.
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !onlyOwing,
                    onClick = { viewModel.onOnlyOwingChange(false) },
                    label = { Text(stringResource(R.string.khata_filter_all), style = MaterialTheme.typography.titleSmall) }
                )
                FilterChip(
                    selected = onlyOwing,
                    onClick = { viewModel.onOnlyOwingChange(true) },
                    label = { Text(stringResource(R.string.khata_filter_owing), style = MaterialTheme.typography.titleSmall) }
                )
            }
            HorizontalDivider()

            // The list. null = still loading.
            val list = customers
            when {
                list == null -> Unit
                list.isEmpty() -> {
                    val message = when (customerEmptyKind(searching = query.isNotBlank(), onlyOwing = onlyOwing)) {
                        CustomerEmptyKind.NO_SEARCH_RESULT -> R.string.empty_customer_search
                        CustomerEmptyKind.NOBODY_OWES -> R.string.empty_customers_owing
                        CustomerEmptyKind.NO_CUSTOMERS -> R.string.empty_customers
                    }
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Extra space at the bottom so the round + button never hides the last row.
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(list, key = { it.customer.id }) { item ->
                        CustomerRow(item, onClick = { onOpenCustomer(item.customer.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerRow(item: CustomerWithBalance, onClick: () -> Unit) {
    val customer = item.customer
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(customer.name) },
        supportingContent = customer.phone?.let { phone -> { Text(toBengaliDigits(phone)) } },
        trailingContent = {
            BalanceText(balance = item.balance, style = MaterialTheme.typography.titleMedium)
        }
    )
    HorizontalDivider()
}

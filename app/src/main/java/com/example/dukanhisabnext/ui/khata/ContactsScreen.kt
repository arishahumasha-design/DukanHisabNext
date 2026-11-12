package com.example.dukanhisabnext.ui.khata

import com.example.dukanhisabnext.ui.components.LoadingIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.LedgerRepository
import com.example.dukanhisabnext.data.dao.KhataCustomerRow
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.customers.BalanceText
import com.example.dukanhisabnext.util.toBengaliDigits
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What the address book shows: every customer, and how many exist (for the empty message). */
data class ContactsUi(val rows: List<KhataCustomerRow>, val totalCustomers: Int)

/** "যোগাযোগ": all customers A to Z. The search text is kept in the SavedStateHandle (rotation, Back). */
class ContactsViewModel(ledger: LedgerRepository, private val state: SavedStateHandle) : ViewModel() {

    val query: StateFlow<String> = state.getStateFlow("contacts_query", "")

    /** null while loading. One query; typing only filters the loaded list. */
    val ui: StateFlow<ContactsUi?> = combine(ledger.observeKhataCustomers(), query) { all, q ->
        ContactsUi(rows = searchedCustomersAtoZ(all, q), totalCustomers = all.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(text: String) {
        state["contacts_query"] = text
    }
}

/**
 * The address book (More → যোগাযোগ): every customer (with or without due) A to Z, the balance in
 * small text, a search box, and a "নতুন কাস্টমার" button that opens the normal add form (with the
 * phonebook button). Tapping a customer opens the customer detail.
 */
@Composable
fun ContactsScreen(
    onBack: () -> Unit,
    onAddCustomer: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    viewModel: ContactsViewModel = appViewModel { app, args -> ContactsViewModel(app.ledgerRepository, args) }
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    SubScreenScaffold(
        title = stringResource(R.string.title_contacts),
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCustomer,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_customer)) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
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
            HorizontalDivider()

            val current = ui
            if (current == null) {
                LoadingIndicator(Modifier.fillMaxSize())
            } else {
                val empty = contactsEmptyKind(current.totalCustomers, current.rows.size)
                if (empty != null) {
                    val message = if (empty == KhataEmpty.NO_CUSTOMERS) R.string.empty_customers else R.string.empty_customer_search
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // Room at the bottom so the round button never hides the last row.
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(current.rows, key = { it.customer.id }) { row ->
                            ListItem(
                                modifier = Modifier.clickable { onOpenCustomer(row.customer.id) },
                                headlineContent = { Text(row.customer.name, style = MaterialTheme.typography.titleMedium) },
                                supportingContent = row.customer.phone?.let { phone ->
                                    { Text(toBengaliDigits(phone), style = MaterialTheme.typography.bodyMedium) }
                                },
                                // The balance is shown small: this is an address book, not the due list.
                                trailingContent = { BalanceText(balance = row.balance, style = MaterialTheme.typography.bodySmall) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

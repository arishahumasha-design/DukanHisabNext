package com.example.dukanhisabnext.ui.sell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.BengaliDigitsTransformation
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.components.messageRes
import com.example.dukanhisabnext.ui.components.sanitizePhoneInput
import com.example.dukanhisabnext.ui.customers.BalanceText
import com.example.dukanhisabnext.util.toBengaliDigits

/**
 * Choose the customer for the sale: search, tap a customer, or add a new one on the spot
 * (name and an optional mobile). Closes itself once a customer is chosen.
 */
@Composable
fun PickCustomerScreen(
    onBack: () -> Unit,
    viewModel: PickCustomerViewModel = appViewModel { app, _ ->
        PickCustomerViewModel(app.customerRepository, app.cartStore)
    }
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val customers by viewModel.customerList.collectAsStateWithLifecycle()
    val quick by viewModel.quick.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()

    LaunchedEffect(finished) {
        if (finished) onBack()
    }

    SubScreenScaffold(title = stringResource(R.string.title_pick_customer), onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
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

            OutlinedButton(
                onClick = viewModel::openQuickAdd,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 52.dp)
            ) {
                Text(stringResource(R.string.btn_new_customer_quick), style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()

            val list = customers
            when {
                list == null -> Unit
                list.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val message = if (query.isNotBlank()) R.string.empty_customer_search else R.string.empty_customers
                    Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(list, key = { it.customer.id }) { item ->
                        ListItem(
                            modifier = Modifier.clickable { viewModel.onPick(item.customer.id) },
                            headlineContent = { Text(item.customer.name, style = MaterialTheme.typography.titleMedium) },
                            supportingContent = item.customer.phone?.let { phone -> { Text(toBengaliDigits(phone)) } },
                            trailingContent = { BalanceText(balance = item.balance) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // ---- Pop-up: new customer (same rules as the Khata form) ----
    if (quick.open) {
        AlertDialog(
            onDismissRequest = viewModel::closeQuickAdd,
            title = { Text(stringResource(R.string.dialog_quick_customer_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quick.name,
                        onValueChange = viewModel::onQuickNameChange,
                        label = { Text(stringResource(R.string.field_customer_name)) },
                        singleLine = true,
                        isError = quick.errors.name != null,
                        supportingText = quick.errors.name?.let { { Text(stringResource(it.messageRes())) } }
                    )
                    OutlinedTextField(
                        value = quick.phone,
                        onValueChange = { viewModel.onQuickPhoneChange(sanitizePhoneInput(it)) },
                        label = { Text(stringResource(R.string.field_phone)) },
                        singleLine = true,
                        isError = quick.errors.phone != null,
                        supportingText = quick.errors.phone?.let { { Text(stringResource(it.messageRes())) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        visualTransformation = BengaliDigitsTransformation
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmQuickAdd, enabled = !quick.isSaving) {
                    Text(stringResource(R.string.btn_add))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeQuickAdd) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

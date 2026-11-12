package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.ui.components.LoadingIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.ui.components.MobileField
import com.example.dukanhisabnext.ui.components.messageRes
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.toBengaliDigits

/**
 * THE customer picker, used on both the payment screen and the "নতুন বাকি" screen.
 *
 * Nobody selected: a name box (type to search; suggestions appear), with a "person +" icon at
 * its end that opens the "কাস্টমার তালিকা" dialog, then mobile (after +88) and address boxes.
 * A customer selected: a small card with name, mobile and address and a "বদলান" button, instead
 * of editable boxes (so an existing customer cannot be edited by accident).
 *
 * [onAddCustomer] opens the add-customer form; the new customer comes back selected.
 */
@Composable
fun CustomerPickerSection(viewModel: CheckoutViewModel, onAddCustomer: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selectedCustomer.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val input = state.customer
    val chosen = selected

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // A due with no customer: say so clearly.
        state.customerNeededFor?.let { due ->
            Text(
                text = stringResource(R.string.customer_needed_for_due, formatTaka(due)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (chosen != null) {
            SelectedCustomerCard(customer = chosen, onChange = viewModel::onChangeCustomer)
        } else {
            // ---- Name: type to search; the icon opens the full list ----
            OutlinedTextField(
                value = input.name,
                onValueChange = viewModel::onCustomerNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_customer_name_search)) },
                singleLine = true,
                isError = input.errors.name != null,
                supportingText = input.errors.name?.let { { Text(stringResource(it.messageRes())) } },
                trailingIcon = {
                    IconButton(onClick = {
                        closeKeyboard(keyboard, focusManager)
                        viewModel.onOpenCustomerList()
                    }) {
                        PersonPlusIcon(contentDescription = stringResource(R.string.btn_open_customer_list))
                    }
                }
            )

            // Suggestions while typing. Tapping one selects that customer.
            if (suggestions.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    suggestions.forEachIndexed { index, customer ->
                        if (index > 0) HorizontalDivider()
                        CustomerRow(customer) {
                            closeKeyboard(keyboard, focusManager)
                            viewModel.onPickCustomer(customer)
                        }
                    }
                }
            }

            // ---- Mobile (after a fixed +88) and address, for a NEW customer ----
            MobileField(
                value = input.phone,
                onValueChange = viewModel::onCustomerPhoneChange,
                label = stringResource(R.string.field_phone),
                error = input.errors.phone,
                prefix = stringResource(R.string.phone_country_prefix)
            )
            OutlinedTextField(
                value = input.address,
                onValueChange = viewModel::onCustomerAddressChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_address)) }
            )
        }
    }

    if (state.listOpen) {
        CustomerListDialog(
            viewModel = viewModel,
            onAddCustomer = {
                viewModel.onCloseCustomerList()
                onAddCustomer()
            }
        )
    }
}

private fun closeKeyboard(keyboard: SoftwareKeyboardController?, focusManager: FocusManager) {
    keyboard?.hide()
    focusManager.clearFocus()
}

/** A "person" icon with a small "+" on it, made from two built-in icons. */
@Composable
private fun PersonPlusIcon(contentDescription: String) {
    Box(modifier = Modifier.size(28.dp)) {
        Icon(
            Icons.Filled.Person,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp).align(Alignment.BottomStart)
        )
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(14.dp).align(Alignment.TopEnd)
        )
    }
}

/** The selected customer: name, mobile, address, and "বদলান" to clear the choice. */
@Composable
private fun SelectedCustomerCard(customer: Customer, onChange: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(customer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                customer.phone?.let {
                    Text(
                        text = stringResource(R.string.phone_country_prefix) + toBengaliDigits(it),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                customer.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            OutlinedButton(onClick = onChange, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.btn_change_customer))
            }
        }
    }
}

/** One customer as a row: name and mobile. */
@Composable
private fun CustomerRow(customer: Customer, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(customer.name, style = MaterialTheme.typography.titleMedium)
        customer.phone?.let { Text(toBengaliDigits(it), style = MaterialTheme.typography.bodyMedium) }
    }
}

/**
 * "কাস্টমার তালিকা": a search box, the customers sorted by name A to Z (name and mobile), and
 * two buttons at the bottom: "বন্ধ করুন" and "কাস্টমার যুক্ত করুন". Tapping a row selects it and closes.
 */
@Composable
private fun CustomerListDialog(viewModel: CheckoutViewModel, onAddCustomer: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows by viewModel.customerRows.collectAsStateWithLifecycle()

    Dialog(
        onDismissRequest = viewModel::onCloseCustomerList,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.82f)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dialog_customer_list_title), style = MaterialTheme.typography.titleLarge)

                OutlinedTextField(
                    value = state.listQuery,
                    onValueChange = viewModel::onListQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_customers)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.listQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onListQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                            }
                        }
                    }
                )

                // The list takes the room between the search box and the buttons.
                val list = rows
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        list == null -> LoadingIndicator(Modifier.fillMaxSize())
                        list.isEmpty() -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            val message = if (state.listQuery.isNotBlank()) R.string.empty_customer_search else R.string.empty_customers
                            Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
                        }
                        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(list, key = { it.id }) { customer ->
                                CustomerRow(customer) { viewModel.onPickCustomer(customer) }
                                HorizontalDivider()
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = viewModel::onCloseCustomerList, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.btn_close_dialog))
                    }
                    Button(
                        onClick = onAddCustomer,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                    ) {
                        Text(stringResource(R.string.btn_add_customer_dialog), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

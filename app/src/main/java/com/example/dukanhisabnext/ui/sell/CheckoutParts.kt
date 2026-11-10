package com.example.dukanhisabnext.ui.sell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.dao.CustomerWithBalance
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.ui.components.BengaliDigitsTransformation
import com.example.dukanhisabnext.ui.components.messageRes
import com.example.dukanhisabnext.ui.components.sanitizePhoneInput
import com.example.dukanhisabnext.ui.customers.BalanceText
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.toBengaliDigits

// Pieces shared by the payment screen and the "নতুন বাকি" screen.

/** The bar at the top: "সর্বমোট ৳ X". */
@Composable
fun TotalBar(total: Long) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_grand_total),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.price_format, formatTaka(total)),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** A label and an on/off switch, at least 56dp high. */
@Composable
fun LabeledSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** The "মন্তব্য" box (saved as the sale's note). */
@Composable
fun NoteField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.field_sale_note)) }
    )
}

/** A spinner with "সংরক্ষণ হচ্ছে…" while a save is running. */
@Composable
fun SavingRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(stringResource(R.string.saving_sale), style = MaterialTheme.typography.titleMedium)
    }
}

/** A message for a save that did not happen. Problems in the cart offer the way back to it. */
@Composable
fun CheckoutErrorCard(error: PaymentError, onBackToCart: () -> Unit) {
    val message = when (error) {
        is PaymentError.ProductsUnavailable ->
            stringResource(R.string.error_products_unavailable, error.productNames.joinToString(", "))
        PaymentError.CustomerNotFound -> stringResource(R.string.error_customer_not_found)
        PaymentError.CustomerRequired -> stringResource(R.string.error_customer_required)
        PaymentError.SaleInvalid -> stringResource(R.string.error_sale_invalid)
        PaymentError.SaveFailed -> stringResource(R.string.error_save_failed)
    }
    val needsCartFix = error is PaymentError.ProductsUnavailable || error == PaymentError.SaleInvalid

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
            if (needsCartFix) {
                Button(onClick = onBackToCart, modifier = Modifier.heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.btn_back_to_cart))
                }
            }
        }
    }
}

/**
 * The customer boxes: name (with suggestions from the khata while typing, and a small add icon),
 * mobile (shown after "+88") and address. A chosen existing customer fills the other boxes;
 * those are then read-only (change details in the খাতা tab). [neededFor] is a due that needs a
 * customer: it is shown as a clear message at the top.
 */
@Composable
fun CustomerInfoFields(
    input: CustomerInputState,
    selected: Customer?,
    selectedBalance: Long,
    suggestions: List<CustomerWithBalance>,
    neededFor: Long?,
    viewModel: CheckoutViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (neededFor != null) {
            Text(
                text = stringResource(R.string.customer_needed_for_due, formatTaka(neededFor)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }

        // ---- Name: type to search; add icon = add as a new customer now ----
        OutlinedTextField(
            value = input.name,
            onValueChange = viewModel::onCustomerNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_customer_name)) },
            singleLine = true,
            isError = input.errors.name != null,
            supportingText = input.errors.name?.let { { Text(stringResource(it.messageRes())) } },
            trailingIcon = {
                when {
                    selected != null -> IconButton(onClick = viewModel::onClearCustomer) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.btn_clear_customer))
                    }
                    input.isCreating -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    input.name.isNotBlank() -> IconButton(onClick = viewModel::onAddCustomerNow) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.btn_add_customer_now))
                    }
                }
            }
        )

        if (selected != null) {
            // An existing customer from the khata, with what they owe before this sale.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.customer_in_khata) + " ", style = MaterialTheme.typography.bodyMedium)
                BalanceText(balance = selectedBalance, style = MaterialTheme.typography.bodyMedium)
            }
        } else if (suggestions.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                suggestions.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable { viewModel.onPickCustomer(item.customer) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.customer.name, style = MaterialTheme.typography.titleMedium)
                            item.customer.phone?.let {
                                Text(toBengaliDigits(it), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        BalanceText(balance = item.balance, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // ---- Mobile, shown after a fixed +88 (same rules as the customer form) ----
        OutlinedTextField(
            value = input.phone,
            onValueChange = { viewModel.onCustomerPhoneChange(sanitizePhoneInput(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_phone)) },
            singleLine = true,
            enabled = selected == null,
            prefix = { Text(stringResource(R.string.phone_country_prefix)) },
            isError = input.errors.phone != null,
            supportingText = input.errors.phone?.let { { Text(stringResource(it.messageRes())) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            visualTransformation = BengaliDigitsTransformation
        )

        OutlinedTextField(
            value = input.address,
            onValueChange = viewModel::onCustomerAddressChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_address)) },
            enabled = selected == null
        )
    }
}

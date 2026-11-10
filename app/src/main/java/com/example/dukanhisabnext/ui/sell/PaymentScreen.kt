package com.example.dukanhisabnext.ui.sell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.ChoiceRow
import com.example.dukanhisabnext.ui.components.NumberField
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.components.messageRes
import com.example.dukanhisabnext.ui.customers.BalanceStatus
import com.example.dukanhisabnext.ui.customers.BalanceText
import com.example.dukanhisabnext.ui.customers.color
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.toBengaliDigits

/**
 * The payment screen: the total, the amount received (starts as the whole total), the payment
 * method, the live change (ফেরত) or due (বাকি), the customer, and the Save button.
 * After a successful save it moves on to the receipt ([onSaved]).
 */
@Composable
fun PaymentScreen(
    onBack: () -> Unit,
    onPickCustomer: () -> Unit,
    onSaved: (saleId: Long, change: Long) -> Unit,
    viewModel: PaymentViewModel = appViewModel { app, _ ->
        PaymentViewModel(app.saleRepository, app.customerRepository, app.cartStore)
    }
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val customer by viewModel.customer.collectAsStateWithLifecycle()
    val customerBalance by viewModel.customerBalance.collectAsStateWithLifecycle()

    // After a successful save, move on to the receipt (the cart is already emptied).
    LaunchedEffect(form.savedSaleId) {
        form.savedSaleId?.let { onSaved(it, form.savedChange) }
    }

    SubScreenScaffold(title = stringResource(R.string.title_payment), onBack = onBack) { padding ->
        // A saved sale: show nothing while the screen is replaced by the receipt.
        if (form.savedSaleId != null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize())
            return@SubScreenScaffold
        }

        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!summary.canContinue) {
                    // Should not happen (the cart screen only continues with a valid cart).
                    Text(stringResource(R.string.payment_cart_invalid), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.btn_back_to_cart))
                    }
                    return@Column
                }

                // ---- Total ----
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.label_grand_total), style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = stringResource(R.string.price_format, formatTaka(view.total)),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // ---- Amount received, with the "পুরো টাকা" reset button ----
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    NumberField(
                        label = stringResource(R.string.field_received),
                        value = form.receivedText,
                        onValueChange = viewModel::onReceivedChange,
                        modifier = Modifier.weight(1f),
                        error = view.receivedError,
                        prefix = stringResource(R.string.unit_taka)
                    )
                    OutlinedButton(
                        onClick = viewModel::onFullAmount,
                        modifier = Modifier.padding(top = 4.dp).heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.btn_full_amount))
                    }
                }

                // ---- Live result: change to give back, or the due ----
                ResultCard(view)

                // ---- Payment method (only matters when something is paid) ----
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.label_payment_method), style = MaterialTheme.typography.titleMedium)
                    ChoiceRow(
                        options = listOf(
                            PaymentMethod.CASH to stringResource(R.string.method_cash),
                            PaymentMethod.BKASH to stringResource(R.string.method_bkash),
                            PaymentMethod.NAGAD to stringResource(R.string.method_nagad)
                        ),
                        selected = form.method,
                        onSelect = viewModel::onMethodChange,
                        enabled = view.paid > 0
                    )
                }

                // ---- Customer (needed when there is a due) ----
                CustomerCard(
                    customer = customer,
                    balance = customerBalance,
                    needed = view.needsCustomer,
                    triedToSave = form.triedToSave,
                    onPick = onPickCustomer,
                    onRemove = viewModel::onRemoveCustomer
                )

                // ---- A problem with the last save attempt ----
                form.error?.let { error -> ErrorCard(error, onBackToCart = onBack) }
            }

            SaveBar(
                enabled = view.readyToSave && !form.isSaving,
                saving = form.isSaving,
                onSave = viewModel::save
            )
        }
    }
}

// =========================================================================================

/** "ফেরত দিন: ৳ ৩০০" or "বাকি: ৳ ১০০" or "ঠিক আছে", updating while you type. */
@Composable
private fun ResultCard(view: PaymentView) {
    if (!view.readyToSave) return

    val (text, color) = when {
        view.change > 0 ->
            stringResource(R.string.result_change, formatTaka(view.change)) to MaterialTheme.colorScheme.primary
        view.due > 0 ->
            stringResource(R.string.result_due, formatTaka(view.due)) to BalanceStatus.OWES.color()
        else ->
            stringResource(R.string.result_exact) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun CustomerCard(
    customer: Customer?,
    balance: Long,
    needed: Boolean,
    triedToSave: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.label_customer), style = MaterialTheme.typography.labelLarge)

            if (customer == null) {
                OutlinedButton(
                    onClick = onPick,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Text(stringResource(R.string.btn_pick_customer), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Text(customer.name, style = MaterialTheme.typography.titleMedium)
                customer.phone?.let { Text(toBengaliDigits(it), style = MaterialTheme.typography.bodyMedium) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.label_balance) + ": ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    BalanceText(balance = balance, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPick, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.btn_change_customer))
                    }
                    TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.btn_remove_customer))
                    }
                }
            }

            // There is a due and nobody to put it on: say so. It turns red after Save is pressed.
            if (needed) {
                Text(
                    text = stringResource(R.string.customer_needed),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (triedToSave) MaterialTheme.colorScheme.error else BalanceStatus.OWES.color()
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(error: PaymentError, onBackToCart: () -> Unit) {
    val message = when (error) {
        is PaymentError.ProductsUnavailable ->
            stringResource(R.string.error_products_unavailable, error.productNames.joinToString("، "))
        PaymentError.CustomerNotFound -> stringResource(R.string.error_customer_not_found)
        PaymentError.CustomerRequired -> stringResource(R.string.error_customer_required)
        PaymentError.SaleInvalid -> stringResource(R.string.error_sale_invalid)
        PaymentError.SaveFailed -> stringResource(R.string.error_save_failed)
    }
    // For these the cart has to be fixed, so offer the way back.
    val needsCartFix = error is PaymentError.ProductsUnavailable || error == PaymentError.SaleInvalid

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            if (needsCartFix) {
                Button(onClick = onBackToCart, modifier = Modifier.heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.btn_back_to_cart))
                }
            }
        }
    }
}

/** The big Save button. While saving it shows a spinner and cannot be pressed again. */
@Composable
private fun SaveBar(enabled: Boolean, saving: Boolean, onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).heightIn(min = 56.dp)
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = stringResource(R.string.saving_sale),
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                Text(stringResource(R.string.btn_save_sale), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

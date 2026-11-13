package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.ui.theme.AppCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.example.dukanhisabnext.ui.components.AppAlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.ui.components.PrimaryButton
import com.example.dukanhisabnext.ui.components.InfoCard
import com.example.dukanhisabnext.ui.components.DialogTextButton
import com.example.dukanhisabnext.ui.components.SecondaryButton
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.NumberField
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.customers.BalanceStatus
import com.example.dukanhisabnext.ui.customers.color
import com.example.dukanhisabnext.util.formatTaka

/**
 * "পেমেন্ট কনফার্ম করুন": the total, "পেয়েছি" (starts as the total) with the live change, three big
 * buttons (নগদ টাকা saves at once, বাকি opens the নতুন বাকি screen, বিকাশ / নগদ asks which and saves),
 * the note, and the optional customer boxes.
 */
@Composable
fun PaymentScreen(
    onBack: () -> Unit,
    onOpenDue: () -> Unit,
    onAddCustomer: () -> Unit,
    onSaved: (saleId: Long, change: Long) -> Unit,
    viewModel: CheckoutViewModel = appViewModel { app, _ ->
        CheckoutViewModel(app.saleRepository, app.customerRepository, app.cartStore)
    }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    // Saved from THIS screen: move on to the receipt.
    LaunchedEffect(state.savedSaleId) {
        val id = state.savedSaleId
        if (id != null && state.savedFrom == CheckoutScreen.PAYMENT) onSaved(id, state.savedChange)
    }
    // A due needed a customer: scroll down to the customer boxes, where the message is.
    LaunchedEffect(state.customerNeededFor) {
        if (state.customerNeededFor != null) scroll.animateScrollTo(scroll.maxValue)
    }

    SubScreenScaffold(title = stringResource(R.string.title_payment), onBack = onBack) { padding ->
        if (state.savedSaleId != null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize()) // moving on to the receipt
            return@SubScreenScaffold
        }

        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
            TotalBar(total = summary.total)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!summary.canContinue) {
                    Text(stringResource(R.string.payment_cart_invalid), style = MaterialTheme.typography.bodyLarge)
                    SecondaryButton(text = stringResource(R.string.btn_back_to_cart), onClick = onBack)
                    return@Column
                }

                val busy = state.isSaving

                // ---- Version 5: a back-dated sale says its date here, before saving ----
                val backDatedDay by viewModel.backDatedDay.collectAsStateWithLifecycle()
                backDatedDay?.let { day ->
                    val monthNames = androidx.compose.ui.res.stringArrayResource(R.array.months_bn).toList()
                    InfoCard(
                        text = stringResource(R.string.payment_sale_date, com.example.dukanhisabnext.ui.sales.dayHeaderText(day, monthNames)),
                        variant = com.example.dukanhisabnext.ui.components.PillVariant.Due,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ---- পেয়েছি, with the live change / due line ----
                NumberField(
                    label = stringResource(R.string.field_received),
                    value = state.receivedText,
                    onValueChange = viewModel::onReceivedChange,
                    error = view.receivedError,
                    prefix = stringResource(R.string.unit_taka)
                )
                if (view.readyToSave && view.change > 0) {
                    Text(
                        text = stringResource(R.string.change_line, formatTaka(view.change)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (view.readyToSave && view.due > 0) {
                    Text(
                        text = stringResource(R.string.due_preview_line, formatTaka(view.due)),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = BalanceStatus.OWES.color()
                    )
                }

                // ---- The three big buttons ----
                // No icon here: a check mark would look like a "selected" state, but this button saves at once.
                BigPayButton(
                    label = stringResource(R.string.btn_pay_cash),
                    icon = null,
                    enabled = !busy && view.readyToSave,
                    primary = true,
                    onClick = viewModel::onCashTap
                )
                BigPayButton(
                    label = stringResource(R.string.btn_pay_due),
                    icon = R.drawable.ic_contacts,
                    enabled = !busy && summary.total > 0, // a due needs something to owe
                    onClick = {
                        viewModel.prepareDue()
                        onOpenDue()
                    }
                )
                BigPayButton(
                    label = stringResource(R.string.btn_pay_mobile),
                    icon = R.drawable.ic_phone,
                    enabled = !busy && view.readyToSave,
                    onClick = viewModel::onMobileTap
                )
                if (state.isSaving) SavingRow()

                state.error?.let { CheckoutErrorCard(it, onBackToCart = onBack) }

                // ---- মন্তব্য ----
                NoteField(value = state.note, onValueChange = viewModel::onNoteChange)

                // ---- কাস্টমার তথ্য ----
                LabeledSwitch(
                    label = stringResource(R.string.switch_customer_info),
                    checked = state.customer.on,
                    onCheckedChange = viewModel::onCustomerSwitch
                )
                if (state.customer.on) {
                    CustomerPickerSection(viewModel = viewModel, onAddCustomer = onAddCustomer)
                } else {
                    // Switched off = no customer for this sale. A due still needs one, so say so
                    // and offer to switch it on again.
                    state.customerNeededFor?.let { due ->
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.customer_off_needed, formatTaka(due)),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                PrimaryButton(
                                    text = stringResource(R.string.btn_turn_on_customer),
                                    onClick = { viewModel.onCustomerSwitch(true) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- The small mobile-money choice ----
    if (state.mobileChoiceOpen) {
        AppAlertDialog(
            onDismissRequest = viewModel::onMobileDismiss,
            title = { Text(stringResource(R.string.dialog_mobile_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton(
                        text = stringResource(R.string.method_bkash),
                        onClick = { viewModel.onMobileChosen(PaymentMethod.BKASH) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    PrimaryButton(
                        text = stringResource(R.string.method_nagad_full),
                        onClick = { viewModel.onMobileChosen(PaymentMethod.NAGAD) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                DialogTextButton(stringResource(R.string.btn_cancel), viewModel::onMobileDismiss)
            }
        )
    }
}

/** One of the three payment buttons: full width, at least 64dp high, a built-in icon and text. */
@Composable
private fun BigPayButton(
    label: String,
    @androidx.annotation.DrawableRes icon: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    if (primary) {
        PrimaryButton(text = label, onClick = onClick, enabled = enabled, modifier = modifier, icon = icon)
    } else {
        SecondaryButton(text = label, onClick = onClick, enabled = enabled, modifier = modifier, icon = icon)
    }
}

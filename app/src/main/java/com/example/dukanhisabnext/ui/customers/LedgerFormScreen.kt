package com.example.dukanhisabnext.ui.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.example.dukanhisabnext.ui.components.AppTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.ledger.FormField
import com.example.dukanhisabnext.ledger.LEDGER_METHODS
import com.example.dukanhisabnext.ledger.LedgerAction
import com.example.dukanhisabnext.ledger.MAX_LEDGER_NOTE_LENGTH
import com.example.dukanhisabnext.ledger.dateToPickerMillis
import com.example.dukanhisabnext.ledger.dayText
import com.example.dukanhisabnext.ledger.errorsFor
import com.example.dukanhisabnext.ledger.formPreview
import com.example.dukanhisabnext.ledger.isCorrection
import com.example.dukanhisabnext.ledger.isSelectableDay
import com.example.dukanhisabnext.ledger.maxAdvanceReturn
import com.example.dukanhisabnext.ledger.noteLength
import com.example.dukanhisabnext.ledger.pickerMillisToDate
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.BengaliDigitsTransformation
import com.example.dukanhisabnext.ui.components.DayField
import com.example.dukanhisabnext.ui.components.DropdownField
import com.example.dukanhisabnext.ui.components.ConfirmAmountDialog
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.components.FormCard
import com.example.dukanhisabnext.ui.components.InfoCard
import com.example.dukanhisabnext.ui.components.PillVariant
import com.example.dukanhisabnext.ui.components.SaveBar
import com.example.dukanhisabnext.ui.components.SegmentedControl
import com.example.dukanhisabnext.ui.components.needsMoneyConfirm
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.parseTakaToPaisa
import com.example.dukanhisabnext.ui.components.LEDGER_CONFIRM_PAISA
import com.example.dukanhisabnext.ui.components.sanitizeNumberInput
import com.example.dukanhisabnext.util.formatTaka
import com.example.dukanhisabnext.util.toBengaliDigits

/**
 * The ONE entry form for টাকা পেলাম, বাকি দিলাম, পুরনো বাকি, সংশোধন and অগ্রিম ফেরত দিলাম:
 * amount, date (today by default, never a future day), the method where money changes hands, and a
 * note (required for a correction). It shows the balance after saving, and a warning when the
 * entry makes an advance. [onSaved] goes back to the customer, which shows a short saved message.
 */
@Composable
fun LedgerFormScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: LedgerFormViewModel = appViewModel { app, args ->
        LedgerFormViewModel(app.ledgerRepository, app.customerRepository, args)
    }
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val customer by viewModel.customer.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val methods = methodLabels()
    val form = ui.form

    LaunchedEffect(ui.saved) {
        if (ui.saved) onSaved()
    }

    // A big amount (৳10,000 or more) asks "ঠিক আছে?" before saving (ConfirmRules.kt).
    var confirmOpen by rememberSaveable { mutableStateOf(false) }
    val amountPaisa = parseTakaToPaisa(form.amountText)

    SubScreenScaffold(title = stringResource(form.action.titleRes()), onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ---- Who, and how much they owe now ----
                FormCard {
                customer?.let { Text(it.name, style = MaterialTheme.typography.titleLarge) }
                balance?.let { current ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ledger_current_balance), style = MaterialTheme.typography.bodyLarge)
                        BalanceText(balance = current, style = MaterialTheme.typography.titleMedium)
                    }
                }
                }

                // ---- A correction is plus or minus ----
                if (isCorrection(form.action)) {
                    SegmentedControl(
                        labels = listOf(stringResource(R.string.ledger_correction_plus), stringResource(R.string.ledger_correction_minus)),
                        selectedIndex = if (form.action == LedgerAction.CORRECTION_PLUS) 0 else 1,
                        onSelect = { viewModel.onCorrectionSign(it == 0) },
                        enabled = !ui.saving
                    )
                }

                // ---- Amount ----
                FormCard {
                val amountError = errorsFor(FormField.AMOUNT, ui.errors).firstOrNull()
                val maxAdvance = balance?.let { maxAdvanceReturn(it, form.action) }
                AppTextField(
                    value = form.amountText,
                    onValueChange = { viewModel.onAmountChange(sanitizeNumberInput(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ledger_amount_label)) },
                    prefix = { Text(stringResource(R.string.unit_taka)) },
                    enabled = !ui.saving,
                    singleLine = true,
                    isError = amountError != null,
                    supportingText = when {
                        amountError != null -> ({ Text(stringResource(amountError.messageRes())) })
                        maxAdvance != null -> ({
                            Text(stringResource(R.string.ledger_advance_max, stringResource(R.string.price_format, formatTaka(maxAdvance))))
                        })
                        else -> null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    visualTransformation = BengaliDigitsTransformation
                )

                // ---- What the balance becomes, and the advance warning ----
                balance?.let { current ->
                    formPreview(current, form)?.let { preview ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.ledger_after_balance), style = MaterialTheme.typography.bodyLarge)
                            BalanceText(balance = preview.balanceAfter, style = MaterialTheme.typography.titleMedium)
                        }
                        preview.advanceCreated?.let { advance ->
                            InfoCard(
                                text = stringResource(
                                    R.string.ledger_advance_warning,
                                    stringResource(R.string.price_format, formatTaka(advance))
                                ),
                                variant = PillVariant.Cancelled
                            )
                        }
                    }
                }

                // ---- Date ----
                DayField(
                    date = form.date,
                    today = viewModel.today,
                    enabled = !ui.saving,
                    errorText = errorsFor(FormField.DATE, ui.errors).firstOrNull()?.let { stringResource(it.messageRes()) },
                    onDateChange = viewModel::onDateChange
                )

                // ---- Method (only where money changes hands) ----
                if (form.action.needsMethod) {
                    DropdownField(
                        label = stringResource(R.string.ledger_method_label),
                        selectedText = form.method?.let { methods.getValue(it) }.orEmpty(),
                        enabled = !ui.saving
                    ) { close ->
                        LEDGER_METHODS.forEach { method ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(methods.getValue(method)) },
                                onClick = {
                                    viewModel.onMethodChange(method)
                                    close()
                                }
                            )
                        }
                    }
                    errorsFor(FormField.METHOD, ui.errors).firstOrNull()?.let {
                        Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error)
                    }
                }

                // ---- Note ----
                val noteError = errorsFor(FormField.NOTE, ui.errors).firstOrNull()
                AppTextField(
                    value = form.note,
                    onValueChange = viewModel::onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            stringResource(
                                if (form.action.needsNote) R.string.ledger_note_label_required else R.string.ledger_note_label
                            )
                        )
                    },
                    enabled = !ui.saving,
                    minLines = 2,
                    maxLines = 4,
                    isError = noteError != null,
                    supportingText = {
                        Column {
                            noteError?.let { Text(stringResource(it.messageRes())) }
                            Text(
                                stringResource(
                                    R.string.ledger_note_counter,
                                    toBengaliDigits(noteLength(form.note).toString()),
                                    toBengaliDigits(MAX_LEDGER_NOTE_LENGTH.toString())
                                )
                            )
                        }
                    }
                )

                }

                // ---- Problems that are not about one box ----
                errorsFor(FormField.GENERAL, ui.errors).forEach {
                    Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                }
                ui.problem?.let {
                    Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                }
            }

            // ---- The save button: disabled while saving, so a double tap cannot save twice ----
            SaveBar(
                label = stringResource(if (ui.saving) R.string.ledger_saving else R.string.btn_save),
                enabled = !ui.saving && !ui.saved,
                onClick = { if (needsMoneyConfirm(amountPaisa, LEDGER_CONFIRM_PAISA)) confirmOpen = true else viewModel.onSave() }
            )
        }
    }

    if (confirmOpen) {
        ConfirmAmountDialog(
            amountText = stringResource(R.string.price_format, formatTaka(amountPaisa ?: 0L)),
            onFix = { confirmOpen = false },
            onConfirm = {
                confirmOpen = false
                viewModel.onSave()
            }
        )
    }
}

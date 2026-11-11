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
import androidx.compose.material3.OutlinedTextField
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
import com.example.dukanhisabnext.ui.components.DropdownField
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
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
                customer?.let { Text(it.name, style = MaterialTheme.typography.titleLarge) }
                balance?.let { current ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ledger_current_balance), style = MaterialTheme.typography.bodyLarge)
                        BalanceText(balance = current, style = MaterialTheme.typography.titleMedium)
                    }
                }

                // ---- A correction is plus or minus ----
                if (isCorrection(form.action)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = form.action == LedgerAction.CORRECTION_PLUS,
                            onClick = { viewModel.onCorrectionSign(true) },
                            enabled = !ui.saving,
                            label = { Text(stringResource(R.string.ledger_correction_plus)) }
                        )
                        FilterChip(
                            selected = form.action == LedgerAction.CORRECTION_MINUS,
                            onClick = { viewModel.onCorrectionSign(false) },
                            enabled = !ui.saving,
                            label = { Text(stringResource(R.string.ledger_correction_minus)) }
                        )
                    }
                }

                // ---- Amount ----
                val amountError = errorsFor(FormField.AMOUNT, ui.errors).firstOrNull()
                val maxAdvance = balance?.let { maxAdvanceReturn(it, form.action) }
                OutlinedTextField(
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
                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                                Text(
                                    text = stringResource(
                                        R.string.ledger_advance_warning,
                                        stringResource(R.string.price_format, formatTaka(advance))
                                    ),
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // ---- Date ----
                DateBox(
                    date = form.date,
                    today = viewModel.today,
                    enabled = !ui.saving,
                    error = errorsFor(FormField.DATE, ui.errors).firstOrNull(),
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
                OutlinedTextField(
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

                // ---- Problems that are not about one box ----
                errorsFor(FormField.GENERAL, ui.errors).forEach {
                    Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                }
                ui.problem?.let {
                    Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                }
            }

            // ---- The save button: disabled while saving, so a double tap cannot save twice ----
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = viewModel::onSave,
                    enabled = !ui.saving && !ui.saved,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().heightIn(min = 56.dp)
                ) {
                    Text(
                        stringResource(if (ui.saving) R.string.ledger_saving else R.string.btn_save),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/** The date box: shows the day (Bengali digits); tapping it opens a calendar that offers no future day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateBox(
    date: java.time.LocalDate,
    today: java.time.LocalDate,
    enabled: Boolean,
    error: com.example.dukanhisabnext.ledger.LedgerError?,
    onDateChange: (java.time.LocalDate) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }

    Column {
        Text(stringResource(R.string.ledger_date_label), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) {
            Text(dayText(date), style = MaterialTheme.typography.titleMedium)
        }
        error?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
    }

    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateToPickerMillis(date),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = isSelectableDay(utcTimeMillis, today)
                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            }
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(pickerMillisToDate(it)) }
                    open = false
                }) { Text(stringResource(R.string.ledger_date_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

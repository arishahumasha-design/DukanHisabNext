package com.example.dukanhisabnext.ui.sell

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.ui.components.PrimaryButton
import com.example.dukanhisabnext.ui.components.NumberField
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.util.formatTaka

/**
 * "নতুন বাকি": how much stays as a due (starts as total − received, or the whole total), the
 * customer (required), a note, and a confirm button that saves the sale with that due.
 * [viewModel] is the SAME one as the payment screen's, so the customer and note carry over.
 */
@Composable
fun DueScreen(
    onBack: () -> Unit,
    onAddCustomer: () -> Unit,
    onSaved: (saleId: Long, change: Long) -> Unit,
    viewModel: CheckoutViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val due by viewModel.due.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedSaleId) {
        val id = state.savedSaleId
        if (id != null && state.savedFrom == CheckoutScreen.DUE) onSaved(id, state.savedChange)
    }

    SubScreenScaffold(title = stringResource(R.string.title_due), onBack = onBack) { padding ->
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ---- বাকির পরিমাণ (more than 0, not more than the total) ----
                NumberField(
                    label = stringResource(R.string.field_due_amount),
                    value = state.dueText,
                    onValueChange = viewModel::onDueChange,
                    error = due.error,
                    prefix = stringResource(R.string.unit_taka)
                )
                due.paid?.let { paid ->
                    Text(
                        text = stringResource(R.string.paid_line, formatTaka(paid)),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // ---- Customer: required here ----
                Text(stringResource(R.string.label_customer_required), style = MaterialTheme.typography.titleMedium)
                CustomerPickerSection(viewModel = viewModel, onAddCustomer = onAddCustomer)

                NoteField(value = state.note, onValueChange = viewModel::onNoteChange)

                if (state.isSaving) SavingRow()
                state.error?.let { CheckoutErrorCard(it, onBackToCart = onBack) }
            }

            // ---- Confirm: saves at once; disabled while saving ----
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                PrimaryButton(
                    text = stringResource(R.string.btn_confirm_due),
                    onClick = viewModel::confirmDue,
                    enabled = !state.isSaving && due.error == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .heightIn(min = 64.dp)
                )
            }
        }
    }
}

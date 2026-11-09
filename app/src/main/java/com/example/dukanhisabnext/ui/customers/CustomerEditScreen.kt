package com.example.dukanhisabnext.ui.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

/** Add a new customer, or edit one (when the route carries a customer id). */
@Composable
fun CustomerEditScreen(
    onBack: () -> Unit,
    viewModel: CustomerEditViewModel = appViewModel { app, args -> CustomerEditViewModel(app.customerRepository, args) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // After a successful save the ViewModel sets finished = true: close the screen.
    LaunchedEffect(state.finished) {
        if (state.finished) onBack()
    }

    SubScreenScaffold(
        title = stringResource(if (state.isNew) R.string.title_customer_new else R.string.title_customer_edit),
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding() // lift the form above the keyboard
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_customer_name)) },
                singleLine = true,
                isError = state.errors.name != null,
                supportingText = state.errors.name?.let { { Text(stringResource(it.messageRes())) } }
            )

            OutlinedTextField(
                value = state.phone,
                // Bengali digits typed are converted to normal digits; they are shown as Bengali again.
                onValueChange = { viewModel.onPhoneChange(sanitizePhoneInput(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_phone)) },
                singleLine = true,
                isError = state.errors.phone != null,
                supportingText = {
                    val error = state.errors.phone
                    when {
                        error != null -> Text(stringResource(error.messageRes()))
                        state.phoneWarning -> Text(stringResource(R.string.phone_duplicate_warning))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                visualTransformation = BengaliDigitsTransformation
            )

            OutlinedTextField(
                value = state.address,
                onValueChange = viewModel::onAddressChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_address)) }
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_note)) }
            )

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving && !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_save))
            }
        }
    }
}

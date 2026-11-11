package com.example.dukanhisabnext.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.MobileField
import com.example.dukanhisabnext.ui.components.SubScreenScaffold

/**
 * The small owner form: owner name and mobile (both optional), a save button and a clear saved
 * message. It saves into the same ShopProfile row as "দোকানের তথ্য".
 */
@Composable
fun OwnerScreen(
    onBack: () -> Unit,
    viewModel: OwnerViewModel = appViewModel { app, _ -> OwnerViewModel(app.shopRepository) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SubScreenScaffold(title = stringResource(R.string.title_owner), onBack = onBack) { padding ->
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
                value = state.ownerName,
                onValueChange = viewModel::onOwnerNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_owner_name)) },
                singleLine = true
            )

            // The shared mobile box: it closes the keyboard once the number is complete. Optional.
            MobileField(
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                label = stringResource(R.string.field_phone),
                error = state.errors.phone,
                prefix = stringResource(R.string.phone_country_prefix)
            )

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving && !state.isLoading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.btn_save), style = MaterialTheme.typography.titleMedium)
            }

            // A clear "saved" message. It goes away as soon as something is edited again.
            if (state.saved) SavedMessage(stringResource(R.string.owner_saved))
        }
    }
}

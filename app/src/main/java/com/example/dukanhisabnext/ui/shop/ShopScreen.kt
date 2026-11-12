package com.example.dukanhisabnext.ui.shop

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.ui.appViewModel
import com.example.dukanhisabnext.ui.components.MobileField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.dukanhisabnext.ui.components.FormWithSaveBar
import com.example.dukanhisabnext.ui.components.SaveBar
import com.example.dukanhisabnext.ui.components.SubScreenScaffold
import com.example.dukanhisabnext.ui.components.messageRes

/**
 * "দোকানের তথ্য": the shop's own details, printed at the top of every receipt.
 * Only the shop name is required; the mobile is optional (and checked if typed).
 */
@Composable
fun ShopScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit = {},
    viewModel: ShopViewModel = appViewModel { app, _ -> ShopViewModel(app.shopRepository) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // After a successful save the app goes back to where it came from (Settings), which then
    // shows the "saved" message and the updated shop button.
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    SubScreenScaffold(title = stringResource(R.string.title_shop), onBack = onBack) { padding ->
        // The Save button is pinned above the keyboard (FormWithSaveBar); the fields scroll.
        FormWithSaveBar(
            padding = padding,
            saveBar = { SaveBar(stringResource(R.string.btn_save), !state.isSaving && !state.isLoading, viewModel::save) }
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_shop_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                isError = state.errors.name != null,
                supportingText = state.errors.name?.let { { Text(stringResource(it.messageRes())) } }
            )

            OutlinedTextField(
                value = state.ownerName,
                onValueChange = viewModel::onOwnerChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_owner_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )

            // The same shared mobile box as everywhere: it closes the keyboard once the number is complete.
            MobileField(
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                label = stringResource(R.string.field_phone),
                error = state.errors.phone,
                prefix = stringResource(R.string.phone_country_prefix)
            )

            OutlinedTextField(
                value = state.address,
                onValueChange = viewModel::onAddressChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_address)) }
            )

            // A clear "saved" message. It goes away as soon as something is edited again.
            if (state.saved) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = stringResource(R.string.shop_saved),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

package com.example.dukanhisabnext.ui.customers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
import com.example.dukanhisabnext.ui.components.AppTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.example.dukanhisabnext.ui.components.FormCard
import com.example.dukanhisabnext.ui.components.SecondaryButton
import com.example.dukanhisabnext.ui.components.messageRes

/**
 * Add a new customer, or edit one (when the route carries a customer id).
 * [onSaved] receives the saved customer's id before the screen closes: a sale uses it to select
 * the customer it just added.
 */
@Composable
fun CustomerEditScreen(
    onBack: () -> Unit,
    onSaved: (customerId: Long) -> Unit = {},
    viewModel: CustomerEditViewModel = appViewModel { app, args -> CustomerEditViewModel(app.customerRepository, args) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // After a successful save the ViewModel sets finished = true: report the id, then close.
    LaunchedEffect(state.finished) {
        if (state.finished) {
            state.savedCustomerId?.let(onSaved)
            onBack()
        }
    }

    // The system phonebook picker. It hands back ONE chosen number, and gives this app
    // temporary read access to just that row, so READ_CONTACTS is not needed.
    val phonebook = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        readPickedContact(context, uri)?.let { (name, number) -> viewModel.onPhonebookPicked(name, number) }
    }

    SubScreenScaffold(
        title = stringResource(if (state.isNew) R.string.title_customer_new else R.string.title_customer_edit),
        onBack = onBack
    ) { padding ->
        // The Save button is pinned above the keyboard (FormWithSaveBar); the fields scroll.
        FormWithSaveBar(
            padding = padding,
            saveBar = { SaveBar(stringResource(R.string.btn_save), !state.isSaving && !state.isLoading, viewModel::save) }
        ) {
            if (state.isNew) {
                SecondaryButton(
                    text = stringResource(R.string.btn_from_phonebook),
                    onClick = {
                        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                        try {
                            phonebook.launch(intent)
                        } catch (e: ActivityNotFoundException) {
                            // A device with no phonebook app: the boxes can still be typed by hand.
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            FormCard {
            AppTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_customer_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                isError = state.errors.name != null,
                supportingText = state.errors.name?.let { { Text(stringResource(it.messageRes())) } }
            )

            // The keyboard closes by itself as soon as the number is complete (see MobileField).
            MobileField(
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                label = stringResource(R.string.field_phone),
                error = state.errors.phone,
                warning = if (state.phoneWarning) stringResource(R.string.phone_duplicate_warning) else null
            )

            AppTextField(
                value = state.address,
                onValueChange = viewModel::onAddressChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_address)) }
            )

            AppTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_note)) }
            )
            }
        }
    }
}

/**
 * Reads the name and the number of the phonebook row the user picked. The picker gives this app
 * temporary access to exactly that row, so no permission is needed.
 */
private fun readPickedContact(context: Context, uri: Uri): Pair<String?, String>? {
    val columns = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    return try {
        context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) to (cursor.getString(1) ?: "") else null
        }
    } catch (e: SecurityException) {
        null // access was not granted: nothing is filled in
    }
}

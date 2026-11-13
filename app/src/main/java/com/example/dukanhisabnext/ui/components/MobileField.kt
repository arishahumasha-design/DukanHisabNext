package com.example.dukanhisabnext.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.dukanhisabnext.ui.customers.isCompleteMobile
import com.example.dukanhisabnext.ui.products.FormError

/**
 * The ONE mobile-number box used everywhere (payment, নতুন বাকি, add/edit customer).
 * Keyboard rule: as soon as the typed number is a complete, valid mobile (see [isCompleteMobile]),
 * the keyboard is hidden and the box lets go of the focus. Tapping the box again opens it.
 * [prefix] is for example "+88 ". [error] is the red message; [warning] a gentle note below.
 */
@Composable
fun MobileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: FormError? = null,
    warning: String? = null,
    prefix: String? = null
) {
    val focusManager: FocusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val message: String? = error?.let { stringResource(it.messageRes()) } ?: warning

    AppTextField(
        value = value,
        onValueChange = { typed ->
            val clean = sanitizePhoneInput(typed)
            onValueChange(clean)
            // Only when the text CHANGED to a complete number (not on every redraw).
            if (clean != value && isCompleteMobile(clean)) {
                keyboard?.hide()
                focusManager.clearFocus()
            }
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        prefix = prefix?.let { { Text(it) } },
        isError = error != null,
        supportingText = message?.let { text -> { Text(text) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        visualTransformation = BengaliDigitsTransformation
    )
}

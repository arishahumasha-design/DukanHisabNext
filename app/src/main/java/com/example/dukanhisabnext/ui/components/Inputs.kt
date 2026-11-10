package com.example.dukanhisabnext.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.ui.products.FormError
import com.example.dukanhisabnext.util.toBengaliDigits
import com.example.dukanhisabnext.util.toLatinDigits

/**
 * Keeps only what a number box may contain. Bengali digits typed on a Bengali keyboard
 * are converted to normal digits, so the stored text is always plain ("12.5").
 */
fun sanitizeNumberInput(text: String): String =
    toLatinDigits(text).filter { it in '0'..'9' || it == '.' }

/**
 * Keeps only what a mobile number box may contain (digits, +, space, dash). Bengali digits
 * are converted to normal digits.
 */
fun sanitizePhoneInput(text: String): String =
    toLatinDigits(text).filter { it in '0'..'9' || it == '+' || it == ' ' || it == '-' }

/**
 * Shows normal digits as Bengali digits while typing. Every character maps to exactly
 * one character, so the cursor position needs no adjustment (OffsetMapping.Identity).
 * The text itself is not changed, only how it is drawn.
 */
object BengaliDigitsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(AnnotatedString(toBengaliDigits(text.text)), OffsetMapping.Identity)
}

/** The Bengali message for each error code. */
fun FormError.messageRes(): Int = when (this) {
    FormError.REQUIRED -> R.string.error_required
    FormError.INVALID_NUMBER -> R.string.error_invalid_number
    FormError.INVALID_PHONE -> R.string.error_invalid_phone
    FormError.MUST_BE_POSITIVE -> R.string.error_must_be_positive
    FormError.DUPLICATE -> R.string.error_duplicate
    FormError.NO_CHANGE -> R.string.error_no_change
    FormError.TOO_LARGE -> R.string.error_too_large
    FormError.DISCOUNT_TOO_LARGE -> R.string.error_discount_too_large
    FormError.PERCENT_OUT_OF_RANGE -> R.string.error_percent_range
    FormError.DUE_MORE_THAN_TOTAL -> R.string.error_due_more_than_total
}

/** True while the on-screen keyboard is showing. (Compose marks this API as experimental.) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberImeVisible(): Boolean = WindowInsets.isImeVisible

/**
 * A text box for money or quantity: number keypad, Bengali digits, error text below.
 * [prefix] (for example "৳") and [suffix] (for example "কেজি") are drawn inside the box.
 */
@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: FormError? = null,
    enabled: Boolean = true,
    prefix: String? = null,
    suffix: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(sanitizeNumberInput(it)) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it.messageRes())) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        visualTransformation = BengaliDigitsTransformation
    )
}

/**
 * A box that opens a menu when tapped (a "dropdown"). [menuContent] fills the menu;
 * call the given function to close it after a choice.
 */
@Composable
fun DropdownField(
    label: String,
    selectedText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    menuContent: @Composable ColumnScope.(close: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    // A read-only text box swallows taps, so we watch for a finished press ourselves.
    LaunchedEffect(interactionSource, enabled) {
        interactionSource.interactions.collect { interaction ->
            if (enabled && interaction is PressInteraction.Release) expanded = true
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menuContent { expanded = false }
        }
    }
}

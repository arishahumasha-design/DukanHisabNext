package com.example.dukanhisabnext.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.ui.theme.AppTheme

/** The shape of every text field (14dp, the same as the buttons). */
val FieldShape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)

/**
 * The colours of every text field: a 1dp border in the border colour; while focused a 2dp primary border
 * and a primary label (Material draws the thicker line itself); a red border and message when there is an
 * error. The helper and counter text use the caption colour.
 */
@Composable
fun appTextFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    val border = AppTheme.colors.border
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = border,
        disabledBorderColor = border,
        errorBorderColor = scheme.error,
        focusedLabelColor = scheme.primary,
        unfocusedLabelColor = scheme.onSurfaceVariant,
        errorLabelColor = scheme.error,
        cursorColor = scheme.primary,
        focusedContainerColor = scheme.surface,
        unfocusedContainerColor = scheme.surface,
        disabledContainerColor = scheme.surface,
        errorContainerColor = scheme.surface,
        focusedSupportingTextColor = scheme.onSurfaceVariant,
        unfocusedSupportingTextColor = scheme.onSurfaceVariant,
        errorSupportingTextColor = scheme.error
    )
}

/**
 * THE text field of the app (a themed [OutlinedTextField]): 14dp corners, at least 56dp high, the colours
 * of [appTextFieldColors]. The supporting text (error message, helper, counter) is drawn by Material in
 * the caption style. Every field in the app uses this, so they all look the same.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = FieldShape,
        colors = appTextFieldColors()
    )
}

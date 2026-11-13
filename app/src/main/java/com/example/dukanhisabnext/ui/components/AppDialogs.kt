package com.example.dukanhisabnext.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.dukanhisabnext.ui.theme.Spacing

/**
 * THE dialog of the app: 20dp corners (the theme's "extra large" shape), the surface colour, a title in
 * the section style (16sp SemiBold). Its text buttons are green; use [DestructiveTextButton] for the one
 * that deletes or cancels something.
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title?.let { t -> { ProvideTextStyle(MaterialTheme.typography.titleMedium) { t() } } },
        text = text,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        properties = properties
    )
}

/** A text button in the primary green (48dp high), for dialogs. */
@Composable
fun DialogTextButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A text button in red: for the action that deletes, archives or cancels something. */
@Composable
fun DestructiveTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A soft coloured card with a message (the amber warning, the blue note). */
@Composable
fun InfoCard(
    text: String,
    modifier: Modifier = Modifier,
    variant: PillVariant = PillVariant.Info
) {
    val c = com.example.dukanhisabnext.ui.theme.AppTheme.colors
    val (container, content) = when (variant) {
        PillVariant.Due, PillVariant.LowStock -> c.amberContainer to c.amberText
        PillVariant.Paid -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        PillVariant.Cancelled -> c.redContainer to MaterialTheme.colorScheme.onErrorContainer
        PillVariant.Info -> c.infoContainer to MaterialTheme.colorScheme.onTertiaryContainer
        PillVariant.Neutral -> c.border to c.textSecondary
    }
    com.example.dukanhisabnext.ui.theme.AppCard(
        modifier = modifier,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = container, contentColor = content),
        border = null
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Spacing.l))
    }
}

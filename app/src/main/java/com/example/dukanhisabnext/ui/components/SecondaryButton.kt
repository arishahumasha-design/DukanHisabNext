package com.example.dukanhisabnext.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.ui.theme.AppTheme
import com.example.dukanhisabnext.ui.theme.ButtonShape

/** The quiet button: outlined, 14dp corners, at least 52dp high. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.primary else AppTheme.colors.border)
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A small outlined button for a row (for example "ফিরিয়ে আনুন"): 48dp high, 14dp corners. */
@Composable
fun SmallOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.primary else AppTheme.colors.border)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** The red outlined button for something that undoes a thing (for example "বিক্রি বাতিল করুন"): 14dp corners, 52dp high. */
@Composable
fun DestructiveOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.error else AppTheme.colors.border)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A small pill-shaped outlined button for a list row (for example "স্টক"): green border, green text,
 * 40dp high. Material still gives it a 48dp touch area, so it is easy to hit.
 */
@Composable
fun PillOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        shape = androidx.compose.foundation.shape.CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.primary else AppTheme.colors.border)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

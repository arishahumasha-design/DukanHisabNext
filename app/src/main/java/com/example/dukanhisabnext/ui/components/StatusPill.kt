package com.example.dukanhisabnext.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.ui.theme.AppTheme
import com.example.dukanhisabnext.ui.theme.PillShape

/** The kinds of small status label: due = amber, paid = green, cancelled = red, lowStock = amber, info = blue, neutral = grey. */
enum class PillVariant { Due, Paid, Cancelled, LowStock, Info, Neutral }

/** A small rounded label (10dp corners) such as "বাকি" or "বাতিল". */
@Composable
fun StatusPill(text: String, variant: PillVariant, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val (container, content) = when (variant) {
        PillVariant.Due, PillVariant.LowStock -> c.amberContainer to c.amberText
        PillVariant.Paid -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        PillVariant.Cancelled -> c.redContainer to MaterialTheme.colorScheme.onErrorContainer
        PillVariant.Info -> c.infoContainer to MaterialTheme.colorScheme.onTertiaryContainer
        PillVariant.Neutral -> c.border to c.textSecondary
    }
    Text(
        text = text,
        modifier = modifier.background(container, PillShape).padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = content
    )
}

package com.example.dukanhisabnext.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A row of big buttons where exactly one is chosen (for example Cash / bKash / Nagad).
 * The chosen one is filled, the others are outlined. At least 52dp high: easy to hit.
 */
@Composable
fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val buttonModifier = Modifier.weight(1f).heightIn(min = 52.dp)
            if (value == selected) {
                Button(onClick = { onSelect(value) }, modifier = buttonModifier, enabled = enabled) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                OutlinedButton(onClick = { onSelect(value) }, modifier = buttonModifier, enabled = enabled) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

package com.example.dukanhisabnext.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.entity.WarrantyUnit
import com.example.dukanhisabnext.ui.components.AppTextField
import com.example.dukanhisabnext.ui.components.BengaliDigitsTransformation
import com.example.dukanhisabnext.ui.components.SegmentedControl
import com.example.dukanhisabnext.ui.components.messageRes
import com.example.dukanhisabnext.ui.theme.Spacing

/** The Bengali word of a warranty unit: দিন, মাস, বছর. */
fun WarrantyUnit.labelRes(): Int = when (this) {
    WarrantyUnit.DAY -> R.string.warranty_unit_day
    WarrantyUnit.MONTH -> R.string.warranty_unit_month
    WarrantyUnit.YEAR -> R.string.warranty_unit_year
}

/** The red message under the warranty box: the limit of the chosen unit for a number out of range. */
@Composable
fun warrantyErrorText(error: FormError?, unit: WarrantyUnit): String? = when (error) {
    null -> null
    FormError.WARRANTY_OUT_OF_RANGE -> stringResource(
        when (unit) {
            WarrantyUnit.DAY -> R.string.warranty_range_day
            WarrantyUnit.MONTH -> R.string.warranty_range_month
            WarrantyUnit.YEAR -> R.string.warranty_range_year
        }
    )
    else -> stringResource(error.messageRes())
}

/**
 * The warranty controls, shared by the product form and the cart's warranty dialog: the switch
 * "ওয়ারেন্টি আছে"; when it is on, a whole-number box and the unit (দিন / মাস / বছর) as a segmented control.
 */
@Composable
fun WarrantyEditor(
    on: Boolean,
    text: String,
    unit: WarrantyUnit,
    error: FormError?,
    onOnChange: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
    onUnitChange: (WarrantyUnit) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.warranty_switch),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = on, onCheckedChange = onOnChange, enabled = enabled)
        }
        if (on) {
            val message = warrantyErrorText(error, unit)
            AppTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.warranty_value_label)) },
                singleLine = true,
                enabled = enabled,
                isError = error != null,
                supportingText = message?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = BengaliDigitsTransformation
            )
            SegmentedControl(
                labels = WarrantyUnit.entries.map { stringResource(it.labelRes()) },
                selectedIndex = unit.ordinal,
                onSelect = { onUnitChange(WarrantyUnit.entries[it]) },
                enabled = enabled
            )
        }
    }
}

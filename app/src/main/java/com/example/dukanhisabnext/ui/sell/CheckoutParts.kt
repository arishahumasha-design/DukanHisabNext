package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.ui.theme.AppCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.example.dukanhisabnext.ui.components.AppTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.ui.components.PrimaryButton
import com.example.dukanhisabnext.util.formatTaka

// Pieces shared by the payment screen and the "নতুন বাকি" screen.
// (The customer picker is in CustomerPicker.kt.)

/** The bar at the top: "সর্বমোট ৳ X". */
@Composable
fun TotalBar(total: Long) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_grand_total),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.price_format, formatTaka(total)),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** A label and an on/off switch, at least 56dp high. */
@Composable
fun LabeledSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** The "মন্তব্য" box (saved as the sale's note). */
@Composable
fun NoteField(value: String, onValueChange: (String) -> Unit) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.field_sale_note)) }
    )
}

/** A spinner with "সংরক্ষণ হচ্ছে…" while a save is running. */
@Composable
fun SavingRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(stringResource(R.string.saving_sale), style = MaterialTheme.typography.titleMedium)
    }
}

/** A message for a save that did not happen. Problems in the cart offer the way back to it. */
@Composable
fun CheckoutErrorCard(error: PaymentError, onBackToCart: () -> Unit) {
    val message = when (error) {
        is PaymentError.ProductsUnavailable ->
            stringResource(R.string.error_products_unavailable, error.productNames.joinToString(", "))
        PaymentError.CustomerNotFound -> stringResource(R.string.error_customer_not_found)
        PaymentError.CustomerRequired -> stringResource(R.string.error_customer_required)
        PaymentError.SaleInvalid -> stringResource(R.string.error_sale_invalid)
        PaymentError.SaveFailed -> stringResource(R.string.error_save_failed)
    }
    val needsCartFix = error is PaymentError.ProductsUnavailable || error == PaymentError.SaleInvalid

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
            if (needsCartFix) {
                PrimaryButton(text = stringResource(R.string.btn_back_to_cart), onClick = onBackToCart)
            }
        }
    }
}

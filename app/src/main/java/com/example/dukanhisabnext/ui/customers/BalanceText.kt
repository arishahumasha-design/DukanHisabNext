package com.example.dukanhisabnext.ui.customers

import com.example.dukanhisabnext.ui.theme.AppTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.util.formatTaka

@Composable
fun BalanceStatus.color(): Color = when (this) {
    BalanceStatus.OWES -> AppTheme.colors.amberText // amber
    BalanceStatus.ADVANCE -> MaterialTheme.colorScheme.primary // green
    BalanceStatus.CLEAR -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Shows a customer's balance in words and colour:
 *   owes -> "৳ ১২০" (amber), nothing owed -> "বাকি নেই", ahead -> "অগ্রিম ৳ ৫০" (green).
 */
@Composable
fun BalanceText(
    balance: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    val status = balanceStatus(balance)
    val text = when (status) {
        BalanceStatus.OWES -> stringResource(R.string.price_format, formatTaka(balance))
        BalanceStatus.CLEAR -> stringResource(R.string.balance_clear)
        BalanceStatus.ADVANCE -> stringResource(R.string.balance_advance, formatTaka(-balance))
    }
    Text(text = text, color = status.color(), style = style, modifier = modifier)
}

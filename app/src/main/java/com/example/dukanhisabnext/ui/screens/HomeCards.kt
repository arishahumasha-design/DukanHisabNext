package com.example.dukanhisabnext.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.ui.theme.AppCard
import com.example.dukanhisabnext.ui.theme.AppTheme
import com.example.dukanhisabnext.ui.theme.Spacing

// The cards of the Home dashboard (Design V2). The numbers and texts are the same as before; only the
// look changed: a green hero card for today's sales, two small stat tiles, a low-stock tile (amber
// when something needs attention, calm green when all is well), a row of four quick actions and a
// tappable backup reminder card. Every card is tappable (the whole card, at least 48dp tall).

/** Today's sales: the big green card with the amount (32sp), the sale count and a basket icon. */
@Composable
fun HeroSalesCard(title: String, amount: String, hint: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.hero, contentColor = colors.onHero),
        border = null,
        onClick = onClick
    ) {
        Row(modifier = Modifier.padding(Spacing.l), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = colors.onHero.copy(alpha = 0.9f))
                Text(amount, style = MaterialTheme.typography.headlineLarge, color = colors.onHero)
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = colors.onHero.copy(alpha = 0.9f))
            }
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.onHero.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_sell), contentDescription = null, tint = colors.onHero, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** Low stock: amber with a warning icon when [needsAttention], a calm green "everything is fine" otherwise. */
@Composable
fun LowStockTile(title: String, value: String, needsAttention: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val container = if (needsAttention) colors.amberContainer else MaterialTheme.colorScheme.primaryContainer
    val on = if (needsAttention) colors.amberText else MaterialTheme.colorScheme.onPrimaryContainer
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = on),
        border = null,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(Spacing.l).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Icon(
                painterResource(if (needsAttention) R.drawable.ic_warning else R.drawable.ic_check),
                contentDescription = null,
                tint = if (needsAttention) colors.amber else on,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.bodySmall, color = on)
                Text(value, style = MaterialTheme.typography.titleMedium, color = on)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = on, modifier = Modifier.size(20.dp))
        }
    }
}

/** One quick action: a soft green rounded square with an icon, and its label under it. */
@Composable
private fun QuickAction(label: String, @DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        }
        Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

/** The row of four quick actions. They only open screens that already exist. */
@Composable
fun QuickActionsRow(
    onNewSale: () -> Unit,
    onReceiveMoney: () -> Unit,
    onNewExpense: () -> Unit,
    onNewProduct: () -> Unit
) {
    val newSale = androidx.compose.ui.res.stringResource(R.string.home_quick_new_sale)
    val receive = androidx.compose.ui.res.stringResource(R.string.home_quick_receive)
    val newExpense = androidx.compose.ui.res.stringResource(R.string.home_quick_new_expense)
    val newProduct = androidx.compose.ui.res.stringResource(R.string.home_quick_new_product)
    // With a very large system font the four actions wrap into a 2x2 grid, so no label is cut off.
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                QuickAction(newSale, R.drawable.ic_sell, onNewSale, Modifier.weight(1f))
                QuickAction(receive, R.drawable.ic_khata, onReceiveMoney, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                QuickAction(newExpense, R.drawable.ic_expense, onNewExpense, Modifier.weight(1f))
                QuickAction(newProduct, R.drawable.ic_product, onNewProduct, Modifier.weight(1f))
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            QuickAction(newSale, R.drawable.ic_sell, onNewSale, Modifier.weight(1f))
            QuickAction(receive, R.drawable.ic_khata, onReceiveMoney, Modifier.weight(1f))
            QuickAction(newExpense, R.drawable.ic_expense, onNewExpense, Modifier.weight(1f))
            QuickAction(newProduct, R.drawable.ic_product, onNewProduct, Modifier.weight(1f))
        }
    }
}

/** "No backup for more than 7 days": a tappable card with a backup icon that opens the backup screen. */
@Composable
fun BackupReminderCard(text: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.infoContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
        border = null,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(Spacing.l).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Icon(painterResource(R.drawable.ic_backup), contentDescription = null, tint = colors.info, modifier = Modifier.size(28.dp))
            Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

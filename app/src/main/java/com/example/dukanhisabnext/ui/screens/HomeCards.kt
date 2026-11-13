package com.example.dukanhisabnext.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.ui.theme.AppCard
import com.example.dukanhisabnext.ui.theme.GoodContainerDark
import com.example.dukanhisabnext.ui.theme.GoodContainerLight
import com.example.dukanhisabnext.ui.theme.HeroDark
import com.example.dukanhisabnext.ui.theme.HeroLight
import com.example.dukanhisabnext.ui.theme.OnGoodContainerDark
import com.example.dukanhisabnext.ui.theme.OnGoodContainerLight
import com.example.dukanhisabnext.ui.theme.OnHeroDark
import com.example.dukanhisabnext.ui.theme.OnHeroLight
import com.example.dukanhisabnext.ui.theme.OnWarnContainerDark
import com.example.dukanhisabnext.ui.theme.OnWarnContainerLight
import com.example.dukanhisabnext.ui.theme.Spacing
import com.example.dukanhisabnext.ui.theme.WarnContainerDark
import com.example.dukanhisabnext.ui.theme.WarnContainerLight
import com.example.dukanhisabnext.ui.theme.WarnIconDark
import com.example.dukanhisabnext.ui.theme.WarnIconLight
import com.example.dukanhisabnext.ui.theme.isDarkScheme

// The four cards of the Home dashboard (M9 group C). The numbers and texts are the same as before;
// only the look changed: a green hero card for today's sales, two smaller cards side by side, and a
// low-stock card that is amber when something needs attention and a calm green when all is well.
// Every card is tappable (the whole card, at least 48dp tall).

/** Today's sales: the big green card with the amount, the sale count and a shopping-cart icon. */
@Composable
fun HeroSalesCard(title: String, amount: String, hint: String, onClick: () -> Unit) {
    val dark = isDarkScheme()
    val on = if (dark) OnHeroDark else OnHeroLight
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (dark) HeroDark else HeroLight, contentColor = on),
        onClick = onClick
    ) {
        Row(modifier = Modifier.padding(Spacing.l), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = on.copy(alpha = 0.85f))
                Text(amount, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = on)
                Text(hint, style = MaterialTheme.typography.bodyLarge, color = on.copy(alpha = 0.9f))
            }
            Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = on.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
        }
    }
}

/** Today's expense (with the shop / personal split) and the total receivable: the two smaller cards. */
@Composable
fun SmallStatCard(
    title: String,
    value: String,
    valueColor: Color,
    icon: ImageVector,
    hint: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.padding(Spacing.l).heightIn(min = 48.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

val ExpenseIcon: ImageVector get() = Icons.AutoMirrored.Filled.List
val ReceivableIcon: ImageVector get() = Icons.Filled.Person

/** Low stock: amber with a warning icon when [needsAttention], a calm green "everything is fine" otherwise. */
@Composable
fun LowStockCard(title: String, value: String, needsAttention: Boolean, onClick: () -> Unit) {
    val dark = isDarkScheme()
    val container = when {
        needsAttention -> if (dark) WarnContainerDark else WarnContainerLight
        else -> if (dark) GoodContainerDark else GoodContainerLight
    }
    val on = when {
        needsAttention -> if (dark) OnWarnContainerDark else OnWarnContainerLight
        else -> if (dark) OnGoodContainerDark else OnGoodContainerLight
    }
    val iconTint = when {
        needsAttention -> if (dark) WarnIconDark else WarnIconLight
        else -> on
    }
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = on),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(Spacing.l).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Icon(
                if (needsAttention) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(32.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = on.copy(alpha = 0.85f))
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = on)
            }
        }
    }
}

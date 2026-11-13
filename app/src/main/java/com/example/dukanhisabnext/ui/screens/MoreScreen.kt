package com.example.dukanhisabnext.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.ui.components.TopBar
import com.example.dukanhisabnext.ui.theme.AppCard
import com.example.dukanhisabnext.ui.theme.AppTheme
import com.example.dukanhisabnext.ui.theme.Spacing

/**
 * The More tab: a list of places to go, in one card (the same card as everywhere), each row with a
 * green icon in a soft square, from our own line icon set. Products, contacts (যোগাযোগ), the sales
 * list, expenses (খরচ), reports (রিপোর্ট) and Settings.
 */
@Composable
fun MoreScreen(
    onOpenProducts: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenSales: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopBar(title = stringResource(R.string.title_more))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l, vertical = Spacing.s)
        ) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                MoreItem(label = stringResource(R.string.more_products), icon = R.drawable.ic_product, onClick = onOpenProducts)
                MoreItem(label = stringResource(R.string.more_contacts), icon = R.drawable.ic_contacts, onClick = onOpenContacts)
                MoreItem(label = stringResource(R.string.more_sales), icon = R.drawable.ic_receipt, onClick = onOpenSales)
                MoreItem(label = stringResource(R.string.more_expenses), icon = R.drawable.ic_expense, onClick = onOpenExpenses)
                MoreItem(label = stringResource(R.string.more_reports), icon = R.drawable.ic_report, onClick = onOpenReports)
                MoreItem(label = stringResource(R.string.more_settings), icon = R.drawable.ic_settings, onClick = onOpenSettings, last = true)
            }
        }
    }
}

@Composable
private fun MoreItem(label: String, @DrawableRes icon: Int, onClick: () -> Unit, last: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = Spacing.m))
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
    if (!last) HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.l), color = AppTheme.colors.border)
}

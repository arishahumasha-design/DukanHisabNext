package com.example.dukanhisabnext.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.ui.theme.AppCard
import com.example.dukanhisabnext.ui.theme.Spacing

/**
 * The More tab: a list of places to go, in one card (the same card as everywhere), each row with a
 * small green icon from the built-in Material set. Products, contacts (যোগাযোগ), the sales list,
 * expenses (খরচ), reports (রিপোর্ট) and Settings.
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
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l)) {
        Text(
            text = stringResource(R.string.title_more),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = Spacing.m)
        )
        AppCard(modifier = Modifier.fillMaxWidth()) {
            MoreItem(label = stringResource(R.string.more_products), icon = Icons.Filled.AccountBox, onClick = onOpenProducts)
            MoreItem(label = stringResource(R.string.more_contacts), icon = Icons.Filled.Call, onClick = onOpenContacts)
            MoreItem(label = stringResource(R.string.more_sales), icon = Icons.AutoMirrored.Filled.List, onClick = onOpenSales)
            MoreItem(label = stringResource(R.string.more_expenses), icon = Icons.Filled.ExitToApp, onClick = onOpenExpenses)
            MoreItem(label = stringResource(R.string.more_reports), icon = Icons.Filled.DateRange, onClick = onOpenReports)
            MoreItem(label = stringResource(R.string.more_settings), icon = Icons.Filled.Settings, onClick = onOpenSettings, last = true)
        }
    }
}

@Composable
private fun MoreItem(label: String, icon: ImageVector, onClick: () -> Unit, last: Boolean = false) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).heightIn(min = 56.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(label, style = MaterialTheme.typography.titleMedium) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    )
    if (!last) HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.l), color = MaterialTheme.colorScheme.outlineVariant)
}

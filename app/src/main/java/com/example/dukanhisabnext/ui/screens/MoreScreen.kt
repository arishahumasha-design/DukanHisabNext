package com.example.dukanhisabnext.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.R

/**
 * The More tab: a list of places to go. Products and the shop info exist so far.
 * Expenses, Reports and the other settings are added in their own milestones (no dead buttons).
 */
@Composable
fun MoreScreen(onOpenProducts: () -> Unit, onOpenShop: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.title_more),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        MoreItem(label = stringResource(R.string.more_products), onClick = onOpenProducts)
        MoreItem(label = stringResource(R.string.more_shop), onClick = onOpenShop)
    }
}

@Composable
private fun MoreItem(label: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(label) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
    )
    HorizontalDivider()
}

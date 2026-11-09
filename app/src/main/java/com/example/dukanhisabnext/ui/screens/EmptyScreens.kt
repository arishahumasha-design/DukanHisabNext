package com.example.dukanhisabnext.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.dukanhisabnext.R

// In M0 every screen is just a centred title. Real content comes in later milestones.
@Composable
private fun EmptyScreen(@StringRes titleRes: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) = EmptyScreen(R.string.title_home, modifier)

@Composable
fun SellScreen(modifier: Modifier = Modifier) = EmptyScreen(R.string.title_sell, modifier)

@Composable
fun KhataScreen(modifier: Modifier = Modifier) = EmptyScreen(R.string.title_khata, modifier)

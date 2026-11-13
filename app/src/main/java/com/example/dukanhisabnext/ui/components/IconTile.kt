package com.example.dukanhisabnext.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.ui.theme.PillShape

/** A rounded square in the soft primary tint with a line icon in the primary green (44dp by default). */
@Composable
fun IconTile(@DrawableRes icon: Int, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier = modifier.size(size).clip(PillShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
    }
}

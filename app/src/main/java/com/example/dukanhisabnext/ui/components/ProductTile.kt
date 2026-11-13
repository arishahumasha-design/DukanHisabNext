package com.example.dukanhisabnext.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.ui.settings.avatarLetter
import com.example.dukanhisabnext.ui.theme.AvatarTintsDark
import com.example.dukanhisabnext.ui.theme.AvatarTintsLight
import com.example.dukanhisabnext.ui.theme.PillShape
import com.example.dukanhisabnext.ui.theme.isDarkScheme

/** A rounded square with the first letter of the product's name (the same tints as the avatars). */
@Composable
fun ProductTile(name: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val (background, letterColor) = (if (isDarkScheme()) AvatarTintsDark else AvatarTintsLight)[avatarTintIndex(name)]
    Box(
        modifier = modifier.size(size).clip(PillShape).background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = avatarLetter(name) ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = letterColor
        )
    }
}

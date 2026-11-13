package com.example.dukanhisabnext.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.example.dukanhisabnext.ui.theme.isDarkScheme

/** How many soft tints the avatars choose from. */
const val AVATAR_TINT_COUNT = 6

/**
 * Which of the six tints a name gets. It depends only on the name's text (String.hashCode is
 * defined by the language, so it is the same on every phone and every run), so a customer keeps
 * the same colour for ever.
 */
fun avatarTintIndex(name: String): Int = Math.floorMod(name.trim().hashCode(), AVATAR_TINT_COUNT)

/** A circle with the first letter of [name] on a soft tint (40dp by default). */
@Composable
fun InitialAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val (background, letterColor) = (if (isDarkScheme()) AvatarTintsDark else AvatarTintsLight)[avatarTintIndex(name)]
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = avatarLetter(name) ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = letterColor
        )
    }
}

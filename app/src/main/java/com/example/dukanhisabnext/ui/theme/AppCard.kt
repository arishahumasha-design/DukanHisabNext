package com.example.dukanhisabnext.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale of the app (dp). Screens use these steps, never odd numbers:
 * 4 between a label and its value, 8 between small things, 12 between cards and fields,
 * 16 at the screen edge and inside cards, 24 between groups.
 */
object Spacing {
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 24.dp
}

/** True while the colour scheme in use is the dark one. */
@Composable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * THE card of the app: the surface colour, a 1dp border, no shadow, the theme's 16dp corners. Every
 * card uses it, so they all look the same. A card that carries a message (an error, a "saved" note)
 * passes its own [colors] but keeps the same shape. [border] is null for a solid card (the hero).
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    colors: CardColors = appCardColors(),
    border: BorderStroke? = BorderStroke(1.dp, AppTheme.colors.border),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // A card you can tap uses Card's own onClick, so the ripple follows the rounded corners.
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            colors = colors,
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            colors = colors,
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content
        )
    }
}

/** The default card colours: the surface colour with the normal text colour. */
@Composable
fun appCardColors(): CardColors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface
)

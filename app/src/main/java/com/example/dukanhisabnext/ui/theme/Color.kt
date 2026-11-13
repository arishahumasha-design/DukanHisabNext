package com.example.dukanhisabnext.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// DokanHisab colours (Design V2). All the colours of the app live here; Theme.kt puts them into
// the Material colour schemes and into [AppColors] (the extra roles Material has no name for).
// Screens never use a hex colour: they read MaterialTheme.colorScheme or AppTheme.colors.

/** The extra colour roles: borders, secondary text, the amber (dues), red, blue and the hero card. */
@Immutable
data class AppColors(
    val border: Color,
    val textSecondary: Color,
    val amber: Color,          // icons and fills for "due"
    val amberText: Color,      // amber as TEXT: a little deeper so it reads at 4.5:1 on cards
    val amberContainer: Color,
    val red: Color,
    val redContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val hero: Color,           // the green card of today's sales
    val onHero: Color
)

// ---- light ----
val LightBackground = Color(0xFFF6F8F7)
val LightSurface = Color(0xFFFFFFFF)
val LightBorder = Color(0xFFE3E8E5)
val LightText = Color(0xFF14211A)
val LightTextSecondary = Color(0xFF5B6B62)
val LightPrimary = Color(0xFF0F7B4F)
val LightPrimaryContainer = Color(0xFFDDF3E8)

internal val LightAppColors = AppColors(
    border = LightBorder,
    textSecondary = LightTextSecondary,
    amber = Color(0xFFB26A00),
    amberText = Color(0xFF8A5100),
    amberContainer = Color(0xFFFFF1D6),
    red = Color(0xFFC62828),
    redContainer = Color(0xFFFDE7E7),
    info = Color(0xFF1C5FBF),
    infoContainer = Color(0xFFE3EEFD),
    hero = LightPrimary,
    onHero = Color(0xFFFFFFFF)
)

// ---- dark ----
val DarkBackground = Color(0xFF0E1411)
val DarkSurface = Color(0xFF17201B)
val DarkBorder = Color(0xFF26332C)
val DarkText = Color(0xFFE6EFE9)
val DarkTextSecondary = Color(0xFF9FB1A6)
val DarkPrimary = Color(0xFF4CC38A)
val DarkPrimaryContainer = Color(0xFF123D2A)

internal val DarkAppColors = AppColors(
    border = DarkBorder,
    textSecondary = DarkTextSecondary,
    amber = Color(0xFFF2B04C),
    amberText = Color(0xFFF2B04C),
    amberContainer = Color(0xFF3A2A0C),
    red = Color(0xFFFF8A80),
    redContainer = Color(0xFF3D1A1A),
    info = Color(0xFF8DB6F5),
    infoContainer = Color(0xFF172A47),
    hero = Color(0xFF14553A),
    onHero = Color(0xFFFFFFFF)
)

/** Six soft tints for the initial avatars: (background, letter) for light and dark. */
internal val AvatarTintsLight = listOf(
    Color(0xFFDDF3E8) to Color(0xFF0B5A3A),
    Color(0xFFE3EEFD) to Color(0xFF184C98),
    Color(0xFFFFF1D6) to Color(0xFF7A4700),
    Color(0xFFFDE7E7) to Color(0xFF9B1F1F),
    Color(0xFFEDE7F8) to Color(0xFF52399A),
    Color(0xFFDDF1F3) to Color(0xFF0C5A64)
)
internal val AvatarTintsDark = listOf(
    Color(0xFF123D2A) to Color(0xFF8FE0B9),
    Color(0xFF172A47) to Color(0xFF8DB6F5),
    Color(0xFF3A2A0C) to Color(0xFFF2B04C),
    Color(0xFF3D1A1A) to Color(0xFFFF8A80),
    Color(0xFF2A2142) to Color(0xFFC3B2F2),
    Color(0xFF0F3338) to Color(0xFF86D4DE)
)

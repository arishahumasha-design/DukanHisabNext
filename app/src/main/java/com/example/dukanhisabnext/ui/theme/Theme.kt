package com.example.dukanhisabnext.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Visible to the tests (they compare the window colour with the "background" below).
internal val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF05241A),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = Color(0xFFBFEFD6),
    secondary = DarkPrimary,
    onSecondary = Color(0xFF05241A),
    secondaryContainer = DarkPrimaryContainer,
    onSecondaryContainer = Color(0xFFBFEFD6),
    tertiary = DarkAppColors.info,
    tertiaryContainer = DarkAppColors.infoContainer,
    onTertiaryContainer = Color(0xFFD3E4FC),
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = DarkAppColors.red,
    onError = Color(0xFF3D1A1A),
    errorContainer = DarkAppColors.redContainer,
    onErrorContainer = Color(0xFFFFD6D2),
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurface,
    surfaceContainerHighest = DarkSurface,
    inverseSurface = Color(0xFF2B3A31),
    inverseOnSurface = DarkText,
    inversePrimary = DarkPrimary
)

internal val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = Color(0xFF0B3F2B),
    secondary = LightPrimary,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = LightPrimaryContainer,
    onSecondaryContainer = Color(0xFF0B3F2B),
    tertiary = LightAppColors.info,
    tertiaryContainer = LightAppColors.infoContainer,
    onTertiaryContainer = Color(0xFF0F3A78),
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = LightAppColors.red,
    onError = Color(0xFFFFFFFF),
    errorContainer = LightAppColors.redContainer,
    onErrorContainer = Color(0xFF8E1B1B),
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = LightSurface,
    inverseSurface = Color(0xFF1F2A24),
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF7FD9AE)
)

/** Cards 16dp, buttons 14dp, chips and pills 10dp, dialogs and sheets 20dp (one family, nothing sharp). */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

/** The shape of the buttons (14dp). */
val ButtonShape = RoundedCornerShape(14.dp)

/** The shape of chips and pills (10dp). */
val PillShape = RoundedCornerShape(10.dp)

private val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** Access to the extra colours: `AppTheme.colors.amber`. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

// No "dynamic colour" here: the app uses our own green colours,
// so it looks the same on every phone.
@Composable
fun DukanHisabNextTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppColors provides if (darkTheme) DarkAppColors else LightAppColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}

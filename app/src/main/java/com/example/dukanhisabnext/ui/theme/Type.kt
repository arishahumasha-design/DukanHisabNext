package com.example.dukanhisabnext.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.dukanhisabnext.R

// Noto Sans Bengali is ONE "variable" font file (res/font/noto_sans_bengali.ttf).
// A variable font holds every weight inside a single file, so below we load
// the same file three times and just pick a different weight each time.
@OptIn(ExperimentalTextApi::class)
val NotoSansBengali = FontFamily(
    Font(
        R.font.noto_sans_bengali,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.noto_sans_bengali,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.noto_sans_bengali,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

// Start from Material's default text styles, then swap the font in every one.
// "copy(fontFamily = ...)" means: same style, but with our Bengali font.
private val DefaultTypography = Typography()

val Typography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(fontFamily = NotoSansBengali),
    displayMedium = DefaultTypography.displayMedium.copy(fontFamily = NotoSansBengali),
    displaySmall = DefaultTypography.displaySmall.copy(fontFamily = NotoSansBengali),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontFamily = NotoSansBengali),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontFamily = NotoSansBengali),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontFamily = NotoSansBengali),
    titleLarge = DefaultTypography.titleLarge.copy(fontFamily = NotoSansBengali),
    titleMedium = DefaultTypography.titleMedium.copy(fontFamily = NotoSansBengali),
    titleSmall = DefaultTypography.titleSmall.copy(fontFamily = NotoSansBengali),
    bodyLarge = DefaultTypography.bodyLarge.copy(fontFamily = NotoSansBengali),
    bodyMedium = DefaultTypography.bodyMedium.copy(fontFamily = NotoSansBengali),
    bodySmall = DefaultTypography.bodySmall.copy(fontFamily = NotoSansBengali),
    labelLarge = DefaultTypography.labelLarge.copy(fontFamily = NotoSansBengali),
    labelMedium = DefaultTypography.labelMedium.copy(fontFamily = NotoSansBengali),
    labelSmall = DefaultTypography.labelSmall.copy(fontFamily = NotoSansBengali)
)

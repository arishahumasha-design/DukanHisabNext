package com.example.dukanhisabnext.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.dukanhisabnext.R

// Noto Sans Bengali is ONE "variable" font file (res/font/noto_sans_bengali.ttf).
// A variable font holds every weight inside a single file, so below we load
// the same file four times and just pick a different weight each time.
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
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.noto_sans_bengali,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

// Bengali needs a generous line height (about 1.45 x the size) so the matras above and below
// the letters are never clipped.
private fun style(size: Int, weight: FontWeight): TextStyle = TextStyle(
    fontFamily = NotoSansBengali,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * 1.45f).sp,
    letterSpacing = 0.sp
)

// The scale of the app: amount 32 SemiBold, screen title 22 SemiBold, section title 16 SemiBold,
// body 15, caption 13 (the caption colour, onSurfaceVariant, is set where it is used).
val Typography = Typography(
    displayLarge = style(32, FontWeight.SemiBold),
    displayMedium = style(32, FontWeight.SemiBold),
    displaySmall = style(32, FontWeight.SemiBold),
    headlineLarge = style(32, FontWeight.SemiBold),   // the amount display
    headlineMedium = style(22, FontWeight.SemiBold),  // the screen title
    headlineSmall = style(22, FontWeight.SemiBold),
    titleLarge = style(22, FontWeight.SemiBold),
    titleMedium = style(16, FontWeight.SemiBold),     // the section title
    titleSmall = style(15, FontWeight.Medium),
    bodyLarge = style(15, FontWeight.Normal),
    bodyMedium = style(15, FontWeight.Normal),
    bodySmall = style(13, FontWeight.Normal),         // the caption
    labelLarge = style(15, FontWeight.Medium),
    labelMedium = style(13, FontWeight.Medium),
    labelSmall = style(12, FontWeight.Medium)
)

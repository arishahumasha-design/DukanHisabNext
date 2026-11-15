package com.example.dukanhisabnext.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.dukanhisabnext.util.AppLanguage

private class AppTypefaceLoader(private val bengaliFirst: Boolean) : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface =
        AppTypefaces.typeface(context, font.weight.weight, bengaliFirst)

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
        loadBlocking(context, font)
}

// One Compose font per weight, each backed by AppTypefaces (Inter for Latin, Noto Sans Bengali for Bengali).
private class AppFont(
    override val weight: FontWeight,
    private val bengaliFirst: Boolean
) : AndroidFont(FontLoadingStrategy.Blocking, AppTypefaceLoader(bengaliFirst), FontVariation.Settings()) {
    override val style: FontStyle = FontStyle.Normal
    override fun equals(other: Any?) = other is AppFont && other.weight == weight && other.bengaliFirst == bengaliFirst
    override fun hashCode() = weight.weight * 31 + bengaliFirst.hashCode()
}

/** The app's font family (see AppTypefaces). [bengaliFirst]: Noto Sans Bengali is the first font. */
fun appFontFamily(bengaliFirst: Boolean): FontFamily = FontFamily(
    AppFont(FontWeight.Normal, bengaliFirst),
    AppFont(FontWeight.Medium, bengaliFirst),
    AppFont(FontWeight.SemiBold, bengaliFirst),
    AppFont(FontWeight.Bold, bengaliFirst)
)

// Bengali needs a generous line height (about 1.45 x the size) so the matras above and below
// the letters are never clipped. English text is a little tighter (1.4 x).
// Tabular figures: the bundled Inter files draw the digits 0-9 tabular BY DEFAULT (see docs/FONTS.md), so amounts and
// table columns line up while hyphens, letters and punctuation stay normal. No font feature is needed here.
private fun style(family: FontFamily, lineFactor: Float, size: Int, weight: FontWeight): TextStyle = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineFactor).sp,
    letterSpacing = 0.sp
)

/**
 * The scale of the app: amount 32 SemiBold, screen title 22 SemiBold, section title 16 SemiBold,
 * body 15, caption 13 (the caption colour, onSurfaceVariant, is set where it is used).
 */
fun appTypography(language: AppLanguage): Typography {
    val bengali = language == AppLanguage.BENGALI
    val family = appFontFamily(bengaliFirst = bengali)
    val f = if (bengali) 1.45f else 1.4f
    fun s(size: Int, weight: FontWeight) = style(family, f, size, weight)
    return Typography(
        displayLarge = s(32, FontWeight.SemiBold),
        displayMedium = s(32, FontWeight.SemiBold),
        displaySmall = s(32, FontWeight.SemiBold),
        headlineLarge = s(32, FontWeight.SemiBold),   // the amount display
        headlineMedium = s(22, FontWeight.SemiBold),  // the screen title
        headlineSmall = s(22, FontWeight.SemiBold),
        titleLarge = s(22, FontWeight.SemiBold),
        titleMedium = s(16, FontWeight.SemiBold),     // the section title
        titleSmall = s(15, FontWeight.Medium),
        bodyLarge = s(15, FontWeight.Normal),
        bodyMedium = s(15, FontWeight.Normal),
        bodySmall = s(13, FontWeight.Normal),         // the caption
        labelLarge = s(15, FontWeight.Medium),
        labelMedium = s(13, FontWeight.Medium),
        labelSmall = s(12, FontWeight.Medium)
    )
}

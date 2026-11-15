package com.example.dukanhisabnext.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import android.os.Build
import com.example.dukanhisabnext.R
import java.io.File

/**
 * The app's one typeface for both the screens (Compose) and the PDFs, so they always match:
 *  - Inter (res/font/inter_*.ttf, Latin subset) draws Latin letters and digits;
 *  - Noto Sans Bengali (res/font/noto_sans_bengali.ttf, variable) draws Bengali letters and Bengali digits.
 * The bundled Noto Sans Bengali has its Latin letters and digits removed (see docs/FONTS.md); it keeps Bengali,
 * spaces and punctuation, so Bengali screens measure and draw exactly as before.
 * On Android 10+ both fonts are ONE typeface with a fallback chain: in Bengali the first font is Noto Sans Bengali
 * (its line metrics stay as they were) and Inter catches Latin letters and digits; in English the first font is Inter
 * and Noto Sans Bengali catches Bengali. Mixed text is right in both languages.
 * On Android 8-9 a typeface cannot carry a custom fallback: Bengali uses Noto Sans Bengali (Latin from the phone's
 * font), English uses Inter (Bengali letters from the phone's font).
 * Fonts are copied from res/font to a cache folder once, because the font APIs want files.
 */
object AppTypefaces {

    /** The four weights the app uses. */
    val WEIGHTS = intArrayOf(400, 500, 600, 700)

    private val cache = HashMap<String, Typeface>()

    private fun interRes(weight: Int) = when (weight) {
        500 -> R.font.inter_medium
        600 -> R.font.inter_semibold
        700 -> R.font.inter_bold
        else -> R.font.inter_regular
    }

    @SuppressLint("ResourceType") // a font file is a plain file inside the app; lint only expects res/raw here
    private fun extract(context: Context, resId: Int, name: String): File {
        val file = File(context.cacheDir, "app-fonts/$name")
        if (!file.exists() || file.length() == 0L) {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "$name.tmp")
            context.resources.openRawResource(resId).use { input -> temp.outputStream().use { input.copyTo(it) } }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }
        return file
    }

    /** The typeface for [weight] (400/500/600/700). [bengaliFirst]: Bengali screens put Noto Sans Bengali first. */
    @Synchronized
    fun typeface(context: Context, weight: Int, bengaliFirst: Boolean): Typeface {
        val w = WEIGHTS.minByOrNull { kotlin.math.abs(it - weight) } ?: 400
        val key = "$w-$bengaliFirst"
        return cache.getOrPut(key) { build(context.applicationContext, w, bengaliFirst) }
    }

    private fun build(context: Context, w: Int, bengaliFirst: Boolean): Typeface {
        return try {
            val inter = extract(context, interRes(w), "inter_$w.ttf")
            val noto = extract(context, R.font.noto_sans_bengali, "noto_bengali_nolatin-v2.ttf")
            val built: Typeface? = if (Build.VERSION.SDK_INT >= 29) {
                val interFamily = FontFamily.Builder(Font.Builder(inter).setWeight(w).build()).build()
                val notoFamily = FontFamily.Builder(
                    Font.Builder(noto).setWeight(w).setFontVariationSettings("'wght' $w").build()
                ).build()
                val (first, second) = if (bengaliFirst) notoFamily to interFamily else interFamily to notoFamily
                Typeface.CustomFallbackBuilder(first)
                    .addCustomFallback(second)
                    .setSystemFallback("sans-serif")
                    .setStyle(FontStyle(w, FontStyle.FONT_SLANT_UPRIGHT))
                    .build()
            } else if (bengaliFirst) {
                Typeface.Builder(noto).setWeight(w).setFontVariationSettings("'wght' $w").setFallback("sans-serif").build()
            } else {
                Typeface.Builder(inter).setWeight(w).build()
            }
            built ?: Typeface.DEFAULT
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
    }
}

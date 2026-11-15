package com.example.dukanhisabnext

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The typography setup: Inter (Latin) + Noto Sans Bengali (Bengali), the same typeface for the screens and the
 * PDFs, with tabular figures. Reads the resources and the source as text (like ReleaseSetupTest).
 */
class FontSetupTest {

    private val fonts = File("src/main/res/font")
    private fun source(path: String) = File("src/main/java/com/example/dukanhisabnext/$path").readText()

    @Test
    fun allFourInterWeightsAreBundled() {
        for (name in listOf("inter_regular", "inter_medium", "inter_semibold", "inter_bold")) {
            val file = File(fonts, "$name.ttf")
            assertTrue("$name.ttf is missing", file.isFile)
            val bytes = file.readBytes()
            assertTrue("$name looks too small to be a font", bytes.size > 50_000)
            // A TrueType file starts with 0x00010000.
            assertEquals(0, bytes[0].toInt()); assertEquals(1, bytes[1].toInt())
        }
    }

    @Test
    fun theBengaliFontIsStillBundledAndStillVariable() {
        val text = String(File(fonts, "noto_sans_bengali.ttf").readBytes(), Charsets.ISO_8859_1)
        assertTrue(text.contains("fvar") && text.contains("gvar"))
    }

    @Test
    fun theInterLicenseIsKeptInTheDocs() {
        val license = File("../docs/fonts/Inter-OFL.txt").readText()
        assertTrue(license.contains("SIL OPEN FONT LICENSE Version 1.1"))
        assertTrue(File("../docs/FONTS.md").readText().contains("rsms/inter"))
    }

    @Test
    fun theThemeUsesTheAppTypefaceInBothLanguages() {
        val type = source("ui/theme/Type.kt")
        assertFalse("tabular digits come from the font files, not a font feature that would also widen hyphens", type.contains("fontFeatureSettings"))
        assertTrue(type.contains("fun appTypography(language: AppLanguage)"))
        assertTrue("Bengali and English each get their own family", type.contains("bengaliFirst"))
        assertTrue(source("ui/theme/Theme.kt").contains("appTypography(CurrentLanguage.value)"))
    }

    @Test
    fun thePdfsDrawWithTheSameTypefaceAsTheScreens() {
        val pdf = source("pdf/PdfWriter.kt")
        assertTrue(pdf.contains("AppTypefaces.typeface("))
        assertFalse("no font feature: the same files give the same digits", pdf.contains("fontFeatureSettings"))
        assertFalse("the old Bengali-only PDF font is gone", pdf.contains("pdf-font"))
    }
}

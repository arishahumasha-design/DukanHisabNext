package com.example.dukanhisabnext

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launcher icon and splash mark (shop awning + check mark) must exist and stay vector. minSdk is 26, so the
 * adaptive icon in mipmap-anydpi-v26 is used on every supported phone and its vector layers scale to every
 * density (mdpi...xxxhdpi); there are deliberately no per-density PNGs to forget.
 */
class BrandResourcesTest {

    private val res = File("src/main/res")
    private fun text(path: String) = File(res, path).also { assertTrue("$path is missing", it.isFile) }.readText()

    @Test
    fun theAdaptiveIconHasBackgroundForegroundAndMonochrome() {
        for (name in listOf("ic_launcher.xml", "ic_launcher_round.xml")) {
            val xml = text("mipmap-anydpi-v26/$name")
            assertTrue(xml.contains("@drawable/ic_launcher_background"))
            assertTrue(xml.contains("@drawable/ic_launcher_foreground"))
            assertTrue(xml.contains("@drawable/ic_launcher_monochrome"))
        }
    }

    @Test
    fun noPerDensityRasterIconsHideBehindTheVectorOne() {
        val raster = res.listFiles { f -> f.isDirectory && f.name.startsWith("mipmap-") && f.name != "mipmap-anydpi-v26" }
            .orEmpty().flatMap { it.listFiles().orEmpty().toList() }
        assertEquals("a stray PNG icon would override the vector icon for its density: $raster", emptyList<File>(), raster)
    }

    @Test
    fun backgroundIsTheSolidAppGreen() {
        val xml = text("drawable/ic_launcher_background.xml")
        assertTrue(xml.contains("#FF0F7B4F"))
        assertFalse("no gradient any more", xml.contains("gradient"))
    }

    @Test
    fun foregroundAndMonochromeDrawTheAwningAndTheCheckMark() {
        for (name in listOf("ic_launcher_foreground.xml", "ic_launcher_monochrome.xml")) {
            val xml = text("drawable/$name")
            assertTrue("$name: awning", xml.contains("M92,176"))
            assertTrue("$name: check mark", xml.contains("M200,330L242,372L318,284"))
            assertTrue("$name: safe-zone scale", xml.contains("android:scaleX=\"0.17\""))
        }
        assertTrue(text("drawable/ic_launcher_foreground.xml").contains("#FFF2B84B"))
    }

    @Test
    fun theOldTakaNotebookMarkIsGone() {
        for (name in listOf("ic_launcher_foreground.xml", "ic_launcher_monochrome.xml")) {
            assertFalse(text("drawable/$name").contains("M58.05,72.2"))
        }
    }

    @Test
    fun theSplashUsesTheNewMarkOnTheLauncherGreen() {
        for (dir in listOf("values-v31", "values-night-v31")) {
            assertTrue(text("$dir/themes.xml").contains("@drawable/ic_launcher_foreground"))
        }
        assertTrue(text("values/colors.xml").contains("name=\"splash_background\">#FF0F7B4F"))
    }

    @Test
    fun thePlayStoreIconAndItsSourceAreInDocs() {
        val png = File("../docs/logo/play-store-512.png")
        assertTrue(png.isFile)
        val bytes = png.readBytes()
        fun int(o: Int) = ((bytes[o].toInt() and 255) shl 24) or ((bytes[o + 1].toInt() and 255) shl 16) or
            ((bytes[o + 2].toInt() and 255) shl 8) or (bytes[o + 3].toInt() and 255)
        assertEquals(512, int(16))
        assertEquals(512, int(20))
        assertTrue(File("../docs/logo/shop-logo.svg").isFile)
    }
}

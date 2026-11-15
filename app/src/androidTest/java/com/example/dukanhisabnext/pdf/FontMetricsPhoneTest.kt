package com.example.dukanhisabnext.pdf

import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dukanhisabnext.data.DatabaseTestBase
import com.example.dukanhisabnext.ui.theme.AppTypefaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The digits of the app's typeface are tabular (every digit is as wide as every other, so amounts line up) while
 * hyphens and letters stay proportional: the bundled Inter files draw 0-9 tabular by default.
 */
@RunWith(AndroidJUnit4::class)
class FontMetricsPhoneTest : DatabaseTestBase() {

    private fun paint(weight: Int, bengaliFirst: Boolean) = Paint().apply {
        textSize = 100f
        typeface = AppTypefaces.typeface(testContext, weight, bengaliFirst)
    }

    @Test
    fun everyDigitIsTheSameWidthInEveryWeightAndBothLanguages() {
        for (bengaliFirst in listOf(false, true)) {
            for (weight in AppTypefaces.WEIGHTS) {
                val p = paint(weight, bengaliFirst)
                val widths = "0123456789".map { p.measureText(it.toString()) }
                assertEquals("weight $weight bengaliFirst=$bengaliFirst: $widths", 1, widths.map { Math.round(it * 10) }.toSet().size)
            }
        }
    }

    @Test
    fun hyphensAndLettersAreNotTabular() {
        for (weight in AppTypefaces.WEIGHTS) {
            val p = paint(weight, false)
            val digit = p.measureText("0")
            assertTrue("hyphen (${p.measureText("-")}) is clearly narrower than a digit ($digit)", p.measureText("-") < digit * 0.85f)
            assertTrue("i is narrower than m", p.measureText("i") < p.measureText("m") * 0.6f)
            assertTrue("1111 is as wide as 0000 (tabular)", Math.abs(p.measureText("1111") - p.measureText("0000")) < 0.5f)
        }
    }
}

package com.example.dukanhisabnext.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchTransitionTest {

    @Test
    fun theWholeTransitionIsNeverLongerThanSixTenthsOfASecond() {
        assertTrue(SwitchTransition.TOTAL_MS <= 600)
        assertEquals(SwitchTransition.FADE_OUT_MS + SwitchTransition.SPINNER_MS + SwitchTransition.FADE_IN_MS, SwitchTransition.TOTAL_MS)
        assertTrue("the spinner shows for about 400 ms", SwitchTransition.SPINNER_MS in 350..450)
    }

    @Test
    fun theNewScreenFadesInAtAFixedTimeAfterTheTap() {
        SwitchTransition.begin(clickedAt = 10_000)
        val fadeInAt = SwitchTransition.takeArrival(now = 10_300)
        assertEquals(10_000L + SwitchTransition.FADE_OUT_MS + SwitchTransition.SPINNER_MS, fadeInAt)
    }

    @Test
    fun aTransitionIsPickedUpOnlyOnce() {
        SwitchTransition.begin(clickedAt = 20_000)
        assertTrue(SwitchTransition.takeArrival(now = 20_200) != null)
        assertNull(SwitchTransition.takeArrival(now = 20_210))
    }

    @Test
    fun aNormalStartHasNoTransitionAndAStaleOneIsForgotten() {
        assertNull(SwitchTransition.takeArrival(now = 5))
        SwitchTransition.begin(clickedAt = 30_000)
        assertNull("nobody picked it up for 10 s", SwitchTransition.takeArrival(now = 40_000))
    }

    @Test
    fun theSpinnerTimeLeftNeverGoesNegative() {
        assertEquals(190L, SwitchTransition.holdRemaining(fadeInAt = 1_000, now = 810))
        assertEquals(0L, SwitchTransition.holdRemaining(fadeInAt = 1_000, now = 1_500))
    }

    @Test
    fun onlyTheLanguageAndCurrencyChangesUseIt() {
        val activity = File("src/main/java/com/example/dukanhisabnext/MainActivity.kt").readText()
        assertEquals("exactly the two switches", 2, Regex("withSwitchTransition \\{").findAll(activity).count())
        assertTrue("remove-animations skips it", File("src/main/java/com/example/dukanhisabnext/ui/components/SwitchTransition.kt")
            .readText().contains("ANIMATOR_DURATION_SCALE"))
    }
}

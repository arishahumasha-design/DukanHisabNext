package com.example.dukanhisabnext.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The short transition when the language or the currency changes (and only then). The screen is recreated for
 * the change, which would be a visible flash, so: the old screen fades out under a plain background with a small
 * spinner (FADE_OUT_MS), the change is applied behind it, the spinner stays until SPINNER_MS after the fade-out,
 * and the new screen fades in (FADE_IN_MS). Everything together is at most TOTAL_MS (0.6 s). It touches no data: the
 * change itself is exactly what it was, only done a moment later. With the system "remove animations" setting the
 * change is applied at once, as before.
 */
object SwitchTransition {
    const val FADE_OUT_MS = 90
    const val SPINNER_MS = 400
    const val FADE_IN_MS = 100
    const val TOTAL_MS = FADE_OUT_MS + SPINNER_MS + FADE_IN_MS

    /** A transition that is not picked up by a new screen within this time is forgotten. */
    private const val STALE_MS = 3_000L

    private var fadeInAt = 0L

    /** The system's "remove animations" (animation scale 0) means: no transition. */
    fun reducedMotion(context: Context): Boolean = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (e: Exception) {
        false
    }

    /** The user chose a language or currency at [clickedAt] (uptimeMillis): the new screen fades in at a fixed time. */
    @Synchronized
    fun begin(clickedAt: Long) {
        fadeInAt = clickedAt + FADE_OUT_MS + SPINNER_MS
    }

    /**
     * Called once when a screen starts. Null: no transition is running. Otherwise the time (uptimeMillis) at which
     * the new screen fades in; the screen waits until then (at once if the recreate already took longer).
     */
    @Synchronized
    fun takeArrival(now: Long): Long? {
        val at = fadeInAt
        fadeInAt = 0L
        return if (at != 0L && now <= at + STALE_MS) at else null
    }

    fun holdRemaining(fadeInAt: Long, now: Long): Long = (fadeInAt - now).coerceAtLeast(0L)
}

/**
 * The cover for [SwitchTransition]. [covering]: the old screen is being replaced, fade the cover in. [arrivalFadeInAt]:
 * this screen was created by a switch, so it starts covered and fades in at that time (uptimeMillis).
 */
@Composable
fun SwitchOverlay(covering: Boolean, arrivalFadeInAt: Long?) {
    val alpha = remember { Animatable(if (arrivalFadeInAt != null) 1f else 0f) }
    LaunchedEffect(covering) {
        if (covering) alpha.animateTo(1f, tween(SwitchTransition.FADE_OUT_MS))
    }
    LaunchedEffect(arrivalFadeInAt) {
        if (arrivalFadeInAt != null) {
            delay(SwitchTransition.holdRemaining(arrivalFadeInAt, android.os.SystemClock.uptimeMillis()))
            alpha.animateTo(0f, tween(SwitchTransition.FADE_IN_MS))
        }
    }
    if (alpha.value > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha.value }
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) { detectTapGestures { } }, // no taps reach the screen underneath
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

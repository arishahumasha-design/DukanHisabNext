package com.example.dukanhisabnext.ui.components

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.ViewModelStore
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
import com.example.dukanhisabnext.util.AppLanguage
import kotlinx.coroutines.delay

/**
 * The short transition when the language or the currency changes (and only then). MainActivity no longer recreates
 * itself for it (a recreate leaves the screen black between the old and the new window). Instead:
 * the old screen fades to a plain background with a small spinner (FADE_OUT_MS), the change is applied, a FRESH
 * MainActivity is started with a cross-fade and the old one finishes (the old window stays visible until the new
 * one has drawn, so there is no empty frame), the spinner stays until SPINNER_MS after the fade-out, and the
 * new screen fades in (FADE_IN_MS). The new activity gets the old one's saved state (back stack, form fields,
 * scroll positions: [handOff]) so the user lands on the same screen. Everything together is about
 * TOTAL_MS. It touches no data: the change itself is exactly what it was. With the system "remove animations"
 * setting there is no cover and no fade: the fresh screen simply replaces the old one.
 */
object SwitchTransition {
    const val FADE_OUT_MS = 90
    const val SPINNER_MS = 400
    const val FADE_IN_MS = 100
    const val TOTAL_MS = FADE_OUT_MS + SPINNER_MS + FADE_IN_MS

    /** A transition that is not picked up by a new screen within this time is forgotten. */
    private const val STALE_MS = 3_000L

    private var fadeInAt = 0L
    private var handedState: Bundle? = null
    private var handedLanguage: AppLanguage? = null
    private var handedStore: ViewModelStore? = null
    private val storeAdopted = java.util.WeakHashMap<Any, ViewModelStore?>()

    /** The old screen's ViewModelStore goes to the fresh screen too: what the ViewModels hold in memory (a search text) stays, as it did for a recreate. */
    @Synchronized
    fun handOffViewModelStore(store: ViewModelStore) {
        handedStore = store
    }

    /**
     * The ViewModelStore [owner] (a screen) should use instead of its own: the handed-over one for the first
     * screen that asks after a switch, null for every other screen (and for the old screen itself, which asked at
     * its own start). Always gives the same answer for the same owner.
     */
    @Synchronized
    fun adoptViewModelStore(owner: Any): ViewModelStore? {
        if (storeAdopted.containsKey(owner)) return storeAdopted[owner]
        val store = handedStore
        handedStore = null
        storeAdopted[owner] = store
        return store
    }

    /**
     * The old screen's saved state ([Bundle] from onSaveInstanceState) and the language to start in, for the fresh
     * screen. Kept in memory (same process), so its size does not matter.
     */
    @Synchronized
    fun handOff(state: Bundle?, language: AppLanguage?) {
        handedState = state
        handedLanguage = language
    }

    /** The language the fresh screen must start in (before it has a context), or null when this is not a switch. */
    @Synchronized
    fun pendingLanguage(): AppLanguage? = handedLanguage

    /** The saved state for the fresh screen's onCreate; handed over once. */
    @Synchronized
    fun takeState(): Bundle? {
        val state = handedState
        handedState = null
        handedLanguage = null
        return state
    }

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

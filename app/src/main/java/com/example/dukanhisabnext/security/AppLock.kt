package com.example.dukanhisabnext.security

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * The optional app lock (Settings → "Lock the app", off by default). It uses the phone's OWN screen lock
 * (KeyguardManager.createConfirmDeviceCredentialIntent, asked by MainActivity): the app stores no PIN and no secret.
 *
 * Kept in its own small preference file, app_lock.xml: the switch, the delay, and for a few seconds the restart note.
 * The backup rules include only named files, so this file is never in Android's backup, never in a backup zip and
 * never restored. Whether the app is unlocked lives only in memory (one answer per start of the app).
 */
class AppLock(context: Context, private val now: () -> Long = SystemClock::elapsedRealtime) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val keyguard: KeyguardManager? = context.getSystemService(KeyguardManager::class.java)

    /** The Settings switch. */
    var enabled by mutableStateOf(prefs.getBoolean(KEY_ON, false))
        private set

    var delay by mutableStateOf(LockDelay.fromKey(prefs.getString(KEY_DELAY, null)))
        private set

    /** True while the lock screen must cover the app. */
    var locked by mutableStateOf(false)
        private set

    /** Goes up each time the app locks, so the phone's lock is asked by itself once per lock (not again after a cancel). */
    var lockCount by mutableIntStateOf(0)
        private set

    /** The lock count the phone's lock was last asked for by itself. */
    var promptedFor = -1

    private var startedScreens = 0
    private var backgroundAt: Long? = null
    private var externalLaunchAt: Long? = null

    init {
        val token = prefs.getLong(KEY_RESTART, -1L).takeIf { it >= 0 }
        if (token != null) prefs.edit { remove(KEY_RESTART) }
        if (enabled && !deviceSecure()) turnOff()
        if (lockAtProcessStart(enabled, deviceSecure(), token, now())) lock()
    }

    /** The phone has a PIN, pattern or password. */
    fun deviceSecure(): Boolean = keyguard?.isDeviceSecure == true

    private fun lock() {
        locked = true
        lockCount += 1
    }

    /** MainActivity.onStart. Also notices a phone lock that was removed: then the app lock is simply turned off. */
    fun onScreenStart() {
        startedScreens += 1
        if (enabled && !deviceSecure()) turnOff()
        if (!locked && lockOnReturn(enabled, deviceSecure(), backgroundAt, externalLaunchAt, now(), delay)) lock()
        backgroundAt = null
        externalLaunchAt = null
    }

    /** MainActivity.onStop. The app is in the background only when no screen of it is started any more. */
    fun onScreenStop(changingConfigurations: Boolean) {
        startedScreens = (startedScreens - 1).coerceAtLeast(0)
        if (startedScreens == 0 && !changingConfigurations) backgroundAt = now()
    }

    /** The app itself opened a system screen (file picker, share sheet, PDF viewer, contact picker). */
    fun noteExternalLaunch() {
        externalLaunchAt = now()
    }

    /** The phone's lock was confirmed. */
    fun unlock() {
        locked = false
    }

    /** Turned on only after the phone's lock was confirmed once (MainActivity), so it is known to work. */
    fun turnOn() {
        if (!deviceSecure()) return
        prefs.edit { putBoolean(KEY_ON, true) }
        enabled = true
        locked = false
    }

    fun turnOff() {
        prefs.edit { putBoolean(KEY_ON, false) }
        enabled = false
        locked = false
    }

    fun chooseDelay(value: LockDelay) {
        prefs.edit { putString(KEY_DELAY, value.key) }
        delay = value
    }

    /** Just before the app restarts itself (shop switch, restore): an unlocked app stays unlocked after the restart. */
    fun beforeRestart() { // written at once (commit): the process ends right after
        if (lockActive(enabled, deviceSecure()) && !locked) prefs.edit(commit = true) { putLong(KEY_RESTART, now()) }
    }

    companion object {
        const val FILE = "app_lock"
        private const val KEY_ON = "lockOn"
        private const val KEY_DELAY = "lockDelay"
        private const val KEY_RESTART = "restartUnlockedAt"
    }
}

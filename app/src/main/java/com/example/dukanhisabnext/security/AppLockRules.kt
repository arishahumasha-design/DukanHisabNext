package com.example.dukanhisabnext.security

// The optional app lock (Settings, off by default): the plain decisions, so they can be tested on the PC.
// All times are SystemClock.elapsedRealtime() (milliseconds since the phone started; changing the phone's clock does
// not move it, and it keeps counting across an app restart).

/** How long the app may be away (in the background) before it asks for the phone's screen lock again. */
enum class LockDelay(val millis: Long, val key: String) {
    IMMEDIATELY(0L, "now"),
    ONE_MINUTE(60_000L, "1m"),
    FIVE_MINUTES(300_000L, "5m");

    companion object {
        val DEFAULT = ONE_MINUTE
        fun fromKey(key: String?): LockDelay = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * A trip to a system screen the app itself opened (the file picker, the share sheet, a PDF viewer, the contact picker)
 * does not lock the app when the user is back within this time, whatever [LockDelay] says.
 */
const val EXTERNAL_GRACE_MILLIS: Long = 5 * 60_000L

/** The app counts as having left FOR its own launch only when it went to the background this soon after it. */
const val EXTERNAL_LEAVE_WINDOW_MILLIS: Long = 3_000L

/** A restart made by the app itself (shop switch, restore) keeps an unlocked app unlocked when it starts again this soon. */
const val RESTART_TOKEN_MILLIS: Long = 20_000L

/**
 * The lock only works when the phone has a screen lock (PIN, pattern or password). Without one the app never locks:
 * the user must never be locked out because the phone's own lock was removed.
 */
fun lockActive(switchOn: Boolean, deviceSecure: Boolean): Boolean = switchOn && deviceSecure

/**
 * A new start of the app (a new process): locked, unless the app restarted itself a moment ago while it was unlocked
 * ([restartTokenAt], written just before that restart).
 */
fun lockAtProcessStart(switchOn: Boolean, deviceSecure: Boolean, restartTokenAt: Long?, now: Long): Boolean {
    if (!lockActive(switchOn, deviceSecure)) return false
    val freshRestart = restartTokenAt != null && now >= restartTokenAt && now - restartTokenAt <= RESTART_TOKEN_MILLIS
    return !freshRestart
}

/**
 * The app comes back to the front. [backgroundAt] = when it went to the background (null: it never did, for example
 * a language switch or a configuration change, which only replace the screen). [externalLaunchAt] = when the app last
 * opened a system screen itself. Locks when it was away longer than [delay] ("immediately": any time away), except
 * after its own trip to a system screen within [EXTERNAL_GRACE_MILLIS].
 */
fun lockOnReturn(
    switchOn: Boolean,
    deviceSecure: Boolean,
    backgroundAt: Long?,
    externalLaunchAt: Long?,
    now: Long,
    delay: LockDelay
): Boolean {
    if (!lockActive(switchOn, deviceSecure)) return false
    if (backgroundAt == null) return false
    val away = now - backgroundAt
    if (away < 0) return true // cannot happen with elapsedRealtime; stay on the safe side
    val leftForOwnLaunch = externalLaunchAt != null && backgroundAt >= externalLaunchAt &&
        backgroundAt - externalLaunchAt <= EXTERNAL_LEAVE_WINDOW_MILLIS
    if (leftForOwnLaunch && away <= maxOf(delay.millis, EXTERNAL_GRACE_MILLIS)) return false
    return delay == LockDelay.IMMEDIATELY || away > delay.millis
}

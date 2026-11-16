package com.example.dukanhisabnext.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockRulesTest {
    private val t0 = 1_000_000L
    private val sec = 1_000L
    private val min = 60_000L

    private fun back(after: Long, delay: LockDelay, externalAt: Long? = null, on: Boolean = true, secure: Boolean = true) =
        lockOnReturn(on, secure, backgroundAt = t0, externalLaunchAt = externalAt, now = t0 + after, delay = delay)

    // ---- off by default, and never without a phone lock ----

    @Test
    fun theDefaultDelayIsOneMinuteAndUnknownKeysFallBackToIt() {
        assertEquals(LockDelay.ONE_MINUTE, LockDelay.DEFAULT)
        assertEquals(LockDelay.ONE_MINUTE, LockDelay.fromKey(null))
        assertEquals(LockDelay.ONE_MINUTE, LockDelay.fromKey("rubbish"))
        assertEquals(LockDelay.FIVE_MINUTES, LockDelay.fromKey("5m"))
        assertEquals(LockDelay.IMMEDIATELY, LockDelay.fromKey("now"))
    }

    @Test
    fun withTheSwitchOffNothingEverLocks() {
        assertFalse(lockAtProcessStart(switchOn = false, deviceSecure = true, restartTokenAt = null, now = t0))
        assertFalse(back(10 * min, LockDelay.IMMEDIATELY, on = false))
    }

    @Test
    fun aPhoneWithoutAScreenLockNeverLocksTheUserOut() {
        assertFalse(lockActive(switchOn = true, deviceSecure = false))
        assertFalse(lockAtProcessStart(switchOn = true, deviceSecure = false, restartTokenAt = null, now = t0))
        assertFalse(back(60 * min, LockDelay.IMMEDIATELY, secure = false))
    }

    // ---- cold start ----

    @Test
    fun aColdStartLocks() {
        assertTrue(lockAtProcessStart(switchOn = true, deviceSecure = true, restartTokenAt = null, now = t0))
    }

    @Test
    fun theAppsOwnRestartAMomentAgoDoesNotAskAgain() {
        assertFalse(lockAtProcessStart(true, true, restartTokenAt = t0, now = t0 + 3 * sec))
        assertFalse(lockAtProcessStart(true, true, restartTokenAt = t0, now = t0 + RESTART_TOKEN_MILLIS))
    }

    @Test
    fun anOldOrStrangeRestartNoteStillLocks() {
        assertTrue(lockAtProcessStart(true, true, restartTokenAt = t0, now = t0 + RESTART_TOKEN_MILLIS + 1))
        // elapsedRealtime starts again at 0 after a reboot: a note "from the future" is not trusted
        assertTrue(lockAtProcessStart(true, true, restartTokenAt = t0, now = t0 - 5 * sec))
    }

    // ---- back from the background ----

    @Test
    fun neverInTheBackgroundMeansNoLock() {
        // language switch, rotation or other configuration change: the screen is replaced, the app never left
        assertFalse(lockOnReturn(true, true, backgroundAt = null, externalLaunchAt = null, now = t0, delay = LockDelay.IMMEDIATELY))
    }

    @Test
    fun oneMinuteLocksOnlyAfterMoreThanOneMinute() {
        assertFalse(back(59 * sec, LockDelay.ONE_MINUTE))
        assertFalse(back(1 * min, LockDelay.ONE_MINUTE))
        assertTrue(back(1 * min + 1, LockDelay.ONE_MINUTE))
    }

    @Test
    fun fiveMinutesLocksOnlyAfterMoreThanFiveMinutes() {
        assertFalse(back(4 * min, LockDelay.FIVE_MINUTES))
        assertTrue(back(5 * min + 1, LockDelay.FIVE_MINUTES))
    }

    @Test
    fun immediatelyLocksAfterAnyTimeAway() {
        assertTrue(back(0, LockDelay.IMMEDIATELY))
        assertTrue(back(2 * sec, LockDelay.IMMEDIATELY))
    }

    // ---- the app's own trips to the file picker, share sheet, PDF viewer, contact picker ----

    @Test
    fun aPickerOrShareRoundTripWithinTheGraceTimeDoesNotLock() {
        val launchedAt = t0 - 500 // the app opened the picker, and went to the background half a second later
        assertFalse(back(30 * sec, LockDelay.IMMEDIATELY, externalAt = launchedAt))
        assertFalse(back(3 * min, LockDelay.ONE_MINUTE, externalAt = launchedAt))
        assertFalse(back(EXTERNAL_GRACE_MILLIS, LockDelay.ONE_MINUTE, externalAt = launchedAt))
    }

    @Test
    fun aRoundTripLongerThanTheGraceTimeLocks() {
        val launchedAt = t0 - 500
        assertTrue(back(EXTERNAL_GRACE_MILLIS + 1, LockDelay.IMMEDIATELY, externalAt = launchedAt))
        assertTrue(back(EXTERNAL_GRACE_MILLIS + 1, LockDelay.FIVE_MINUTES, externalAt = launchedAt))
    }

    @Test
    fun leavingLongAfterOpeningAPickerIsAnOrdinaryLeave() {
        // the picker was opened, the user came back, and much later pressed Home: the normal delay applies
        assertTrue(back(2 * min, LockDelay.ONE_MINUTE, externalAt = t0 - EXTERNAL_LEAVE_WINDOW_MILLIS - 1))
        assertTrue(back(1 * sec, LockDelay.IMMEDIATELY, externalAt = t0 - 10 * min))
        // a launch noted after the leave cannot explain it
        assertTrue(back(1 * sec, LockDelay.IMMEDIATELY, externalAt = t0 + 100))
    }

    @Test
    fun aClockGoingBackwardsLocksToBeSafe() {
        assertTrue(lockOnReturn(true, true, backgroundAt = t0, externalLaunchAt = null, now = t0 - 1, delay = LockDelay.FIVE_MINUTES))
    }

    // ---- the lock's file is never backed up ----

    @Test
    fun theLockFileIsNotInAnyBackupRule() {
        for (path in listOf("src/main/res/xml/backup_rules.xml", "src/main/res/xml/data_extraction_rules.xml")) {
            val xml = File(path).readText()
            assertFalse(path, xml.contains(AppLock.FILE))
            assertTrue(path, Regex("""<include domain="sharedpref" path="([^"]+)"""").findAll(xml).all { it.groupValues[1] == "backup_prefs.xml" })
        }
    }

    @Test
    fun theLockTextsExistInBothLanguages() {
        val keys = listOf(
            "lock_switch", "lock_switch_hint", "lock_screenshot_note", "lock_no_device_lock", "lock_delay_title",
            "lock_delay_immediately", "lock_delay_1min", "lock_delay_5min", "lock_screen_title", "lock_screen_hint",
            "lock_unlock_button", "lock_prompt_title", "lock_prompt_text"
        )
        for (file in listOf("src/main/res/values/strings.xml", "src/main/res/values-en/strings.xml")) {
            val xml = File(file).readText()
            for (k in keys) assertTrue("$k in $file", xml.contains("name=\"$k\""))
        }
    }
}

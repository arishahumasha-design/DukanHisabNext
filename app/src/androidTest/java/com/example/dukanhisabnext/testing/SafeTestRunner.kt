package com.example.dukanhisabnext.testing

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/**
 * The test runner of this project (build.gradle.kts: testInstrumentationRunner). It is the normal
 * AndroidJUnitRunner plus ONE check before anything else happens: on a device that is not an emulator
 * the whole run stops at once with "Do not run phone tests on a real phone" (a crash of the
 * instrumentation, so `am instrument` and Gradle both report a failure and no test starts).
 *
 * For checking the guard itself, `-e simulateRealPhone true` makes the guard treat the emulator as a
 * real phone (it can only make the guard stricter, never weaker).
 */
class SafeTestRunner : AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle?) {
        val pretendRealPhone = arguments?.getString(SIMULATE_REAL_PHONE) == "true"
        if (pretendRealPhone || !DeviceGuard.isEmulator()) {
            throw IllegalStateException(DeviceGuard.MESSAGE)
        }
        super.onCreate(arguments)
    }

    private companion object {
        const val SIMULATE_REAL_PHONE = "simulateRealPhone"
    }
}

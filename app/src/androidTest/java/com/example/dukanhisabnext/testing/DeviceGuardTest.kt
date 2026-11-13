package com.example.dukanhisabnext.testing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "never on a real phone" guard: the decision function with the build values of real phones and of
 * emulators, and the facts that this run really goes through the guarded runner on an emulator.
 */
@RunWith(AndroidJUnit4::class)
class DeviceGuardTest {

    private fun emulator(fingerprint: String?, hardware: String?, product: String?, model: String?, qemu: String?) =
        DeviceGuard.looksLikeEmulator(fingerprint, hardware, product, model, qemu)

    @Test
    fun anAndroidStudioEmulatorIsRecognised() {
        // The current Google emulator image (Medium Phone), the older generic/goldfish ones and the qemu property alone.
        assertTrue(emulator("google/sdk_gphone64_x86_64/emu64xa:15/AE3A.240806.043/12260840:userdebug/dev-keys", "ranchu", "sdk_gphone64_x86_64", "sdk_gphone64_x86_64", "1"))
        assertTrue(emulator("generic/sdk/generic:8.1.0/OSM1.180201.037/6806722:userdebug/test-keys", "goldfish", "sdk", "Android SDK built for x86", "1"))
        assertTrue(emulator("some/unknown/build", "unknown", "unknown", "unknown", "1"))
        assertTrue(emulator("generic_x86/vbox86p/vbox86p:7.1/NMF26Q/user:userdebug/test-keys", "vbox86", "vbox86p", "Custom Phone", null))
    }

    @Test
    fun realPhonesAreRefused() {
        // Pixel
        assertFalse(emulator("google/husky/husky:14/UD1A.231105.004/11010374:user/release-keys", "husky", "husky", "Pixel 8 Pro", ""))
        // Samsung
        assertFalse(emulator("samsung/a52sxqxx/a52s:13/TP1A.220624.014/A528BXXS5EWH1:user/release-keys", "qcom", "a52sxqxx", "SM-A528B", ""))
        // Xiaomi / Redmi
        assertFalse(emulator("Redmi/lancelot_global/lancelot:11/RP1A.200720.011/V12.5.5.0.RJCMIXM:user/release-keys", "mt6768", "lancelot_global", "Redmi 9", ""))
        // Realme / Oppo, Infinix, Walton: no sign of an emulator
        assertFalse(emulator("realme/RMX3511EEA/RE54E4L1:13/TP1A.220905.001/R.1:user/release-keys", "mt6877", "RMX3511EEA", "RMX3511", null))
        assertFalse(emulator("Infinix/X6511-GL/Infinix-X6511:12/SP1A.210812.016/221101V1:user/release-keys", "mt6785", "X6511-GL", "Infinix X6511", ""))
        assertFalse(emulator("WALTON/Primo_H10/Primo_H10:11/RP1A.200720.011/1:user/release-keys", "mt6762", "Primo_H10", "Primo H10", ""))
        // Missing values are not an emulator.
        assertFalse(emulator(null, null, null, null, null))
    }

    @Test
    fun thisRunGoesThroughTheGuardedRunnerAndRunsOnAnEmulator() {
        assertTrue(
            "the project's runner must be SafeTestRunner",
            InstrumentationRegistry.getInstrumentation() is SafeTestRunner
        )
        assertTrue("these tests may only run on an emulator", DeviceGuard.isEmulator())
    }

    @Test
    fun theMessageIsClear() {
        assertTrue(DeviceGuard.MESSAGE.startsWith("Do not run phone tests on a real phone"))
        assertEquals(true, DeviceGuard.MESSAGE.contains("emulator"))
    }
}

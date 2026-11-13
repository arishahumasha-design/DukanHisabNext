package com.example.dukanhisabnext.testing

import android.os.Build

/**
 * Decides whether this device is an emulator. The phone tests may run ONLY on an emulator: they are
 * built never to touch the app's real data, but a real phone holds the shopkeeper's real shop, so the
 * whole run refuses to start there (SafeTestRunner).
 *
 * The decision is a plain function of the device's build values, so DeviceGuardTest can check it with
 * the values of real phones and of emulators.
 */
object DeviceGuard {

    const val MESSAGE =
        "Do not run phone tests on a real phone. Start an emulator and run them with: adb -s emulator-5554 ..."

    /** True when the values describe an emulator (any one clear sign is enough). */
    fun looksLikeEmulator(
        fingerprint: String?,
        hardware: String?,
        product: String?,
        model: String?,
        qemuProperty: String?
    ): Boolean {
        val f = fingerprint.orEmpty().lowercase()
        val h = hardware.orEmpty().lowercase()
        val p = product.orEmpty().lowercase()
        val m = model.orEmpty().lowercase()
        return qemuProperty == "1" ||
            h.contains("goldfish") || h.contains("ranchu") ||
            f.startsWith("generic") || f.contains("/generic/") || f.contains("emulator") || f.contains("sdk_gphone") ||
            p.contains("sdk_gphone") || p.contains("emulator") || p == "sdk" || p.startsWith("sdk_") ||
            m.contains("android sdk built for") || m.contains("emulator")
    }

    /** The system property ro.kernel.qemu ("1" on an emulator), read the way Android apps can. */
    fun qemuProperty(): String? = try {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java, String::class.java)
            .invoke(null, "ro.kernel.qemu", "") as String
    } catch (e: Exception) {
        null // not readable: the other signs decide
    }

    /** This device. */
    fun isEmulator(): Boolean = looksLikeEmulator(
        fingerprint = Build.FINGERPRINT,
        hardware = Build.HARDWARE,
        product = Build.PRODUCT,
        model = Build.MODEL,
        qemuProperty = qemuProperty()
    )
}

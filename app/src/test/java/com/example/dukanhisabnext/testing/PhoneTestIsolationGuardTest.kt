package com.example.dukanhisabnext.testing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-scan guard (PC): the phone (instrumented) tests must never reach the app's REAL data. They
 * get their files, caches and databases only from an IsolatedContext (a temporary folder of the TEST
 * package), and the whole run is guarded against real phones.
 *
 * This test reads the source of src/androidTest and fails the moment someone writes a phone test that:
 *  - takes the app's context directly (targetContext, ApplicationProvider...) instead of passing it to
 *    IsolatedContext.create(...);
 *  - names the app's singletons, real database file, real settings or real cart/journal files.
 * The few files that must look at the real app are listed below, with the reason.
 */
class PhoneTestIsolationGuardTest {

    private val root = File("src/androidTest/java/com/example/dukanhisabnext")

    /** Phone test sources that are ALLOWED to look at the real app, and why. */
    private val allowed = mapOf(
        "testing/IsolatedContext.kt" to "defines the isolation (wraps the app context and redirects every folder)",
        "testing/RealDataIsolationTest.kt" to "the proof: reads the real data (read-only) before and after a sample of tests",
        "testing/SafeTestRunner.kt" to "the guarded runner",
        "testing/DeviceGuard.kt" to "the real-phone guard",
        "testing/DeviceGuardTest.kt" to "tests the guard",
        "ExampleInstrumentedTest.kt" to "only reads the package name of the target app"
    )

    private fun sources(): List<Pair<String, String>> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(root).path.replace('\\', '/') to it.readText() }
            .toList()

    @Test
    fun theScanSeesThePhoneTests() {
        val names = sources().map { it.first }
        assertTrue(names.toString(), "backup/BackupEngineTest.kt" in names)
        assertTrue(names.toString(), "data/MigrationTest.kt" in names)
        assertTrue(names.toString(), "data/DatabaseTestBase.kt" in names)
        assertTrue(names.toString(), names.size >= 25)
        for (file in allowed.keys) assertTrue("allowed file $file must exist", file in names)
    }

    @Test
    fun noPhoneTestTakesTheAppContextExceptIntoAnIsolatedContext() {
        for ((name, text) in sources()) {
            if (name in allowed) continue
            for ((index, line) in text.lines().withIndex()) {
                val takesRealContext = line.contains("targetContext") ||
                    line.contains("getApplicationContext()") || line.contains("ApplicationProvider")
                if (!takesRealContext) continue
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("import ")) continue
                assertTrue(
                    "$name:${index + 1} uses the app's real context directly. Pass it to IsolatedContext.create(...) and use that:\n$line",
                    line.contains("IsolatedContext.create(")
                )
            }
        }
    }

    @Test
    fun noPhoneTestNamesTheAppsSingletonsOrRealFiles() {
        val forbidden = listOf(
            "DokanHisabApp", "AppDatabase.getInstance", "AppDatabase.FILE_NAME", "dokan_hisab",
            "getSharedPreferences", "BACKUP_PREFS_NAME", "BackupPrefs(", "backup_prefs", "cartStore", "backupManager",
            "backupEngine", "saleRepository", "shop_prefs", "restore-safety"
        )
        for ((name, text) in sources()) {
            if (name in allowed) continue
            val code = text.lines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") || it.trim().startsWith("/*") }
            for (word in forbidden) {
                val hit = code.indexOfFirst { it.contains(word) }
                assertTrue("$name:${hit + 1} must not use \"$word\" (it is the app's real data or a real singleton)", hit < 0)
            }
        }
    }

    @Test
    fun everyTestThatBuildsItsOwnContextCleansItUp() {
        for ((name, text) in sources()) {
            if (name in allowed) continue
            if (text.contains("IsolatedContext.create(")) {
                assertTrue("$name creates an IsolatedContext but never calls cleanUp()", text.contains("cleanUp()"))
            }
        }
    }

    @Test
    fun theRunnerIsTheGuardedOne() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("testInstrumentationRunner = \"com.example.dukanhisabnext.testing.SafeTestRunner\""))
        assertFalse(gradle.contains("testInstrumentationRunner = \"androidx.test.runner.AndroidJUnitRunner\""))

        val runner = File(root, "testing/SafeTestRunner.kt").readText()
        val check = runner.indexOf("DeviceGuard.isEmulator()")
        val start = runner.indexOf("super.onCreate(arguments)")
        assertTrue("the guard must run before the normal runner starts", check in 0 until start)
        assertTrue(runner.contains("throw IllegalStateException(DeviceGuard.MESSAGE)"))

        val guard = File(root, "testing/DeviceGuard.kt").readText()
        assertTrue(guard.contains("Do not run phone tests on a real phone"))
    }

    @Test
    fun theAllowedListHasNoOtherEntries() {
        // Adding a file here is a decision: it must come with a reason (the map above), reviewed by a person.
        assertEquals(6, allowed.size)
        assertTrue(allowed.values.all { it.length > 10 })
    }
}

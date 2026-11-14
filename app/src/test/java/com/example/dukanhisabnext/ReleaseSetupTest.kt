package com.example.dukanhisabnext

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Group D1: the app's permanent identity and the release setup must not drift.
 * These read the build and manifest files as text (the same way BackupRulesTest does).
 */
class ReleaseSetupTest {

    private val gradle = File("build.gradle.kts").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val gitignore = File("../.gitignore").readText()

    @Test
    fun thePermanentIdIsTheTaqwatechOneAndDebugIsSeparate() {
        assertTrue(gradle.contains("val newAppId = \"com.taqwatech.dokanhisab\""))
        assertTrue(gradle.contains("applicationId = if (buildForOldApp) oldAppId else newAppId"))
        assertTrue("debug lives next to the real app", gradle.contains("applicationIdSuffix = \".debug\""))
        // The old id only through the explicit one-time property.
        assertTrue(gradle.contains("providers.gradleProperty(\"oldAppId\").orNull == \"true\""))
    }

    @Test
    fun theVersionIsOnePointZero() {
        assertTrue(gradle.contains("versionCode = 1"))
        assertTrue(gradle.contains("versionName = \"1.0.0\""))
        assertTrue(gradle.contains("minSdk = 26"))
        assertTrue(gradle.contains("targetSdk = 37"))
    }

    @Test
    fun r8StaysOnAndPackageScopeNeverComesBack() {
        assertTrue(gradle.contains("optimization {") && gradle.contains("enable = true"))
        // Only the warning comment may mention it; no real setting.
        val code = gradle.lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        assertFalse("packageScope crashed the release app once", code.contains("packageScope"))
    }

    @Test
    fun signingIsReadFromAnIgnoredFileAndNoSecretIsInTheBuild() {
        assertTrue(gradle.contains("rootProject.file(\"keystore.properties\")"))
        assertTrue("the release still builds without the file", gradle.contains("if (keystorePropertiesFile.exists()) signingConfig"))
        val code = gradle.lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        assertFalse(Regex("""(storePassword|keyPassword)\s*=\s*"""").containsMatchIn(code))
        assertFalse(Regex("""storeFile\s*=\s*file\("""").containsMatchIn(code))
        for (line in listOf("*.jks", "*.keystore", "keystore.properties", "local.properties", "mapping.txt")) {
            assertTrue("$line must be git-ignored", gitignore.lines().any { it.trim() == line })
        }
    }

    @Test
    fun theShareFolderIsTiedToWhicheverIdIsInstalled() {
        assertTrue(manifest.contains("android:authorities=\"\${applicationId}.fileprovider\""))
        val sources = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }
        for (file in sources) {
            val text = file.readText()
            assertFalse("${file.name} hard-codes a package", text.contains("\"com.example.dukanhisabnext") || text.contains("\"com.taqwatech"))
        }
    }

    @Test
    fun theAppAsksForNoPermissionAndHasNoNetwork() {
        assertFalse(manifest.contains("<uses-permission"))
        assertFalse(manifest.contains("android.permission.INTERNET"))
        val libraries = File("../gradle/libs.versions.toml").readText().lowercase()
        for (word in listOf("retrofit", "okhttp", "ktor", "volley", "firebase", "analytics", "crashlytics", "admob", "play-services-ads")) {
            assertFalse("no network, analytics or ads library ($word)", libraries.contains(word))
        }
    }
}

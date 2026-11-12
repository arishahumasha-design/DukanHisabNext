package com.example.dukanhisabnext.backup

import com.example.dukanhisabnext.data.AppDatabase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android's automatic backup: both rule files back up exactly the database and the small settings
 * file, and nothing of the restore work (no file-domain include). Also: every refusal message says
 * the data did not change.
 */
class BackupRulesTest {

    private val xmlDir = File("src/main/res/xml")
    private val expected = listOf(
        "database" to AppDatabase.FILE_NAME,
        "database" to AppDatabase.FILE_NAME + "-wal",
        "sharedpref" to "$BACKUP_PREFS_NAME.xml"
    )

    private fun rules(text: String, tag: String): List<Pair<String, String>> =
        Regex("<$tag domain=\"(\\w+)\" path=\"([^\"]+)\"").findAll(text).map { it.groupValues[1] to it.groupValues[2] }.toList()

    private fun withoutComments(text: String) = text.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    @Test
    fun theOldRulesFileIncludesExactlyTheDatabaseAndTheSettingsFile() {
        val text = withoutComments(File(xmlDir, "backup_rules.xml").readText())
        assertEquals(expected, rules(text, "include"))
        assertTrue(rules(text, "exclude").isEmpty())
    }

    @Test
    fun bothSectionsOfTheNewRulesFileAreTheSame() {
        val text = withoutComments(File(xmlDir, "data_extraction_rules.xml").readText())
        for (section in listOf("cloud-backup", "device-transfer")) {
            val body = text.substringAfter("<$section>").substringBefore("</$section>")
            assertEquals(section, expected, rules(body, "include"))
            assertTrue(section, rules(body, "exclude").isEmpty())
        }
    }

    @Test
    fun theManifestPointsAtBothRuleFiles() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"true\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertFalse("no new permission", manifest.contains("<uses-permission"))
    }

    @Test
    fun everyRefusalHasAMessageThatSaysTheDataDidNotChange() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val messages = Regex("<string name=\"(err_backup_\\w+)\">([^<]+)</string>").findAll(strings).associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(BackupProblem.entries.size, messages.size)
        for ((name, text) in messages) assertTrue(name, text.contains("আপনার ডেটা বদলায়নি"))
    }
}

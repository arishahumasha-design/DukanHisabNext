package com.example.dukanhisabnext.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/DATA_FORMAT.md must describe every table and column of the current schema (3.json), and
 * its backup section must match the code. Fails as soon as the database changes without the doc.
 */
class DataFormatDocTest {

    private val doc = File("../docs/DATA_FORMAT.md").readText()
    private val schema = File("schemas/com.example.dukanhisabnext.data.AppDatabase/$CURRENT_SCHEMA_VERSION.json").readText()

    /** table -> its columns, from the schema file (in each entity, "columnName" belongs to its fields). */
    private fun schemaTables(): Map<String, List<String>> =
        schema.split("\"tableName\": \"").drop(1).associate { chunk ->
            val table = chunk.substringBefore('"')
            table to Regex("\"columnName\": \"(\\w+)\"").findAll(chunk).map { it.groupValues[1] }.toList()
        }

    /** The text of the doc's section for one table (from its heading to the next heading). */
    private fun section(table: String): String {
        val start = doc.indexOf("### `$table`")
        assertTrue("DATA_FORMAT.md has no section for table $table", start >= 0)
        val end = doc.indexOf("\n#", start + 1).let { if (it < 0) doc.length else it }
        return doc.substring(start, end)
    }

    @Test
    fun everyTableAndColumnIsDescribed() {
        val tables = schemaTables()
        assertEquals(11, tables.size)
        for ((table, columns) in tables) {
            assertTrue(table, columns.isNotEmpty())
            val text = section(table)
            for (c in columns) assertTrue("DATA_FORMAT.md misses column $table.$c", text.contains("| `$c` |"))
        }
    }

    @Test
    fun theDocHasNoTableThatIsNotInTheSchema() {
        val documented = Regex("### `(\\w+)`").findAll(doc).map { it.groupValues[1] }.toSet()
        assertEquals(schemaTables().keys, documented)
    }

    @Test
    fun theBackupSectionMatchesTheCode() {
        assertTrue(doc.contains("`$MANIFEST_ENTRY`"))
        assertTrue(doc.contains("`$DATABASE_ENTRY`"))
        assertTrue(doc.contains("format=$BACKUP_FORMAT_NAME"))
        assertTrue(doc.contains("formatVersion=$BACKUP_FORMAT_VERSION"))
        assertTrue(doc.contains("platform=$BACKUP_PLATFORM"))
        for ((version, hash) in KNOWN_SCHEMA_HASHES) assertTrue("hash of $version", doc.contains("| $version | `$hash` |"))
        // Every manifest key the app writes is in the key table.
        val sample = BackupManifest(dbSchemaVersion = 3, appVersionCode = 1, appVersionName = "1", createdAt = 0, dbSize = 0, dbSha256 = "0".repeat(64), counts = emptyMap())
        for (key in writeManifest(sample).lines().filter { it.isNotEmpty() }.map { it.substringBefore('=') }) {
            assertTrue("manifest key $key", doc.contains("| `$key` |"))
        }
        assertTrue(doc.contains("| `count.<table>` |"))
    }

    @Test
    fun theDocExampleManifestIsAValidManifest() {
        val example = doc.substringAfter("```properties\n").substringBefore("```")
        val parsed = parseManifest(example)
        assertTrue("$parsed", parsed is ManifestResult.Ok)
        assertEquals(schemaTables().keys, (parsed as ManifestResult.Ok).manifest.counts.keys)
    }
}

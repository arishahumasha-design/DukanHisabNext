package com.example.dukanhisabnext.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks on the PC (no phone) that the migration SQL is EXACTLY what Room expects, by comparing it
 * with the exported schema file. The phone test MigrationTest also runs it on a real database.
 */
class MigrationSqlTest {

    private val schemaDir = File("schemas/com.example.dukanhisabnext.data.AppDatabase")

    /** Runs a migration against a fake database that only writes down every SQL statement it gets. */
    private fun sqlOf(migration: androidx.room.migration.Migration): List<String> {
        val statements = mutableListOf<String>()
        val fake = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") statements += args!![0] as String
            null
        } as SupportSQLiteDatabase
        migration.migrate(fake)
        return statements
    }

    /** Every "createSql" of one table in a schema file, with Room's table-name placeholder filled in. */
    private fun createSqlOf(version: Int, table: String): List<String> {
        val json = File(schemaDir, "$version.json").readText()
        val start = json.indexOf("\"tableName\": \"$table\"")
        assertTrue("table $table must be in $version.json", start >= 0)
        val next = json.indexOf("\"tableName\":", start + 1).let { if (it < 0) json.length else it }
        val block = json.substring(start, next)
        return Regex("\"createSql\": \"((?:[^\"\\\\]|\\\\.)*)\"").findAll(block)
            .map { it.groupValues[1].replace("\${TABLE_NAME}", table) }
            .toList()
    }

    @Test
    fun theSchemaFilesExist() {
        assertTrue(File(schemaDir, "1.json").exists())
        assertTrue(File(schemaDir, "2.json").exists())
    }

    @Test
    fun migration1To2RunsExactlyTheSqlFromTheVersion2Schema() {
        val expected = createSqlOf(2, "sale_cancellations") // the table, then its unique index
        assertEquals(2, expected.size)
        assertEquals(expected, sqlOf(MIGRATION_1_2))
    }

    @Test
    fun theNewTableHasAUniqueIndexOnSaleIdAndAForeignKeyToSales() {
        val (table, index) = sqlOf(MIGRATION_1_2)
        assertTrue(table.contains("FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`)"))
        assertTrue(index.startsWith("CREATE UNIQUE INDEX"))
        assertTrue(index.contains("(`saleId`)"))
    }

    @Test
    fun theVersion1SchemaHasNoCancellationTable() {
        assertTrue(!File(schemaDir, "1.json").readText().contains("sale_cancellations"))
    }

    @Test
    fun theMigrationOnlyCreatesNeverDropsOrChangesExistingTables() {
        for (sql in sqlOf(MIGRATION_1_2)) {
            assertTrue(sql, sql.startsWith("CREATE"))
            assertTrue(sql, !sql.contains("DROP", ignoreCase = true))
            assertTrue(sql, !sql.contains("ALTER", ignoreCase = true))
        }
    }

    @Test
    fun theMigrationsFormOneUnbrokenChainUpToVersion2() {
        assertEquals(1, ALL_MIGRATIONS.first().startVersion)
        ALL_MIGRATIONS.toList().zipWithNext { a, b -> assertEquals(a.endVersion, b.startVersion) }
        assertEquals(2, ALL_MIGRATIONS.last().endVersion)
        // The newest schema file must be the version the last migration reaches.
        val newest = schemaDir.listFiles()!!.mapNotNull { it.nameWithoutExtension.toIntOrNull() }.max()
        assertEquals(newest, ALL_MIGRATIONS.last().endVersion)
    }
}

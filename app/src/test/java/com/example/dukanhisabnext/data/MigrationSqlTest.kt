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
    fun theMigrationsFormOneUnbrokenChainUpToVersion4() {
        assertEquals(1, ALL_MIGRATIONS.first().startVersion)
        ALL_MIGRATIONS.toList().zipWithNext { a, b -> assertEquals(a.endVersion, b.startVersion) }
        assertEquals(4, ALL_MIGRATIONS.last().endVersion)
        // The newest schema file must be the version the last migration reaches.
        val newest = schemaDir.listFiles()!!.mapNotNull { it.nameWithoutExtension.toIntOrNull() }.max()
        assertEquals(newest, ALL_MIGRATIONS.last().endVersion)
        // And there is a schema file for every version on the way.
        for (v in 1..4) assertTrue("$v.json", File(schemaDir, "$v.json").exists())
    }

    // =====================================================================================
    // Version 2 -> 3 (M6, expenses): three added columns and two added indexes
    // =====================================================================================

    private fun tableNames(version: Int): List<String> =
        Regex("\"tableName\": \"(\\w+)\"").findAll(File(schemaDir, "$version.json").readText()).map { it.groupValues[1] }.toList()

    @Test
    fun migration2To3OnlyAddsColumnsAndIndexes() {
        val sql = sqlOf(MIGRATION_2_3)
        assertEquals(5, sql.size)
        for (statement in sql) {
            assertTrue(statement, statement.startsWith("ALTER TABLE") && statement.contains(" ADD COLUMN ") || statement.startsWith("CREATE"))
            assertTrue(statement, !statement.contains("DROP", ignoreCase = true))
            assertTrue(statement, !statement.contains("RENAME", ignoreCase = true))
        }
    }

    @Test
    fun theAddedColumnsGiveExactlyTheVersion3ExpenseCategoriesTable() {
        // The v2 table plus the ALTER-added columns must be the v3 table, character for character.
        val v2Table = createSqlOf(2, "expense_categories").first()
        val v3Table = createSqlOf(3, "expense_categories").first()
        val added = sqlOf(MIGRATION_2_3)
            .filter { it.startsWith("ALTER TABLE `expense_categories` ADD COLUMN ") }
            .map { it.removePrefix("ALTER TABLE `expense_categories` ADD COLUMN ") }
        assertEquals(
            listOf("`isPersonal` INTEGER NOT NULL DEFAULT 0", "`isActive` INTEGER NOT NULL DEFAULT 1", "`systemKey` TEXT"),
            added
        )
        assertTrue(v2Table, v2Table.endsWith(")"))
        assertEquals(v3Table, v2Table.dropLast(1) + ", " + added.joinToString(", ") + ")")
    }

    @Test
    fun theTwoNewIndexesAreExactlyTheOnesInTheVersion3Schema() {
        val sql = sqlOf(MIGRATION_2_3)
        val systemKeyIndex = createSqlOf(3, "expense_categories").single { it.contains("index_expense_categories_systemKey") }
        val dateIndex = createSqlOf(3, "expenses").single { it.contains("index_expenses_expenseDate") }
        assertTrue(systemKeyIndex.startsWith("CREATE UNIQUE INDEX"))
        assertTrue(sql.contains(systemKeyIndex))
        assertTrue(sql.contains(dateIndex))
        // Neither index existed in version 2.
        assertTrue(createSqlOf(2, "expense_categories").none { it.contains("systemKey") })
        assertTrue(createSqlOf(2, "expenses").none { it.contains("expenseDate`)") })
    }

    @Test
    fun theExpensesTableItselfIsUnchangedFromVersion2() {
        assertEquals(createSqlOf(2, "expenses").first(), createSqlOf(3, "expenses").first())
    }

    // =====================================================================================
    // Version 3 -> 4 (M9, speed): one added index, nothing else
    // =====================================================================================

    @Test
    fun migration3To4RunsExactlyTheNewIndexSqlFromTheVersion4Schema() {
        val index = createSqlOf(4, "stock_movements").single { it.contains("index_stock_movements_productId_quantity") }
        assertEquals(
            "CREATE INDEX IF NOT EXISTS `index_stock_movements_productId_quantity` ON `stock_movements` (`productId`, `quantity`)",
            index
        )
        assertEquals(listOf(index), sqlOf(MIGRATION_3_4))
    }

    @Test
    fun migration3To4NeverDropsRenamesOrAltersAnything() {
        for (sql in sqlOf(MIGRATION_3_4)) {
            assertTrue(sql, sql.startsWith("CREATE INDEX"))
            assertTrue(sql, !sql.contains("DROP", ignoreCase = true))
            assertTrue(sql, !sql.contains("RENAME", ignoreCase = true))
            assertTrue(sql, !sql.contains("ALTER", ignoreCase = true))
        }
    }

    @Test
    fun version4IsVersion3PlusExactlyOneIndex() {
        assertEquals(tableNames(3), tableNames(4)) // the same tables in the same order
        for (table in tableNames(4)) {
            val v3 = createSqlOf(3, table)
            val v4 = createSqlOf(4, table)
            if (table == "stock_movements") {
                // The table and its old indexes are unchanged; one index is added at the end.
                assertEquals(v3, v4.dropLast(1))
                assertEquals(sqlOf(MIGRATION_3_4), listOf(v4.last()))
            } else {
                assertEquals(table, v3, v4)
            }
        }
    }

    @Test
    fun theOldProductIdIndexIsKept() {
        assertTrue(createSqlOf(4, "stock_movements").any { it.contains("`index_stock_movements_productId` ") })
    }

    @Test
    fun everyOtherTableIsExactlyTheSameInVersion2And3() {
        assertEquals(tableNames(2).sorted(), tableNames(3).sorted()) // no table added or removed
        for (table in tableNames(3) - setOf("expense_categories", "expenses")) {
            assertEquals(table, createSqlOf(2, table), createSqlOf(3, table))
        }
    }
}

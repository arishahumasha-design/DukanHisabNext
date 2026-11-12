package com.example.dukanhisabnext.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Database migrations: each one turns an old database file into the next version WITHOUT losing
// any data. The app never uses fallbackToDestructiveMigration (that would wipe the shop's records).
//
// The SQL here must match what Room expects for the new version exactly. It is copied from the
// exported schema files app/schemas/.../<version>.json; MigrationSqlTest (PC) compares the SQL
// with those files and MigrationTest (phone) runs it on a real database.

/**
 * Version 1 -> 2: adds the sale_cancellations table (cancelling a sale) and its unique index on
 * saleId. No existing table or row is touched.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sale_cancellations` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`saleId` INTEGER NOT NULL, " +
                "`reason` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_sale_cancellations_saleId` " +
                "ON `sale_cancellations` (`saleId`)"
        )
    }
}

/**
 * Version 2 -> 3 (M6, expenses): only ADDS, nothing is changed or dropped.
 *  - expense_categories gets isPersonal (default 0 = a shop cost), isActive (default 1 = shown)
 *    and systemKey (null; set only on the default categories), plus a unique index on systemKey;
 *  - expenses gets an index on expenseDate (every list and report cuts by it).
 * Every existing row keeps its values; the new columns get the defaults above.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `expense_categories` ADD COLUMN `isPersonal` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `expense_categories` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `expense_categories` ADD COLUMN `systemKey` TEXT")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_expense_categories_systemKey` " +
                "ON `expense_categories` (`systemKey`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_expenseDate` ON `expenses` (`expenseDate`)")
    }
}

/** Every migration, in order. AppDatabase passes them to Room. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

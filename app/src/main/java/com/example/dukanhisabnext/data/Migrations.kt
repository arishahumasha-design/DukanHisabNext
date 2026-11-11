package com.example.dukanhisabnext.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Database migrations: each one turns an old database file into the next version WITHOUT losing
// any data. The app never uses fallbackToDestructiveMigration (that would wipe the shop's records).
//
// The SQL here must match what Room expects for the new version exactly. It is copied from the
// exported schema file app/schemas/.../2.json, and MigrationTest checks it on a real database.

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

/** Every migration, in order. AppDatabase passes them to Room. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)

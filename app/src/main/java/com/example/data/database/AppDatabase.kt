package com.example.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.database.entity.DedupEntity
import com.example.data.database.entity.TransactionEntity
import com.example.data.database.entity.WalletTransactionEntity
import com.example.data.database.entity.CustomerBorrowEntity
import com.example.data.database.entity.IntruderLogEntity

@Database(entities = [
    TransactionEntity::class, 
    DedupEntity::class, 
    WalletTransactionEntity::class, 
    CustomerBorrowEntity::class,
    IntruderLogEntity::class
], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun dedupDao(): DedupDao
    abstract fun cashbookDao(): CashbookDao
    abstract fun intruderDao(): IntruderDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `wallet_transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `walletType` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `category` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `customer_borrows` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `customerName` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `purpose` TEXT NOT NULL,
                        `expectedReturnDate` INTEGER NOT NULL,
                        `isCleared` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `intruder_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `appPackage` TEXT NOT NULL,
                        `appName` TEXT NOT NULL,
                        `photoPath` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `lockType` TEXT NOT NULL,
                        `failedAttempts` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}

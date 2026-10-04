package com.example.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.database.entity.DedupEntity
import com.example.data.database.entity.TransactionEntity
import com.example.data.database.entity.WalletTransactionEntity
import com.example.data.database.entity.CustomerBorrowEntity

@Database(entities = [
    TransactionEntity::class, 
    DedupEntity::class, 
    WalletTransactionEntity::class, 
    CustomerBorrowEntity::class
], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun dedupDao(): DedupDao
    abstract fun cashbookDao(): CashbookDao
}

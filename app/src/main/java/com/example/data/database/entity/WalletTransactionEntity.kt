package com.example.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallet_transactions")
data class WalletTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val walletType: String, // "DRAWER", "HOME"
    val amount: Double, // Positive for IN, Negative for OUT
    val category: String, // "INCOME", "EXPENSE", "TRANSFER", "MANUAL_ADJUST"
    val note: String,
    val timestamp: Long
)

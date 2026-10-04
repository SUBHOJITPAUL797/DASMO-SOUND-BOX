package com.example.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["timestamp"]), Index(value = ["status"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val amountFormatted: String,
    val sourceType: String,
    val sourceApp: String,
    val sourceAppName: String,
    val payerName: String?,
    val rawText: String,
    val timestamp: Long,
    val status: String,
    val announcementText: String?,
    val dedupKey: String,
    val refId: String? = null
)

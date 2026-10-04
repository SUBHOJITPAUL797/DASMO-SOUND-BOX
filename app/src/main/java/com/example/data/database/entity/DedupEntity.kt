package com.example.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dedup_records")
data class DedupEntity(
    @PrimaryKey
    val dedupKey: String,
    val timestamp: Long,
    val amount: Double,
    val expiresAt: Long
)

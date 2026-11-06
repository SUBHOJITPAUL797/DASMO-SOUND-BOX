package com.example.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intruder_logs")
data class IntruderLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appPackage: String,
    val appName: String,
    val photoPath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val lockType: String = "PATTERN", // "PATTERN" or "PIN"
    val failedAttempts: Int = 1
)


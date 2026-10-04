package com.example.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customer_borrows")
data class CustomerBorrowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customerName: String,
    val amount: Double,
    val purpose: String,
    val expectedReturnDate: Long,
    val isCleared: Boolean = false,
    val timestamp: Long
)

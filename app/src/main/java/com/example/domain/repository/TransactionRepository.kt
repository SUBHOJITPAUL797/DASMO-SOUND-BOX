package com.example.domain.repository

import com.example.data.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getAllTransactions(): Flow<List<TransactionEntity>>
    suspend fun insertTransaction(transaction: TransactionEntity)
    suspend fun deleteTransaction(id: Long)
    suspend fun clearAll()
}

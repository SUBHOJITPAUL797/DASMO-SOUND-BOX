package com.example.data.repository

import com.example.data.database.TransactionDao
import com.example.data.database.entity.TransactionEntity
import com.example.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow

class TransactionRepositoryImpl(private val dao: TransactionDao) : TransactionRepository {
    override fun getAllTransactions(): Flow<List<TransactionEntity>> = dao.getAllTransactions()

    override suspend fun insertTransaction(transaction: TransactionEntity) = dao.insertTransaction(transaction)

    override suspend fun deleteTransaction(id: Long) = dao.deleteById(id)

    override suspend fun clearAll() = dao.clearAll()
}

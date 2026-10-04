package com.example.domain.repository

import com.example.data.database.CashbookDao
import com.example.data.database.entity.CustomerBorrowEntity
import com.example.data.database.entity.WalletTransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CashbookRepository(private val cashbookDao: CashbookDao) {
    
    fun getWalletTransactions(): Flow<List<WalletTransactionEntity>> = cashbookDao.getAllWalletTransactions()
    
    fun getDrawerBalance(): Flow<Double> = cashbookDao.getWalletBalance("DRAWER").map { it ?: 0.0 }
    
    fun getHomeBalance(): Flow<Double> = cashbookDao.getWalletBalance("HOME").map { it ?: 0.0 }
    
    suspend fun addWalletTransaction(walletType: String, amount: Double, category: String, note: String) {
        val entity = WalletTransactionEntity(
            walletType = walletType,
            amount = amount,
            category = category,
            note = note,
            timestamp = System.currentTimeMillis()
        )
        cashbookDao.insertWalletTransaction(entity)
    }

    fun getCustomerBorrows(): Flow<List<CustomerBorrowEntity>> = cashbookDao.getAllCustomerBorrows()
    
    fun getTotalPendingBorrows(): Flow<Double> = cashbookDao.getTotalPendingBorrows().map { it ?: 0.0 }
    
    suspend fun addCustomerBorrow(name: String, amount: Double, purpose: String, expectedReturnDate: Long) {
        val entity = CustomerBorrowEntity(
            customerName = name,
            amount = amount,
            purpose = purpose,
            expectedReturnDate = expectedReturnDate,
            timestamp = System.currentTimeMillis()
        )
        cashbookDao.insertCustomerBorrow(entity)
    }
    
    suspend fun markBorrowCleared(borrow: CustomerBorrowEntity) {
        cashbookDao.updateCustomerBorrow(borrow.copy(isCleared = true))
    }
}

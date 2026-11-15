package com.example.harness

import com.example.data.database.CashbookDao
import com.example.data.database.entity.CustomerBorrowEntity
import com.example.data.database.entity.WalletTransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.CopyOnWriteArrayList

class InMemoryCashbookDao : CashbookDao {
    private val walletTxs = CopyOnWriteArrayList<WalletTransactionEntity>()
    private val walletFlow = MutableStateFlow<List<WalletTransactionEntity>>(emptyList())

    private val borrows = CopyOnWriteArrayList<CustomerBorrowEntity>()
    private val borrowFlow = MutableStateFlow<List<CustomerBorrowEntity>>(emptyList())

    override fun getAllWalletTransactions(): Flow<List<WalletTransactionEntity>> = walletFlow.asStateFlow()

    override fun getWalletBalance(walletType: String): Flow<Double?> {
        return walletFlow.map { list ->
            list.filter { it.walletType == walletType }.sumOf { it.amount }
        }
    }

    override suspend fun insertWalletTransaction(transaction: WalletTransactionEntity) {
        val id = (walletTxs.size + 1).toLong()
        walletTxs.add(0, transaction.copy(id = id))
        walletFlow.value = walletTxs.toList()
    }

    override fun getAllCustomerBorrows(): Flow<List<CustomerBorrowEntity>> = borrowFlow.asStateFlow()

    override suspend fun insertCustomerBorrow(borrow: CustomerBorrowEntity) {
        val id = (borrows.size + 1).toLong()
        borrows.add(0, borrow.copy(id = id))
        borrowFlow.value = borrows.toList()
    }

    override suspend fun updateCustomerBorrow(borrow: CustomerBorrowEntity) {
        val idx = borrows.indexOfFirst { it.id == borrow.id }
        if (idx != -1) {
            borrows[idx] = borrow
            borrowFlow.value = borrows.toList()
        }
    }

    override fun getTotalPendingBorrows(): Flow<Double?> {
        return borrowFlow.map { list ->
            list.filter { !it.isCleared }.sumOf { it.amount }
        }
    }

    fun clear() {
        walletTxs.clear()
        borrows.clear()
        walletFlow.value = emptyList()
        borrowFlow.value = emptyList()
    }
}

package com.example.presentation.screens.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.di.AppModule
import com.example.data.database.entity.CustomerBorrowEntity
import com.example.data.database.entity.WalletTransactionEntity
import com.example.domain.model.AnnouncementStatus
import com.example.domain.repository.CashbookRepository
import com.example.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LedgerUiState(
    val onlineBalance: Double = 0.0,
    val drawerBalance: Double = 0.0,
    val homeBalance: Double = 0.0,
    val totalPendingBorrows: Double = 0.0,
    val manualTransactions: List<WalletTransactionEntity> = emptyList(),
    val customerBorrows: List<CustomerBorrowEntity> = emptyList()
)

class LedgerViewModel(
    cashbookRepoParam: CashbookRepository? = null,
    transactionRepoParam: TransactionRepository? = null
) : ViewModel() {
    private val cashbookRepo: CashbookRepository? = cashbookRepoParam ?: AppModule.cashbookRepository
    private val transactionRepo: TransactionRepository? = transactionRepoParam ?: AppModule.transactionRepository

    private val flow1 = combine(
        transactionRepo?.getAllTransactions() ?: flowOf(emptyList()),
        cashbookRepo?.getDrawerBalance() ?: flowOf(0.0),
        cashbookRepo?.getHomeBalance() ?: flowOf(0.0)
    ) { onlineTxs, drawerBal, homeBal ->
        Triple(onlineTxs, drawerBal, homeBal)
    }

    private val flow2 = combine(
        cashbookRepo?.getTotalPendingBorrows() ?: flowOf(0.0),
        cashbookRepo?.getWalletTransactions() ?: flowOf(emptyList()),
        cashbookRepo?.getCustomerBorrows() ?: flowOf(emptyList())
    ) { pendingBorrows, manualTxs, borrows ->
        Triple(pendingBorrows, manualTxs, borrows)
    }

    val uiState: StateFlow<LedgerUiState> = combine(flow1, flow2) { f1, f2 ->
        val onlineTxs = f1.first
        val drawerBal = f1.second
        val homeBal = f1.third
        
        val pendingBorrows = f2.first
        val manualTxs = f2.second
        val borrows = f2.third

        // Exclude DUPLICATE_SKIPPED and PARSE_ERROR: only count verified ANNOUNCED payments
        val onlineTotal = onlineTxs
            .filter { it.status == AnnouncementStatus.ANNOUNCED.name }
            .sumOf { it.amount }
            
        LedgerUiState(
            onlineBalance = onlineTotal,
            drawerBalance = drawerBal,
            homeBalance = homeBal,
            totalPendingBorrows = pendingBorrows,
            manualTransactions = manualTxs,
            customerBorrows = borrows
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LedgerUiState())

    fun addManualTransaction(type: String, amount: Double, wallet: String, note: String) {
        viewModelScope.launch {
            val amountValue = if (type == "EXPENSE" || type == "WITHDRAW") -amount else amount
            val category = when(type) {
                "INCOME" -> "INCOME"
                "EXPENSE" -> "EXPENSE"
                else -> "TRANSFER"
            }
            cashbookRepo?.addWalletTransaction(wallet, amountValue, category, note)
        }
    }

    fun addCustomerBorrow(name: String, amount: Double, purpose: String, expectedReturnDate: Long) {
        viewModelScope.launch {
            cashbookRepo?.addCustomerBorrow(name, amount, purpose, expectedReturnDate)
        }
    }
    
    fun markBorrowCleared(borrow: CustomerBorrowEntity) {
        viewModelScope.launch {
            cashbookRepo?.markBorrowCleared(borrow)
        }
    }
}

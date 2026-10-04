package com.example.presentation.screens.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.di.AppModule
import com.example.data.database.entity.CustomerBorrowEntity
import com.example.data.database.entity.WalletTransactionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

class LedgerViewModel : ViewModel() {
    private val cashbookRepo = AppModule.cashbookRepository!!
    private val transactionRepo = AppModule.transactionRepository!!

    private val flow1 = combine(
        transactionRepo.getAllTransactions(),
        cashbookRepo.getDrawerBalance(),
        cashbookRepo.getHomeBalance()
    ) { onlineTxs, drawerBal, homeBal ->
        Triple(onlineTxs, drawerBal, homeBal)
    }

    private val flow2 = combine(
        cashbookRepo.getTotalPendingBorrows(),
        cashbookRepo.getWalletTransactions(),
        cashbookRepo.getCustomerBorrows()
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

        val onlineTotal = onlineTxs
            .filter { it.status == "ANNOUNCED" || it.status == "DUPLICATE_SKIPPED" }
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
            cashbookRepo.addWalletTransaction(wallet, amountValue, category, note)
        }
    }

    fun addCustomerBorrow(name: String, amount: Double, purpose: String, expectedReturnDate: Long) {
        viewModelScope.launch {
            cashbookRepo.addWalletTransaction("DRAWER", -amount, "BORROW_GIVEN", "Given to $name")
            cashbookRepo.addCustomerBorrow(name, amount, purpose, expectedReturnDate)
        }
    }
    
    fun markBorrowCleared(borrow: CustomerBorrowEntity) {
        viewModelScope.launch {
            cashbookRepo.addWalletTransaction("DRAWER", borrow.amount, "BORROW_REPAID", "Repaid by ${borrow.customerName}")
            cashbookRepo.markBorrowCleared(borrow)
        }
    }
}

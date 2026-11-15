package com.example.presentation.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.entity.TransactionEntity
import com.example.di.AppModule
import com.example.domain.model.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {
    private val transRepo = AppModule.transactionRepository!!
    private val settingsRepo = AppModule.settingsRepository

    val transactions: StateFlow<List<TransactionEntity>> = transRepo.getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appSettings: StateFlow<AppSettings> = (settingsRepo?.getSettings() ?: flowOf(AppSettings()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            transRepo.deleteTransaction(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            transRepo.clearAll()
        }
    }
}

package com.example.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.entity.TransactionEntity
import com.example.di.AppModule
import com.example.domain.model.AnnouncementStatus
import com.example.domain.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeUiState(
    val appSettings: AppSettings = AppSettings(),
    val todayEarnings: Double = 0.0,
    val todayCount: Int = 0,
    val todayTotalAmount: Double = 0.0,
    val todayTransactionCount: Int = 0,
    val lastPayment: TransactionEntity? = null,
    val liveActivity: List<TransactionEntity> = emptyList(),
    val recentTransactions: List<TransactionEntity> = emptyList()
)

class HomeViewModel : ViewModel() {
    private val settingsRepo = AppModule.settingsRepository!!
    private val transRepo = AppModule.transactionRepository!!

    val uiState: StateFlow<HomeUiState> = combine(
        settingsRepo.getSettings(),
        transRepo.getAllTransactions()
    ) { settings, transactions ->
        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY, 0)
        now.set(Calendar.MINUTE, 0)
        now.set(Calendar.SECOND, 0)
        now.set(Calendar.MILLISECOND, 0)
        val todayStartMs = now.timeInMillis
        
        val todayTransactions = transactions.filter { it.timestamp >= todayStartMs && it.status == AnnouncementStatus.ANNOUNCED.name }
        
        val todayEarnings = todayTransactions.sumOf { it.amount }
        val todayCount = todayTransactions.size
        
        val lastPayment = transactions.firstOrNull { it.status == AnnouncementStatus.ANNOUNCED.name }
        val liveActivity = transactions.take(15)
        
        HomeUiState(
            appSettings = settings,
            todayEarnings = todayEarnings,
            todayCount = todayCount,
            todayTotalAmount = todayEarnings,
            todayTransactionCount = todayCount,
            lastPayment = lastPayment,
            liveActivity = liveActivity,
            recentTransactions = transactions
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
    
    fun toggleServiceState(isEnabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateIsEnabled(isEnabled)
        }
    }

    fun updateShopUpi(upiId: String, shopName: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(shopUpiId = upiId, shopName = shopName) }
        }
    }

    fun simulateTestPayment(amount: Double = 150.0, payerName: String? = "Rahul Sharma", appName: String = "Google Pay") {
        viewModelScope.launch {
            val event = com.example.domain.model.PaymentEvent(
                amount = amount,
                sourceType = com.example.domain.model.SourceType.NOTIFICATION,
                sourceApp = "com.google.android.apps.nbu.paisa.user",
                sourceAppName = appName,
                payerName = payerName,
                refId = "4287" + (10000000..99999999).random().toString(),
                rawText = "Received ₹$amount from $payerName via $appName"
            )
            AppModule.paymentEventBus?.emit(event)
        }
    }
}

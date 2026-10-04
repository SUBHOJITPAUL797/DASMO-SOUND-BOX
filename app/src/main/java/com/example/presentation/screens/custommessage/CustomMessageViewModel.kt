package com.example.presentation.screens.custommessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.di.AppModule
import com.example.domain.model.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CustomMessageViewModel : ViewModel() {
    private val settingsRepo = AppModule.settingsRepository!!

    val uiState: StateFlow<AppSettings> = settingsRepo.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun updatePrefix(enabled: Boolean, text: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(customPrefixEnabled = enabled, customPrefix = text) }
        }
    }

    fun updateSuffix(enabled: Boolean, text: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(customSuffixEnabled = enabled, customSuffix = text) }
        }
    }
}

package com.example.presentation.screens.custommessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.di.AppModule
import com.example.domain.model.AppSettings
import com.example.domain.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CustomMessageViewModel(
    settingsRepoParam: SettingsRepository? = null
) : ViewModel() {
    private val settingsRepo: SettingsRepository? = settingsRepoParam ?: AppModule.settingsRepository

    val uiState: StateFlow<AppSettings> = settingsRepo?.getSettings()
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
        ?: MutableStateFlow(AppSettings())

    private var prefixJob: Job? = null
    private var suffixJob: Job? = null

    fun updatePrefix(enabled: Boolean, text: String) {
        prefixJob?.cancel()
        viewModelScope.launch {
            settingsRepo?.updateSettings { it.copy(customPrefixEnabled = enabled, customPrefix = text) }
        }
    }

    fun updatePrefixDebounced(enabled: Boolean, text: String, debounceMs: Long = 400L) {
        prefixJob?.cancel()
        prefixJob = viewModelScope.launch {
            delay(debounceMs)
            settingsRepo?.updateSettings { it.copy(customPrefixEnabled = enabled, customPrefix = text) }
        }
    }

    fun updateSuffix(enabled: Boolean, text: String) {
        suffixJob?.cancel()
        viewModelScope.launch {
            settingsRepo?.updateSettings { it.copy(customSuffixEnabled = enabled, customSuffix = text) }
        }
    }

    fun updateSuffixDebounced(enabled: Boolean, text: String, debounceMs: Long = 400L) {
        suffixJob?.cancel()
        suffixJob = viewModelScope.launch {
            delay(debounceMs)
            settingsRepo?.updateSettings { it.copy(customSuffixEnabled = enabled, customSuffix = text) }
        }
    }
}

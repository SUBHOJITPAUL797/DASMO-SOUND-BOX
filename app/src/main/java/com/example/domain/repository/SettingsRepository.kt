package com.example.domain.repository

import com.example.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings)
    suspend fun updateIsEnabled(enabled: Boolean)
    suspend fun updateHasCompletedOnboarding(completed: Boolean)
    suspend fun updateAppLockEnabled(enabled: Boolean)
    suspend fun updateAppLockType(type: String)
    suspend fun updateAppLockPin(pin: String)
    suspend fun updateAppLockPattern(pattern: String)
    suspend fun updateLockedAppPackages(packages: Set<String>)
    suspend fun updateIntruderSelfieEnabled(enabled: Boolean)
}

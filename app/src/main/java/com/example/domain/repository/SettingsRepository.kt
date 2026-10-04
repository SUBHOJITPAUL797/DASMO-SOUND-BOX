package com.example.domain.repository

import com.example.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings)
    suspend fun updateIsEnabled(enabled: Boolean)
    suspend fun updateHasCompletedOnboarding(completed: Boolean)
}

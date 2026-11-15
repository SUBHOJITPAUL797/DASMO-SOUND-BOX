package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.data.datastore.PreferencesKeys
import com.example.data.datastore.dataStore
import com.example.domain.model.AppSettings
import com.example.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    private val _cachedSettings = MutableStateFlow(AppSettings())
    val cachedSettings = _cachedSettings.asStateFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.dataStore.data.collect { preferences ->
                    _cachedSettings.value = mapPreferencesToAppSettings(preferences)
                }
            } catch (e: Throwable) {
                // Keep default or current cached settings
            }
        }
    }

    override fun getLatestSettings(): AppSettings {
        return _cachedSettings.value
    }

    private fun mapPreferencesToAppSettings(preferences: Preferences): AppSettings {
        return AppSettings(
            isEnabled = preferences[PreferencesKeys.IS_ENABLED] ?: true,
            language = preferences[PreferencesKeys.LANGUAGE] ?: "hi-IN",
            speechRate = preferences[PreferencesKeys.SPEECH_RATE] ?: 1.0f,
            voicePitch = preferences[PreferencesKeys.VOICE_PITCH] ?: 1.0f,
            voiceName = preferences[PreferencesKeys.VOICE_NAME] ?: "",
            chimeEnabled = preferences[PreferencesKeys.CHIME_ENABLED] ?: true,
            announcePayerName = preferences[PreferencesKeys.ANNOUNCE_PAYER_NAME] ?: false,
            announcementVolume = preferences[PreferencesKeys.ANNOUNCEMENT_VOLUME] ?: 100,
            dedupWindowSeconds = preferences[PreferencesKeys.DEDUP_WINDOW_SECONDS] ?: 30,
            smsDetectionEnabled = preferences[PreferencesKeys.SMS_DETECTION_ENABLED] ?: true,
            notificationDetectionEnabled = preferences[PreferencesKeys.NOTIF_DETECTION_ENABLED] ?: true,
            customPrefixEnabled = preferences[PreferencesKeys.CUSTOM_PREFIX_ENABLED] ?: false,
            customPrefix = preferences[PreferencesKeys.CUSTOM_PREFIX] ?: "",
            customSuffixEnabled = preferences[PreferencesKeys.CUSTOM_SUFFIX_ENABLED] ?: true,
            customSuffix = preferences[PreferencesKeys.CUSTOM_SUFFIX] ?: "Thank you!",
            customTemplates = preferences[PreferencesKeys.CUSTOM_TEMPLATES] ?: "[]",
            activeTemplateIndex = preferences[PreferencesKeys.ACTIVE_TEMPLATE_INDEX] ?: 0,
            theme = preferences[PreferencesKeys.THEME] ?: "LIGHT",
            hasCompletedOnboarding = preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] ?: false,
            subscriptionStatus = preferences[PreferencesKeys.SUBSCRIPTION_STATUS] ?: "FREE",
            subscriptionExpiryMs = preferences[PreferencesKeys.SUBSCRIPTION_EXPIRY_MS] ?: 0L,
            lastSubscriptionCheckMs = preferences[PreferencesKeys.LAST_SUBSCRIPTION_CHECK_MS] ?: 0L,
            disabledAppPackages = preferences[PreferencesKeys.DISABLED_APP_PACKAGES] ?: emptySet(),
            chimeSound = preferences[PreferencesKeys.CHIME_SOUND] ?: "PAYTM_DING_DONG",
            bilingualEnabled = preferences[PreferencesKeys.BILINGUAL_ENABLED] ?: false,
            flashAlertEnabled = preferences[PreferencesKeys.FLASH_ALERT_ENABLED] ?: false,
            minAmountThreshold = preferences[PreferencesKeys.MIN_AMOUNT_THRESHOLD] ?: 0.0,
            shopUpiId = preferences[PreferencesKeys.SHOP_UPI_ID] ?: "",
            shopName = preferences[PreferencesKeys.SHOP_NAME] ?: "My Shop",
            appLockEnabled = preferences[PreferencesKeys.APP_LOCK_ENABLED] ?: false,
            appLockType = preferences[PreferencesKeys.APP_LOCK_TYPE] ?: "PATTERN",
            appLockPin = preferences[PreferencesKeys.APP_LOCK_PIN] ?: "",
            appLockPattern = preferences[PreferencesKeys.APP_LOCK_PATTERN] ?: "",
            appLockPassword = preferences[PreferencesKeys.APP_LOCK_PASSWORD] ?: "",
            lockedAppPackages = preferences[PreferencesKeys.LOCKED_APP_PACKAGES] ?: emptySet(),
            intruderSelfieEnabled = preferences[PreferencesKeys.INTRUDER_SELFIE_ENABLED] ?: true
        )
    }

    override fun getSettings(): Flow<AppSettings> {
        return context.dataStore.data.map { preferences ->
            mapPreferencesToAppSettings(preferences)
        }
    }

    override suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings) {
        context.dataStore.edit { preferences ->
            val current = mapPreferencesToAppSettings(preferences)
            val updated = transform(current)
            _cachedSettings.value = updated
            preferences[PreferencesKeys.IS_ENABLED] = updated.isEnabled
            preferences[PreferencesKeys.LANGUAGE] = updated.language
            preferences[PreferencesKeys.SPEECH_RATE] = updated.speechRate
            preferences[PreferencesKeys.VOICE_PITCH] = updated.voicePitch
            preferences[PreferencesKeys.VOICE_NAME] = updated.voiceName
            preferences[PreferencesKeys.CHIME_ENABLED] = updated.chimeEnabled
            preferences[PreferencesKeys.ANNOUNCE_PAYER_NAME] = updated.announcePayerName
            preferences[PreferencesKeys.ANNOUNCEMENT_VOLUME] = updated.announcementVolume
            preferences[PreferencesKeys.DEDUP_WINDOW_SECONDS] = updated.dedupWindowSeconds
            preferences[PreferencesKeys.SMS_DETECTION_ENABLED] = updated.smsDetectionEnabled
            preferences[PreferencesKeys.NOTIF_DETECTION_ENABLED] = updated.notificationDetectionEnabled
            preferences[PreferencesKeys.CUSTOM_PREFIX_ENABLED] = updated.customPrefixEnabled
            preferences[PreferencesKeys.CUSTOM_PREFIX] = updated.customPrefix
            preferences[PreferencesKeys.CUSTOM_SUFFIX_ENABLED] = updated.customSuffixEnabled
            preferences[PreferencesKeys.CUSTOM_SUFFIX] = updated.customSuffix
            preferences[PreferencesKeys.CUSTOM_TEMPLATES] = updated.customTemplates
            preferences[PreferencesKeys.ACTIVE_TEMPLATE_INDEX] = updated.activeTemplateIndex
            preferences[PreferencesKeys.THEME] = updated.theme
            preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] = updated.hasCompletedOnboarding
            preferences[PreferencesKeys.SUBSCRIPTION_STATUS] = updated.subscriptionStatus
            preferences[PreferencesKeys.SUBSCRIPTION_EXPIRY_MS] = updated.subscriptionExpiryMs
            preferences[PreferencesKeys.LAST_SUBSCRIPTION_CHECK_MS] = updated.lastSubscriptionCheckMs
            preferences[PreferencesKeys.DISABLED_APP_PACKAGES] = updated.disabledAppPackages
            preferences[PreferencesKeys.CHIME_SOUND] = updated.chimeSound
            preferences[PreferencesKeys.BILINGUAL_ENABLED] = updated.bilingualEnabled
            preferences[PreferencesKeys.FLASH_ALERT_ENABLED] = updated.flashAlertEnabled
            preferences[PreferencesKeys.MIN_AMOUNT_THRESHOLD] = updated.minAmountThreshold
            preferences[PreferencesKeys.SHOP_UPI_ID] = updated.shopUpiId
            preferences[PreferencesKeys.SHOP_NAME] = updated.shopName
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = updated.appLockEnabled
            preferences[PreferencesKeys.APP_LOCK_TYPE] = updated.appLockType
            preferences[PreferencesKeys.APP_LOCK_PIN] = updated.appLockPin
            preferences[PreferencesKeys.APP_LOCK_PATTERN] = updated.appLockPattern
            preferences[PreferencesKeys.APP_LOCK_PASSWORD] = updated.appLockPassword
            preferences[PreferencesKeys.LOCKED_APP_PACKAGES] = updated.lockedAppPackages
            preferences[PreferencesKeys.INTRUDER_SELFIE_ENABLED] = updated.intruderSelfieEnabled
        }
    }

    override suspend fun updateIsEnabled(enabled: Boolean) {
        _cachedSettings.value = _cachedSettings.value.copy(isEnabled = enabled)
        context.dataStore.edit { it[PreferencesKeys.IS_ENABLED] = enabled }
    }

    override suspend fun updateHasCompletedOnboarding(completed: Boolean) {
        _cachedSettings.value = _cachedSettings.value.copy(hasCompletedOnboarding = completed)
        context.dataStore.edit { it[PreferencesKeys.HAS_COMPLETED_ONBOARDING] = completed }
    }

    override suspend fun updateAppLockEnabled(enabled: Boolean) {
        _cachedSettings.value = _cachedSettings.value.copy(appLockEnabled = enabled)
        context.dataStore.edit { it[PreferencesKeys.APP_LOCK_ENABLED] = enabled }
    }

    override suspend fun updateAppLockType(type: String) {
        _cachedSettings.value = _cachedSettings.value.copy(appLockType = type)
        context.dataStore.edit { it[PreferencesKeys.APP_LOCK_TYPE] = type }
    }

    override suspend fun updateAppLockPin(pin: String) {
        _cachedSettings.value = _cachedSettings.value.copy(appLockPin = pin)
        context.dataStore.edit { it[PreferencesKeys.APP_LOCK_PIN] = pin }
    }

    override suspend fun updateAppLockPattern(pattern: String) {
        _cachedSettings.value = _cachedSettings.value.copy(appLockPattern = pattern)
        context.dataStore.edit { it[PreferencesKeys.APP_LOCK_PATTERN] = pattern }
    }

    override suspend fun updateAppLockPassword(password: String) {
        _cachedSettings.value = _cachedSettings.value.copy(appLockPassword = password)
        context.dataStore.edit { it[PreferencesKeys.APP_LOCK_PASSWORD] = password }
    }

    override suspend fun updateLockedAppPackages(packages: Set<String>) {
        _cachedSettings.value = _cachedSettings.value.copy(lockedAppPackages = packages)
        context.dataStore.edit { it[PreferencesKeys.LOCKED_APP_PACKAGES] = packages }
    }

    override suspend fun updateIntruderSelfieEnabled(enabled: Boolean) {
        _cachedSettings.value = _cachedSettings.value.copy(intruderSelfieEnabled = enabled)
        context.dataStore.edit { it[PreferencesKeys.INTRUDER_SELFIE_ENABLED] = enabled }
    }
}

package com.example.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.di.AppModule
import com.example.domain.model.AppSettings
import com.example.util.TonePreset
import com.example.util.VoiceOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsViewModel : ViewModel() {
    private val settingsRepo = AppModule.settingsRepository!!
    private val ttsEngine = AppModule.ttsEngine!!

    val uiState: StateFlow<AppSettings> = settingsRepo.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _availableVoices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<VoiceOption>> = _availableVoices.asStateFlow()

    private val _isPlayingPreview = MutableStateFlow(false)
    val isPlayingPreview: StateFlow<Boolean> = _isPlayingPreview.asStateFlow()

    init {
        loadAvailableVoices()
    }

    fun loadAvailableVoices() {
        viewModelScope.launch {
            val lang = uiState.value.language
            _availableVoices.value = ttsEngine.getAvailableVoices(lang)
        }
    }

    fun updateLanguage(language: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(language = language) }
            val locale = Locale.forLanguageTag(language)
            ttsEngine.setLanguage(locale)
            _availableVoices.value = ttsEngine.getAvailableVoices(language)
        }
    }

    fun updateSpeechRate(rate: Float) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(speechRate = rate) }
            ttsEngine.setSpeechRate(rate)
        }
    }

    fun updateVoicePitch(pitch: Float) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(voicePitch = pitch) }
            ttsEngine.setPitch(pitch)
        }
    }

    fun updateVoiceName(voiceName: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(voiceName = voiceName) }
            ttsEngine.setVoiceByName(voiceName)
        }
    }

    fun applyTonePreset(preset: TonePreset) {
        viewModelScope.launch {
            settingsRepo.updateSettings {
                it.copy(voicePitch = preset.pitch, speechRate = preset.speed)
            }
            ttsEngine.setPitch(preset.pitch)
            ttsEngine.setSpeechRate(preset.speed)
            previewVoiceSample(pitch = preset.pitch, rate = preset.speed)
        }
    }

    fun updateChime(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(chimeEnabled = enabled) } }
    }

    fun updateChimeSound(sound: String) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(chimeSound = sound) } }
    }

    fun playChimePreview(context: android.content.Context, sound: String) {
        viewModelScope.launch {
            com.example.util.ChimePlayer.play(context, sound, uiState.value.announcementVolume)
        }
    }

    fun updateBilingualEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(bilingualEnabled = enabled) } }
    }

    fun updateFlashAlertEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(flashAlertEnabled = enabled) } }
    }

    fun updateMinAmountThreshold(threshold: Double) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(minAmountThreshold = threshold) } }
    }

    fun updateShopDetails(upiId: String, shopName: String) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(shopUpiId = upiId, shopName = shopName) } }
    }

    fun updateSmsDetection(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(smsDetectionEnabled = enabled) } }
    }

    fun updateNotificationDetection(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(notificationDetectionEnabled = enabled) } }
    }

    fun updateVolume(volume: Int) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(announcementVolume = volume) } }
    }

    fun updateDedupWindow(seconds: Int) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(dedupWindowSeconds = seconds) } }
    }

    fun updateAnnouncePayerName(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(announcePayerName = enabled) } }
    }

    fun stopAudio() {
        _isPlayingPreview.value = false
        ttsEngine.stop()
    }

    fun previewVoiceSample(
        customText: String? = null,
        rate: Float? = null,
        pitch: Float? = null,
        voiceName: String? = null
    ) {
        viewModelScope.launch {
            _isPlayingPreview.value = true
            val settings = uiState.value
            val currentRate = rate ?: settings.speechRate
            val currentPitch = pitch ?: settings.voicePitch
            val currentVoice = voiceName ?: settings.voiceName

            ttsEngine.applyVoiceSettings(settings.language, currentRate, currentPitch, currentVoice)

            val textToSpeak = customText?.takeIf { it.isNotBlank() } ?: ttsEngine.buildAnnouncementText(
                amount = 100.0,
                prefixEnabled = settings.customPrefixEnabled,
                prefix = settings.customPrefix,
                suffixEnabled = settings.customSuffixEnabled,
                suffix = settings.customSuffix,
                language = settings.language,
                payerName = if (settings.announcePayerName) "Rahul Kumar" else null
            )

            ttsEngine.announce(
                text = textToSpeak,
                volume = settings.announcementVolume,
                rate = currentRate,
                pitch = currentPitch,
                voiceName = currentVoice
            )
            
            // Auto reset wave after estimated speak duration
            kotlinx.coroutines.delay(3200)
            _isPlayingPreview.value = false
        }
    }

    private fun String?.isNullByOrBlank(): Boolean {
        return this == null || this.isBlank()
    }

    fun testAnnouncement() {
        previewVoiceSample()
    }
}


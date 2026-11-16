package com.example.presentation.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.di.AppModule
import com.example.domain.model.AppSettings
import com.example.util.TonePreset
import com.example.util.VoiceOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

sealed interface VoicePreviewState {
    data object Idle : VoicePreviewState
    data class Playing(val seq: Int) : VoicePreviewState
}

class SettingsViewModel : ViewModel() {
    private val settingsRepo = AppModule.settingsRepository!!
    private val ttsEngine = AppModule.ttsEngine!!
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isSettingsLoaded = MutableStateFlow(false)
    val isSettingsLoaded: StateFlow<Boolean> = _isSettingsLoaded.asStateFlow()

    val uiState: StateFlow<AppSettings> = settingsRepo.getSettings()
        .onEach { _isSettingsLoaded.value = true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = settingsRepo.getLatestSettings()
        )

    private val _availableVoices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<VoiceOption>> = _availableVoices.asStateFlow()

    private val _previewState = MutableStateFlow<VoicePreviewState>(VoicePreviewState.Idle)
    val previewState: StateFlow<VoicePreviewState> = _previewState.asStateFlow()

    val isPlayingPreview: StateFlow<Boolean> = _previewState
        .map { it is VoicePreviewState.Playing }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var previewJob: Job? = null
    private val previewSeq = AtomicInteger(0)

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
        persistenceScope.launch(NonCancellable) {
            settingsRepo.updateSettings { it.copy(minAmountThreshold = threshold) }
        }
    }

    fun updateShopDetails(upiId: String, shopName: String) {
        val cleanUpi = upiId.trim()
        val cleanShopName = shopName.trim().ifBlank { "My Shop" }
        persistenceScope.launch(NonCancellable) {
            settingsRepo.updateSettings {
                it.copy(shopUpiId = cleanUpi, shopName = cleanShopName)
            }
        }
    }

    fun flushMerchantProfile(upiId: String, shopName: String, minAmountThreshold: Double) {
        val cleanUpi = upiId.trim()
        val cleanShopName = shopName.trim()
        persistenceScope.launch(NonCancellable) {
            settingsRepo.updateSettings { current ->
                current.copy(
                    shopUpiId = cleanUpi,
                    shopName = if (cleanShopName.isNotEmpty()) cleanShopName else current.shopName,
                    minAmountThreshold = minAmountThreshold
                )
            }
        }
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

    fun updateTheme(theme: String) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(theme = theme) } }
    }

    fun stopAudio() {
        previewSeq.incrementAndGet()
        previewJob?.cancel()
        _previewState.value = VoicePreviewState.Idle
        ttsEngine.stop()
    }

    fun previewVoiceSample(
        customText: String? = null,
        rate: Float? = null,
        pitch: Float? = null,
        voiceName: String? = null
    ) {
        previewJob?.cancel()
        val seq = previewSeq.incrementAndGet()
        previewJob = viewModelScope.launch {
            _previewState.value = VoicePreviewState.Playing(seq)
            try {
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

                ttsEngine.announceSuspend(
                    text = textToSpeak,
                    volume = settings.announcementVolume,
                    rate = currentRate,
                    pitch = currentPitch,
                    voiceName = currentVoice
                )
            } finally {
                if (previewSeq.get() == seq) {
                    _previewState.value = VoicePreviewState.Idle
                }
            }
        }
    }

    private fun String?.isNullByOrBlank(): Boolean {
        return this == null || this.isBlank()
    }

    fun testAnnouncement() {
        previewVoiceSample()
    }

    // --- GitHub Releases In-App Update Engine ---

    private val updateManager: com.example.util.update.AppUpdateManager =
        AppModule.updateManager ?: com.example.util.update.AppUpdateManager.getInstance()

    private val _updateState = MutableStateFlow<com.example.util.update.UpdateStatus>(
        com.example.util.update.UpdateStatus.Idle
    )
    val updateState: StateFlow<com.example.util.update.UpdateStatus> = _updateState.asStateFlow()

    fun checkForUpdates(context: Context) {
        viewModelScope.launch {
            _updateState.value = com.example.util.update.UpdateStatus.Checking
            val result = updateManager.checkForUpdate(context)
            _updateState.value = result
        }
    }

    fun downloadAndInstallUpdate(context: Context, updateInfo: com.example.util.update.AppUpdateInfo) {
        viewModelScope.launch {
            try {
                _updateState.value = com.example.util.update.UpdateStatus.Downloading(
                    updateInfo = updateInfo,
                    progressPercent = 0,
                    bytesDownloaded = 0,
                    totalBytes = updateInfo.apkSizeBytes
                )
                val apkFile = updateManager.downloadApk(context, updateInfo) { progressStatus ->
                    _updateState.value = progressStatus
                }
                _updateState.value = com.example.util.update.UpdateStatus.ReadyToInstall(
                    updateInfo = updateInfo,
                    apkFile = apkFile
                )
                // Trigger installer immediately once downloaded
                updateManager.installApk(context, apkFile)
            } catch (e: Exception) {
                _updateState.value = com.example.util.update.UpdateStatus.Error(
                    message = e.localizedMessage ?: "Failed to download update."
                )
            }
        }
    }

    fun installApk(context: Context, apkFile: java.io.File) {
        updateManager.installApk(context, apkFile)
    }

    fun openReleaseInBrowser(context: Context, url: String) {
        updateManager.openReleaseInBrowser(context, url)
    }

    fun dismissUpdate() {
        _updateState.value = com.example.util.update.UpdateStatus.Idle
    }
}



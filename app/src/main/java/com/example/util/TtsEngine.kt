package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.domain.parser.IndianNumberFormatter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class VoiceOption(
    val name: String,
    val displayName: String,
    val language: String,
    val isNetwork: Boolean
)

data class TonePreset(
    val id: String,
    val title: String,
    val description: String,
    val pitch: Float,
    val speed: Float
)

class TtsEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    @Volatile private var isInitialized = false
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var previousVolume: Int = -1
    private var pendingLanguage: Locale? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    init {
        ensureInitialized()
    }

    companion object {
        private const val TAG = "TtsEngine"
        val TONE_PRESETS = listOf(
            TonePreset("DEEP_WARM", "Deep & Warm", "Relaxed bass tone for natural announcement", pitch = 0.75f, speed = 0.95f),
            TonePreset("NATURAL_SOFT", "Soft & Natural", "Gentle human-like vocal profile", pitch = 0.9f, speed = 0.95f),
            TonePreset("STANDARD_STUDIO", "Standard Studio", "Default clear soundbox voice", pitch = 1.0f, speed = 1.0f),
            TonePreset("BRIGHT_CLEAR", "Bright & Crisp", "High clarity for noisy shop environments", pitch = 1.15f, speed = 1.0f),
            TonePreset("HIGH_ENERGETIC", "Energetic High", "Distinct high-pitch prompt", pitch = 1.35f, speed = 1.05f)
        )
    }

    @Synchronized
    fun ensureInitialized() {
        if (tts == null) {
            try {
                tts = TextToSpeech(context.applicationContext, this)
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing TextToSpeech: ${e.message}")
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            try {
                pendingLanguage?.let { setLanguage(it) } ?: run { setLanguage("en-IN") }
            } catch (e: Exception) {
                Log.w(TAG, "Error setting language onInit: ${e.message}")
            }
        } else {
            isInitialized = false
            Log.e(TAG, "TTS onInit failed with status: $status")
        }
    }

    fun setLanguage(languageCode: String): Boolean {
        val locale = Locale.forLanguageTag(languageCode)
        return setLanguage(locale)
    }

    fun setLanguage(locale: Locale): Boolean {
        pendingLanguage = locale
        ensureInitialized()
        if (!isInitialized) return false

        return try {
            val t = tts ?: return false
            val res1 = t.setLanguage(locale)
            if (res1 >= TextToSpeech.LANG_AVAILABLE) {
                return true
            }

            val baseLocale = Locale(locale.language)
            val res2 = t.setLanguage(baseLocale)
            if (res2 >= TextToSpeech.LANG_AVAILABLE) {
                return true
            }

            t.setLanguage(Locale("en", "IN"))
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed setLanguage: ${e.message}")
            false
        }
    }

    fun setSpeechRate(rate: Float) {
        ensureInitialized()
        if (isInitialized) {
            try {
                tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
            } catch (e: Exception) {
                Log.w(TAG, "Failed setSpeechRate: ${e.message}")
            }
        }
    }

    fun setPitch(pitch: Float) {
        ensureInitialized()
        if (isInitialized) {
            try {
                tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
            } catch (e: Exception) {
                Log.w(TAG, "Failed setPitch: ${e.message}")
            }
        }
    }

    fun setVoiceByName(voiceName: String): Boolean {
        ensureInitialized()
        if (!isInitialized || voiceName.isBlank()) return false
        return try {
            val voices = tts?.voices ?: return false
            val matchedVoice = voices.find { it.name.equals(voiceName, ignoreCase = true) }
            if (matchedVoice != null) {
                tts?.voice = matchedVoice
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed setVoiceByName: ${e.message}")
            false
        }
    }

    fun applyVoiceSettings(language: String, rate: Float, pitch: Float, voiceName: String = "") {
        setLanguage(language)
        setSpeechRate(rate)
        setPitch(pitch)
        if (voiceName.isNotBlank()) {
            setVoiceByName(voiceName)
        }
    }

    fun getAvailableVoices(languageCode: String): List<VoiceOption> {
        ensureInitialized()
        if (!isInitialized) return emptyList()
        val result = mutableListOf<VoiceOption>()
        try {
            val targetLocale = Locale.forLanguageTag(languageCode)
            val baseLang = targetLocale.language
            val voices = tts?.voices ?: return emptyList()

            for (v in voices) {
                if (v.locale.language == baseLang) {
                    val cleanName = v.name
                        .replace("language", "")
                        .replace("network", "(Online)")
                        .replace("local", "(Offline)")
                        .replace("_", " ")
                    val option = VoiceOption(
                        name = v.name,
                        displayName = cleanName.take(30),
                        language = v.locale.displayName,
                        isNetwork = v.isNetworkConnectionRequired
                    )
                    result.add(option)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed getAvailableVoices: ${e.message}")
        }
        return result
    }

    fun buildAnnouncementText(
        amount: Double,
        prefixEnabled: Boolean,
        prefix: String,
        suffixEnabled: Boolean,
        suffix: String,
        language: String = "en-IN",
        payerName: String? = null,
        bilingualEnabled: Boolean = false
    ): String {
        val amountWords = IndianNumberFormatter.format(amount, language)
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

        fun processTemplate(template: String): String =
            template.replace("{amount}", amountWords).replace("{time}", timeStr).replace("{name}", payerName ?: "")

        val coreAnnouncement = when {
            payerName != null -> when (language) {
                "hi-IN" -> "$payerName से $amountWords रुपये प्राप्त हुए"
                "bn-IN" -> "$payerName থেকে $amountWords টাকা পাওয়া গেছে"
                "mr-IN" -> "$payerName कडून $amountWords रुपये मिळाले"
                "ta-IN" -> "$payerName இடமிருந்து $amountWords ரூபாய் பெறப்பட்டது"
                "te-IN" -> "$payerName నుండి $amountWords రూపాయలు పొందబడ్డాయి"
                "gu-IN" -> "$payerName થી $amountWords રૂપિયા મળ્યા"
                "kn-IN" -> "$payerName ಇಂದ $amountWords ರೂಪಾಯಿ ಸ್ವೀಕರಿಸಲಾಗಿದೆ"
                "pa-IN" -> "$payerName ਤੋਂ $amountWords ਰੁਪਏ ਪ੍ਰਾਪਤ ਹੋਏ"
                "ml-IN" -> "$payerName ൽ നിന്ന് $amountWords രൂപ ലഭിച്ചു"
                else -> "Rupees $amountWords received from $payerName"
            }
            else -> when (language) {
                "hi-IN" -> "$amountWords रुपये प्राप्त हुए"
                "bn-IN" -> "$amountWords টাকা পাওয়া গেছে"
                "mr-IN" -> "$amountWords रुपये मिळाले"
                "ta-IN" -> "$amountWords ரூபாய் பெறப்பட்டது"
                "te-IN" -> "$amountWords రూపాయలు పొందబడ్డాయి"
                "gu-IN" -> "$amountWords રૂપિયા મળ્યા"
                "kn-IN" -> "$amountWords ರೂಪಾಯಿ ಸ್ವೀಕರಿಸಲಾಗಿದೆ"
                "pa-IN" -> "$amountWords ਰੁਪਏ ਪ੍ਰਾਪਤ ਹੋਏ"
                "ml-IN" -> "$amountWords രൂപ ലഭിച്ചു"
                else -> "Rupees $amountWords received"
            }
        }

        val englishSuffix = if (bilingualEnabled && language != "en-IN") {
            val engWords = IndianNumberFormatter.format(amount, "en-IN")
            if (payerName != null) {
                ". $engWords rupees received from $payerName"
            } else {
                ". $engWords rupees received"
            }
        } else ""

        return buildString {
            if (prefixEnabled && prefix.isNotBlank()) {
                append(processTemplate(prefix))
                append(". ")
            }
            append(coreAnnouncement)
            if (englishSuffix.isNotBlank()) {
                append(englishSuffix)
            }
            if (suffixEnabled && suffix.isNotBlank()) {
                append(". ")
                append(processTemplate(suffix))
            }
        }
    }

    fun buildDailySummaryText(count: Int, totalAmount: Double, language: String = "hi-IN"): String {
        val amountWords = IndianNumberFormatter.format(totalAmount, language)
        return when (language) {
            "hi-IN" -> "आज का हिसाब: कुल $count भुगतान प्राप्त हुए। कुल संग्रह $amountWords रुपये है।"
            "bn-IN" -> "আজকের হিসাব: মোট $count টি পেমেন্ট পেয়েছেন। মোট সংগ্রহ $amountWords টাকা।"
            "mr-IN" -> "आजचा हिशोब: एकूण $count पेमेंट मिळाले. एकूण जमा $amountWords रुपये आहे."
            "ta-IN" -> "இன்றைய சுருக்கம்: மொத்தம் $count பணம் பெறப்பட்டது. மொத்த தொகை $amountWords ரூபாய்."
            "te-IN" -> "ఈరోజు సారాంశం: మొత్తం $count చెల్లింపులు వచ్చాయి. మొత్తం సేకరణ $amountWords రూపాయలు."
            "gu-IN" -> "આજનો હિસાબ: કુલ $count પેમેન્ટ મળ્યા. કુલ રકમ $amountWords રૂપિયા છે."
            "kn-IN" -> "ಇಂದಿನ ಸಾರಾಂಶ: ಒಟ್ಟು $count ಪಾವತಿಗಳು ಬಂದಿವೆ. ಒಟ್ಟು ಮೊತ್ತ $amountWords ರೂಪಾಯಿ."
            "pa-IN" -> "ਅੱਜ ਦਾ ਹਿਸਾਬ: ਕੁੱਲ $count ਭੁਗਤਾਨ ਪ੍ਰਾਪਤ ਹੋਏ। ਕੁੱਲ ਰਕਮ $amountWords ਰੁਪਏ ਹੈ।"
            "ml-IN" -> "ഇന്നത്തെ കണക്ക്: ആകെ $count പേയ്‌മെന്റുകൾ ലഭിച്ചു. ആകെ തുക $amountWords രൂപ."
            else -> "Today's summary: Total $count payments received. Total collection is Rupees $amountWords."
        }
    }

    private fun requestAudioFocus() {
        try {
            audioManager?.let { am ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attrs)
                        .build()
                    audioFocusRequest = req
                    am.requestAudioFocus(req)
                } else {
                    @Suppress("DEPRECATION")
                    am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request failed: ${e.message}")
        }
    }

    private fun releaseAudioFocus() {
        try {
            audioManager?.let { am ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                } else {
                    @Suppress("DEPRECATION")
                    am.abandonAudioFocus(null)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus release failed: ${e.message}")
        }
    }

    suspend fun announceSuspend(
        text: String,
        volume: Int,
        rate: Float? = null,
        pitch: Float? = null,
        voiceName: String? = null
    ) {
        ensureInitialized()
        if (!isInitialized) {
            var count = 0
            while (!isInitialized && count < 25) {
                kotlinx.coroutines.delay(100)
                count++
            }
        }
        if (!isInitialized) {
            Log.e(TAG, "Cannot announce: TTS failed to initialize")
            return
        }

        requestAudioFocus()
        try {
            rate?.let { setSpeechRate(it) }
            pitch?.let { setPitch(it) }
            voiceName?.let { setVoiceByName(it) }

            val utteranceId = UUID.randomUUID().toString()
            val completion = CompletableDeferred<Unit>()

            setVolumeMax(volume)

            val currentTts = tts ?: return
            currentTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) completion.complete(Unit)
                }
                override fun onError(id: String?) {
                    if (id == utteranceId) completion.complete(Unit)
                }
            })

            val speakResult = currentTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (speakResult == TextToSpeech.ERROR) {
                Log.e(TAG, "TTS speak returned ERROR code, retrying after reinit")
                isInitialized = false
                tts = null
                ensureInitialized()
            } else {
                withTimeoutOrNull(15000L) {
                    completion.await()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during announceSuspend: ${e.message}", e)
        } finally {
            restoreVolume()
            releaseAudioFocus()
        }
    }

    fun announce(
        text: String,
        volume: Int,
        rate: Float? = null,
        pitch: Float? = null,
        voiceName: String? = null
    ) {
        ensureInitialized()
        if (!isInitialized) return
        try {
            requestAudioFocus()
            rate?.let { setSpeechRate(it) }
            pitch?.let { setPitch(it) }
            voiceName?.let { setVoiceByName(it) }

            setVolumeMax(volume)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        } catch (e: Exception) {
            Log.e(TAG, "Exception in announce: ${e.message}")
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS: ${e.message}")
        } finally {
            restoreVolume()
            releaseAudioFocus()
        }
    }

    private fun setVolumeMax(volumePercent: Int) {
        try {
            audioManager?.let { am ->
                val stream = AudioManager.STREAM_MUSIC
                val maxVol = am.getStreamMaxVolume(stream)
                previousVolume = am.getStreamVolume(stream)
                val targetVol = (maxVol * volumePercent / 100.0).toInt().coerceIn(0, maxVol)
                am.setStreamVolume(stream, targetVol, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set volume: ${e.message}")
        }
    }

    private fun restoreVolume() {
        try {
            audioManager?.let { am ->
                if (previousVolume >= 0) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
                    previousVolume = -1
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore volume: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            isInitialized = false
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.w(TAG, "TTS shutdown error: ${e.message}")
        }
    }
}

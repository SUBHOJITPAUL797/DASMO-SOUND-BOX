package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.media.SoundPool
import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sin

object ChimePlayer {

    private const val TAG = "ChimePlayer"
    private const val SAMPLE_RATE = 22050

    val CHIME_OPTIONS = listOf(
        "PAYTM_DING_DONG" to "Classic SoundBox (Ding-Dong)",
        "CASH_BELL" to "Cash Register Bell",
        "SYNTH_UPBEAT" to "Upbeat 3-Note Chime",
        "SYSTEM_DEFAULT" to "Phone Default Alert",
        "MUTE" to "Mute (No Chime)"
    )

    private val soundMap = ConcurrentHashMap<String, Int>()
    private val loadedSoundIds = ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var soundPool: SoundPool? = null
    @Volatile private var isInitialized = false

    @Volatile var isLoaded = false
        private set

    @Volatile var lastPlaybackStreamId: Int = -1
        internal set

    @Volatile var lastPlaybackFallback: Boolean = false
        internal set

    @Synchronized
    fun init(context: Context) {
        if (isInitialized && soundPool != null) return

        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build()

            // Attach listener BEFORE calling load() to prevent missing fast load callbacks
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    loadedSoundIds.add(sampleId)
                    val targetCount = soundMap.size
                    if (targetCount > 0 && loadedSoundIds.size >= targetCount) {
                        isLoaded = true
                    }
                    Log.d(TAG, "SoundPool sample $sampleId loaded successfully")
                } else {
                    Log.w(TAG, "SoundPool sample $sampleId load failed with status $status")
                }
            }

            soundPool = pool

            val chimesDir = File(context.cacheDir, "chimes")
            if (!chimesDir.exists()) {
                chimesDir.mkdirs()
            }

            // Pre-generate and pre-load all custom chime files
            val dingDongFile = File(chimesDir, "paytm_ding_dong.wav")
            if (!dingDongFile.exists() || dingDongFile.length() < 44) {
                writeWavFile(dingDongFile, generatePaytmDingDongSamples())
            }
            val dingDongId = pool.load(dingDongFile.absolutePath, 1)
            if (dingDongId > 0) soundMap["PAYTM_DING_DONG"] = dingDongId

            val cashBellFile = File(chimesDir, "cash_bell.wav")
            if (!cashBellFile.exists() || cashBellFile.length() < 44) {
                writeWavFile(cashBellFile, generateCashBellSamples())
            }
            val cashBellId = pool.load(cashBellFile.absolutePath, 1)
            if (cashBellId > 0) soundMap["CASH_BELL"] = cashBellId

            val synthFile = File(chimesDir, "synth_upbeat.wav")
            if (!synthFile.exists() || synthFile.length() < 44) {
                writeWavFile(synthFile, generateSynthUpbeatSamples())
            }
            val synthId = pool.load(synthFile.absolutePath, 1)
            if (synthId > 0) soundMap["SYNTH_UPBEAT"] = synthId

            isInitialized = true
        } catch (e: Throwable) {
            Log.w(TAG, "SoundPool initialization fallback: ${e.message}")
        }
    }

    suspend fun play(context: Context, chimeStyle: String, volumePercent: Int = 100): Boolean {
        if (chimeStyle.equals("MUTE", ignoreCase = true)) {
            lastPlaybackStreamId = 0
            lastPlaybackFallback = false
            return true
        }

        try {
            val styleKey = chimeStyle.uppercase()
            if (styleKey == "SYSTEM_DEFAULT") {
                lastPlaybackStreamId = 0
                lastPlaybackFallback = true
                playSystemDefault(context)
                return true
            }

            if (!isInitialized || soundPool == null) {
                withContext(Dispatchers.IO) {
                    init(context.applicationContext)
                }
            }

            val soundId = soundMap[styleKey]
            val currentPool = soundPool
            var streamId = 0

            if (currentPool != null && soundId != null && soundId > 0) {
                val clampedVolPercent = volumePercent.coerceIn(10, 100)
                val vol = (clampedVolPercent / 100.0f) * 0.9f
                streamId = currentPool.play(soundId, vol, vol, 1, 0, 1.0f)
            }

            lastPlaybackStreamId = streamId

            if (streamId != 0) {
                lastPlaybackFallback = false
                return true
            } else {
                Log.d(TAG, "SoundPool play returned stream 0 for $styleKey (loaded=${soundId != null && loadedSoundIds.contains(soundId)}), falling back to system default")
                lastPlaybackFallback = true
                playSystemDefault(context)
                return false
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Chime playback fallback: ${e.message}")
            lastPlaybackStreamId = 0
            lastPlaybackFallback = true
            playSystemDefault(context)
            return false
        }
    }

    fun isSoundLoaded(chimeStyle: String): Boolean {
        val soundId = soundMap[chimeStyle.uppercase()] ?: return false
        return loadedSoundIds.contains(soundId)
    }

    @VisibleForTesting
    fun notifySoundLoadedForTesting(soundId: Int) {
        loadedSoundIds.add(soundId)
        if (soundMap.isNotEmpty() && loadedSoundIds.size >= soundMap.size) {
            isLoaded = true
        }
    }

    private fun playSystemDefault(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
            ringtone?.play()
        } catch (e: Throwable) {
            Log.w(TAG, "System ringtone playback failed: ${e.message}")
        }
    }

    @Synchronized
    fun release() {
        try {
            soundPool?.setOnLoadCompleteListener(null)
            soundPool?.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Error releasing SoundPool: ${e.message}")
        } finally {
            soundPool = null
            soundMap.clear()
            loadedSoundIds.clear()
            isInitialized = false
            isLoaded = false
            lastPlaybackStreamId = -1
            lastPlaybackFallback = false
        }
    }

    private fun generatePaytmDingDongSamples(): ShortArray {
        val note1 = generateTone(1046.0, 160, 0.9f, decay = false)
        val pause = ShortArray((SAMPLE_RATE * 0.03).toInt()) // 30ms gap
        val note2 = generateTone(1318.0, 260, 0.9f, decay = true)

        val fullTrack = ShortArray(note1.size + pause.size + note2.size)
        System.arraycopy(note1, 0, fullTrack, 0, note1.size)
        System.arraycopy(pause, 0, fullTrack, note1.size, pause.size)
        System.arraycopy(note2, 0, fullTrack, note1.size + pause.size, note2.size)
        return fullTrack
    }

    private fun generateCashBellSamples(): ShortArray {
        return generateTone(1760.0, 380, 0.9f, decay = true)
    }

    private fun generateSynthUpbeatSamples(): ShortArray {
        val n1 = generateTone(784.0, 80, 0.9f, decay = false)
        val n2 = generateTone(1046.0, 80, 0.9f, decay = false)
        val n3 = generateTone(1318.0, 200, 0.9f, decay = true)

        val combined = ShortArray(n1.size + n2.size + n3.size)
        System.arraycopy(n1, 0, combined, 0, n1.size)
        System.arraycopy(n2, 0, combined, n1.size, n2.size)
        System.arraycopy(n3, 0, combined, n1.size + n2.size, n3.size)
        return combined
    }

    private fun generateTone(freqHz: Double, durationMs: Int, volume: Float, decay: Boolean): ShortArray {
        val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val angle = 2.0 * Math.PI * freqHz * t
            val amp = sin(angle)

            val envelope = when {
                i < 150 -> i / 150.0
                decay -> {
                    val remaining = numSamples - i
                    (remaining.toDouble() / numSamples)
                }
                i > numSamples - 150 -> (numSamples - i) / 150.0
                else -> 1.0
            }

            val sampleVal = (amp * envelope * volume * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            samples[i] = sampleVal.toShort()
        }
        return samples
    }

    private fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int = SAMPLE_RATE) {
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
        val blockAlign = numChannels * (bitsPerSample / 8)
        val subchunk2Size = samples.size * 2
        val chunkSize = 36 + subchunk2Size

        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(chunkSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size for PCM
            header.putShort(1.toShort()) // AudioFormat 1 = PCM
            header.putShort(numChannels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray())
            header.putInt(subchunk2Size)
            fos.write(header.array())

            val sampleBuffer = ByteBuffer.allocate(subchunk2Size).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                sampleBuffer.putShort(sample)
            }
            fos.write(sampleBuffer.array())
        }
    }
}

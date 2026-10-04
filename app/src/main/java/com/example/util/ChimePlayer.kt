package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.RingtoneManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun play(context: Context, chimeStyle: String, volumePercent: Int = 100) = withContext(Dispatchers.IO) {
        if (chimeStyle.equals("MUTE", ignoreCase = true)) return@withContext

        try {
            when (chimeStyle.uppercase()) {
                "PAYTM_DING_DONG" -> playPaytmDingDong(volumePercent)
                "CASH_BELL" -> playCashBell(volumePercent)
                "SYNTH_UPBEAT" -> playSynthUpbeat(volumePercent)
                else -> playSystemDefault(context)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Custom chime generation error, fallback to system: ${e.message}")
            playSystemDefault(context)
        }
    }

    private fun playSystemDefault(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
            ringtone?.play()
        } catch (e: Exception) {
            Log.w(TAG, "System ringtone playback failed: ${e.message}")
        }
    }

    private fun playPaytmDingDong(volumePercent: Int) {
        // High note (1046 Hz - C6) for 160ms, then Higher note (1318 Hz - E6) for 240ms
        val vol = (volumePercent.coerceIn(10, 100) / 100.0f) * 0.9f
        val note1 = generateTone(1046.0, 160, vol, decay = false)
        val pause = ShortArray((SAMPLE_RATE * 0.03).toInt()) // 30ms gap
        val note2 = generateTone(1318.0, 260, vol, decay = true)

        val fullTrack = ShortArray(note1.size + pause.size + note2.size)
        System.arraycopy(note1, 0, fullTrack, 0, note1.size)
        System.arraycopy(pause, 0, fullTrack, note1.size, pause.size)
        System.arraycopy(note2, 0, fullTrack, note1.size + pause.size, note2.size)

        playPcmTrack(fullTrack)
    }

    private fun playCashBell(volumePercent: Int) {
        val vol = (volumePercent.coerceIn(10, 100) / 100.0f) * 0.9f
        // Bell chime at 1760 Hz with bell decay
        val note = generateTone(1760.0, 380, vol, decay = true)
        playPcmTrack(note)
    }

    private fun playSynthUpbeat(volumePercent: Int) {
        val vol = (volumePercent.coerceIn(10, 100) / 100.0f) * 0.9f
        // G5 (784Hz) -> C6 (1046Hz) -> E6 (1318Hz)
        val n1 = generateTone(784.0, 80, vol, decay = false)
        val n2 = generateTone(1046.0, 80, vol, decay = false)
        val n3 = generateTone(1318.0, 200, vol, decay = true)

        val combined = ShortArray(n1.size + n2.size + n3.size)
        System.arraycopy(n1, 0, combined, 0, n1.size)
        System.arraycopy(n2, 0, combined, n1.size, n2.size)
        System.arraycopy(n3, 0, combined, n1.size + n2.size, n3.size)

        playPcmTrack(combined)
    }

    private fun generateTone(freqHz: Double, durationMs: Int, volume: Float, decay: Boolean): ShortArray {
        val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val angle = 2.0 * Math.PI * freqHz * t
            var amp = sin(angle)

            // Envelope to avoid audio clicks
            val envelope = when {
                i < 150 -> i / 150.0 // quick fade in
                decay -> {
                    val remaining = numSamples - i
                    (remaining.toDouble() / numSamples)
                }
                i > numSamples - 150 -> (numSamples - i) / 150.0 // quick fade out
                else -> 1.0
            }

            val sampleVal = (amp * envelope * volume * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            samples[i] = sampleVal.toShort()
        }
        return samples
    }

    private fun playPcmTrack(pcmData: ShortArray) {
        var track: AudioTrack? = null
        try {
            val bufferSize = pcmData.size * 2
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            track = AudioTrack(
                attrs,
                format,
                bufferSize,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            track.write(pcmData, 0, pcmData.size)
            track.play()
            Thread.sleep((pcmData.size.toDouble() / SAMPLE_RATE * 1000).toLong() + 30)
        } catch (e: Throwable) {
            Log.w(TAG, "AudioTrack play error: ${e.message}")
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (e: Throwable) {}
        }
    }
}

package com.example.harness

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Test double / spy for TtsEngine capturing speech parameters, utterance IDs,
 * and high-precision invocation timestamps.
 */
class SpyTtsEngine {

    data class TtsInvocation(
        val text: String,
        val utteranceId: String,
        val volume: Int,
        val language: String = "hi-IN",
        val rate: Float = 1.0f,
        val pitch: Float = 1.0f,
        val voiceName: String = "",
        val timestampNano: Long = System.nanoTime()
    )

    private val _history = CopyOnWriteArrayList<TtsInvocation>()
    val history: List<TtsInvocation> get() = _history

    val callCount: Int get() = _history.size
    val lastInvocation: TtsInvocation? get() = _history.lastOrNull()

    /**
     * Records a simulated TTS speech invocation.
     * Returns the exact nanosecond timestamp when speech was invoked.
     */
    fun recordSpeak(
        text: String,
        utteranceId: String,
        volume: Int,
        language: String = "hi-IN",
        rate: Float = 1.0f,
        pitch: Float = 1.0f,
        voiceName: String = ""
    ): Long {
        val now = System.nanoTime()
        _history.add(
            TtsInvocation(
                text = text,
                utteranceId = utteranceId,
                volume = volume,
                language = language,
                rate = rate,
                pitch = pitch,
                voiceName = voiceName,
                timestampNano = now
            )
        )
        return now
    }

    fun hasSpokenContaining(fragment: String): Boolean {
        return _history.any { it.text.contains(fragment, ignoreCase = true) }
    }

    fun reset() {
        _history.clear()
    }
}

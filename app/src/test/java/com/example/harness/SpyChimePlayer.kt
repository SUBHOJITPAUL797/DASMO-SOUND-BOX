package com.example.harness

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Test double / spy for ChimePlayer capturing playback parameters and
 * high-precision trigger timestamps.
 */
class SpyChimePlayer {

    data class ChimeInvocation(
        val soundType: String,
        val volumePercent: Int,
        val timestampNano: Long = System.nanoTime()
    )

    private val _history = CopyOnWriteArrayList<ChimeInvocation>()
    val history: List<ChimeInvocation> get() = _history
    val invocations: List<ChimeInvocation> get() = _history

    val callCount: Int get() = _history.size
    val lastInvocation: ChimeInvocation? get() = _history.lastOrNull()

    /**
     * Records a simulated chime playback call.
     * Returns the exact nanosecond timestamp when playback was triggered.
     */
    fun recordPlay(soundType: String, volumePercent: Int): Long {
        val now = System.nanoTime()
        _history.add(ChimeInvocation(soundType, volumePercent, now))
        return now
    }

    fun hasPlayed(soundType: String): Boolean {
        return _history.any { it.soundType.equals(soundType, ignoreCase = true) }
    }

    fun reset() {
        _history.clear()
    }
}

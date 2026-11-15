package com.example.harness

import org.junit.Assert.assertTrue

/**
 * High-precision telemetry model capturing nanosecond timestamps across the
 * soundbox payment processing pipeline.
 *
 * Latency requirement: Ingestion to Audio/TTS trigger must be strictly < 100ms.
 */
data class PipelineTelemetry(
    val injectionNano: Long = 0L,
    val parserCompletedNano: Long = 0L,
    val dedupCheckedNano: Long = 0L,
    val chimeTriggeredNano: Long = 0L,
    val ttsInvokedNano: Long = 0L,
    val transactionSavedNano: Long = 0L,
    val isDuplicate: Boolean = false,
    val status: String = "SUCCESS",
    val announcedText: String? = null,
    val parsedAmount: Double? = null,
    val parsedPayer: String? = null,
    val parsedRefId: String? = null
) {
    /**
     * Elapsed time from notification/SMS ingestion to ChimePlayer trigger in milliseconds.
     * Returns Double.MAX_VALUE if chime was not triggered, preventing false-pass assertions.
     */
    val audioTriggerLatencyMs: Double
        get() = if (chimeTriggeredNano > 0L && injectionNano > 0L) {
            (chimeTriggeredNano - injectionNano) / 1_000_000.0
        } else {
            Double.MAX_VALUE
        }

    /**
     * Elapsed time from ingestion to TtsEngine.announceSuspend invocation in milliseconds.
     * Returns Double.MAX_VALUE if TTS was not invoked, preventing false-pass assertions.
     */
    val ttsSpeechLatencyMs: Double
        get() = if (ttsInvokedNano > 0L && injectionNano > 0L) {
            (ttsInvokedNano - injectionNano) / 1_000_000.0
        } else {
            Double.MAX_VALUE
        }

    /**
     * Total end-to-end pipeline latency from ingestion to transaction persistence in Room.
     * Returns Double.MAX_VALUE if transaction was not saved, preventing false-pass assertions.
     */
    val e2eTotalLatencyMs: Double
        get() = if (transactionSavedNano > 0L && injectionNano > 0L) {
            (transactionSavedNano - injectionNano) / 1_000_000.0
        } else {
            Double.MAX_VALUE
        }

    /**
     * Asserts that audio trigger latency is strictly under 100ms.
     * Explicitly verifies that chime audio was triggered, clock did not roll back,
     * and measured latency is strictly < 100.0ms.
     */
    fun assertSub100msLatency(tag: String = "AudioAnnouncement") {
        assertTrue(
            "[$tag] Audio chime must have triggered (chimeTriggeredNano > 0)",
            chimeTriggeredNano > 0L
        )
        assertTrue(
            "[$tag] Audio trigger latency cannot be negative (measured: ${audioTriggerLatencyMs}ms)",
            audioTriggerLatencyMs >= 0.0
        )
        assertTrue(
            "[$tag] Audio trigger latency must be strictly < 100.0ms. Measured: ${audioTriggerLatencyMs}ms",
            audioTriggerLatencyMs < 100.0
        )
    }

    /**
     * Asserts that both chime and TTS trigger latencies are strictly under 100ms.
     * Explicitly verifies that both audio channels were invoked, clocks did not roll back,
     * and both latencies are strictly < 100.0ms.
     */
    fun assertFullPipelineLatency(tag: String = "FullPipeline") {
        assertTrue(
            "[$tag] Audio chime must have triggered (chimeTriggeredNano > 0)",
            chimeTriggeredNano > 0L
        )
        assertTrue(
            "[$tag] Audio trigger latency cannot be negative (measured: ${audioTriggerLatencyMs}ms)",
            audioTriggerLatencyMs >= 0.0
        )
        assertTrue(
            "[$tag] Chime latency must be < 100.0ms. Measured: ${audioTriggerLatencyMs}ms",
            audioTriggerLatencyMs < 100.0
        )

        assertTrue(
            "[$tag] TTS speech must have been invoked (ttsInvokedNano > 0)",
            ttsInvokedNano > 0L
        )
        assertTrue(
            "[$tag] TTS speech latency cannot be negative (measured: ${ttsSpeechLatencyMs}ms)",
            ttsSpeechLatencyMs >= 0.0
        )
        assertTrue(
            "[$tag] TTS latency must be < 100.0ms. Measured: ${ttsSpeechLatencyMs}ms",
            ttsSpeechLatencyMs < 100.0
        )
    }
}

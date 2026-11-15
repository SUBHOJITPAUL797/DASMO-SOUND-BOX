package com.example.tier4_realworld

import com.example.harness.MockPaymentInjectionHarness
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tier 4: Real-World Scenarios — Objective Nano-Latency Benchmark Test.
 * Measures nanosecond-precision latency from mock payment notification injection
 * to ChimePlayer trigger and TtsEngine speech invocation.
 *
 * Latency specifications:
 * - Hard ceiling: 100% of runs must be strictly < 100.0ms.
 * - Statistical threshold: Mean latency < 25.0ms.
 * - Statistical threshold: P99 latency < 50.0ms.
 * - Strict verification: 100% of runs must have status == "ANNOUNCED" and chimeTriggeredNano > 0L.
 */
class ObjectiveNanoLatencyBenchmarkTest {

    private lateinit var harness: MockPaymentInjectionHarness

    @Before
    fun setUp() {
        harness = MockPaymentInjectionHarness()
    }

    @Test
    fun testSub100msObjectiveNanoLatencyBenchmark() = runBlocking {
        // 1. Expanded warm-up phase (20 runs) to thoroughly prime JVM classloader and JIT compiler
        repeat(20) { i ->
            harness.injectNotification(
                packageName = "com.phonepe.app",
                title = "PhonePe",
                text = "Received ₹${(i + 1) * 10} from Warmup Customer via PhonePe"
            )
            val warmupTele = harness.awaitAnnouncement(timeoutMs = 500)
            assertEquals("Warmup run #${i + 1} status must be ANNOUNCED", "ANNOUNCED", warmupTele.status)
            assertTrue("Warmup run #${i + 1} chime must have triggered", warmupTele.chimeTriggeredNano > 0L)
            harness.reset()
        }

        // 2. High-precision measurement phase (50 consecutive runs)
        val latencies = mutableListOf<Double>()
        val ttsLatencies = mutableListOf<Double>()

        for (run in 1..50) {
            val amount = (run * 20 + 50).toDouble()
            val payer = "Customer $run"
            val text = "Received ₹${amount.toInt()} from $payer via PhonePe"

            val injectionNano = harness.injectNotification(
                packageName = "com.phonepe.app",
                title = "PhonePe",
                text = text
            )

            val telemetry = harness.awaitAnnouncement(timeoutMs = 500)

            // Strict audit assertions: every run must be ANNOUNCED and chime must have genuinely triggered
            assertEquals("Run #$run status must be ANNOUNCED", "ANNOUNCED", telemetry.status)
            assertTrue("Run #$run chime must have triggered (chimeTriggeredNano > 0)", telemetry.chimeTriggeredNano > 0L)
            assertTrue("Run #$run TTS must have been invoked (ttsInvokedNano > 0)", telemetry.ttsInvokedNano > 0L)

            val audioLatencyMs = telemetry.audioTriggerLatencyMs
            val ttsLatencyMs = telemetry.ttsSpeechLatencyMs

            // Guard against clock skew or rollback
            assertTrue(
                "Run #$run audio trigger latency cannot be negative. Measured: ${audioLatencyMs}ms",
                audioLatencyMs >= 0.0
            )
            assertTrue(
                "Run #$run TTS speech latency cannot be negative. Measured: ${ttsLatencyMs}ms",
                ttsLatencyMs >= 0.0
            )

            // HARD CEILING ASSERTION: Every single payment MUST trigger audio in < 100.0ms!
            assertTrue(
                "Run #$run audio trigger latency must be < 100.0ms. Measured: ${audioLatencyMs}ms",
                audioLatencyMs < 100.0
            )

            assertTrue(
                "Run #$run TTS speech latency must be < 100.0ms. Measured: ${ttsLatencyMs}ms",
                ttsLatencyMs < 100.0
            )

            latencies.add(audioLatencyMs)
            ttsLatencies.add(ttsLatencyMs)

            harness.reset()
        }

        // 3. Statistical verification across all 50 runs
        val avgLatency = latencies.average()
        val sortedLatencies = latencies.sorted()
        val p50Latency = sortedLatencies[24]
        val p90Latency = sortedLatencies[44]
        val p99Latency = sortedLatencies[49]
        val maxLatency = sortedLatencies.last()

        val avgTtsLatency = ttsLatencies.average()

        println(
            """
            ========================================================================
            DASMO-SOUND-BOX HIGH-PRECISION LATENCY BENCHMARK RESULTS (50 RUNS)
            ========================================================================
            Audio Trigger Latency:
              - Mean (Average): ${String.format("%.3f", avgLatency)} ms
              - P50 (Median)  : ${String.format("%.3f", p50Latency)} ms
              - P90           : ${String.format("%.3f", p90Latency)} ms
              - P99           : ${String.format("%.3f", p99Latency)} ms
              - Maximum       : ${String.format("%.3f", maxLatency)} ms
            TTS Speech Latency:
              - Mean (Average): ${String.format("%.3f", avgTtsLatency)} ms
            ========================================================================
            """.trimIndent()
        )

        // Objective assertions
        assertTrue(
            "Average audio trigger latency must be < 25.0ms. Actual: ${avgLatency}ms",
            avgLatency < 25.0
        )

        assertTrue(
            "P99 audio trigger latency must be < 50.0ms. Actual: ${p99Latency}ms",
            p99Latency < 50.0
        )

        assertTrue(
            "Maximum latency must be strictly < 100.0ms. Actual: ${maxLatency}ms",
            maxLatency < 100.0
        )
    }
}

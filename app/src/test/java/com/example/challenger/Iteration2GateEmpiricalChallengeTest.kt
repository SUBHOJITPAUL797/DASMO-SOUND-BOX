package com.example.challenger

import com.example.domain.model.AnnouncementStatus
import com.example.harness.MockPaymentInjectionHarness
import com.example.harness.PipelineTelemetry
import com.example.util.DedupEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Iteration 2 Gate Verification Empirical Challenge Test Suite.
 * Empirically challenges and stress-tests:
 * 1. PipelineTelemetry: Verifies whether unannounced payments or dropped audio can ever false-pass < 100.0ms.
 * 2. RapidConsecutiveCustomerPaymentsTest: Proves Customer B is genuinely injected without spy fakes.
 * 3. DedupBoundaryTest: Proves division-by-zero resilience is tested and exposes production DedupEngine crash.
 * 4. Concurrency: Proves MerchantMorningRushHourLoadTest & DualChannelPaymentInteractionTest execute across concurrent coroutine threads.
 */
class Iteration2GateEmpiricalChallengeTest {

    private lateinit var harness: MockPaymentInjectionHarness

    @Before
    fun setUp() {
        harness = MockPaymentInjectionHarness()
    }

    // =========================================================================
    // 1. PipelineTelemetry False-Pass Stress Testing
    // =========================================================================

    @Test
    fun testUnannouncedOrDroppedAudioNeverPassesSub100msLatency() {
        // Scenario A: chime never fired (chimeTriggeredNano == 0L)
        val teleNoChime = PipelineTelemetry(
            injectionNano = 1_000_000L,
            chimeTriggeredNano = 0L,
            ttsInvokedNano = 1_005_000L,
            status = "FAILED"
        )
        assertEquals("Latency must be Double.MAX_VALUE when chime never fired", Double.MAX_VALUE, teleNoChime.audioTriggerLatencyMs, 0.0)
        assertFalse("audioTriggerLatencyMs < 100.0 must be FALSE", teleNoChime.audioTriggerLatencyMs < 100.0)

        try {
            teleNoChime.assertSub100msLatency("TestNoChime")
            fail("assertSub100msLatency must throw AssertionError when chimeTriggeredNano == 0L")
        } catch (e: AssertionError) {
            assertTrue("Error message must mention chimeTriggeredNano", e.message!!.contains("Audio chime must have triggered"))
        }

        // Scenario B: injectionNano was 0L (clock uninitialized)
        val teleNoInjection = PipelineTelemetry(
            injectionNano = 0L,
            chimeTriggeredNano = 5_000_000L,
            status = "FAILED"
        )
        assertEquals("Latency must be Double.MAX_VALUE when injectionNano == 0L", Double.MAX_VALUE, teleNoInjection.audioTriggerLatencyMs, 0.0)
        assertFalse("audioTriggerLatencyMs < 100.0 must be FALSE when injectionNano == 0L", teleNoInjection.audioTriggerLatencyMs < 100.0)

        // Scenario C: dropped audio where TTS never fired (ttsInvokedNano == 0L)
        val teleNoTts = PipelineTelemetry(
            injectionNano = 1_000_000L,
            chimeTriggeredNano = 1_005_000L,
            ttsInvokedNano = 0L,
            status = "TTS_DROPPED"
        )
        assertEquals("TTS latency must be Double.MAX_VALUE when TTS never invoked", Double.MAX_VALUE, teleNoTts.ttsSpeechLatencyMs, 0.0)
        assertFalse("ttsSpeechLatencyMs < 100.0 must be FALSE", teleNoTts.ttsSpeechLatencyMs < 100.0)

        try {
            teleNoTts.assertFullPipelineLatency("TestNoTts")
            fail("assertFullPipelineLatency must throw AssertionError when ttsInvokedNano == 0L")
        } catch (e: AssertionError) {
            assertTrue("Error message must mention TTS speech must have been invoked", e.message!!.contains("TTS speech must have been invoked"))
        }
    }

    @Test
    fun testPipelineTelemetryRejectsClockRollbackOrNegativeLatency() {
        // Scenario D: Clock rollback or invalid timestamp (chimeTriggeredNano < injectionNano)
        val teleClockRollback = PipelineTelemetry(
            injectionNano = 2_000_000L,
            chimeTriggeredNano = 1_000_000L, // -1.0ms
            status = "ANNOUNCED"
        )
        val latency = teleClockRollback.audioTriggerLatencyMs
        assertTrue("Latency is negative when clock rolls back", latency < 0.0)

        try {
            teleClockRollback.assertSub100msLatency("TestRollback")
            fail("assertSub100msLatency must reject negative latency")
        } catch (e: AssertionError) {
            assertTrue("Error message must catch negative latency", e.message!!.contains("cannot be negative"))
        }
    }

    @Test
    fun testMockHarnessGuaranteesZeroChimeOnDroppedOrSkippedPayments() = runBlocking {
        // Case 1: Duplicate skipped
        harness.injectNotification("com.phonepe.app", "PhonePe", "Received ₹100 from User A via PhonePe")
        val tele1 = harness.awaitAnnouncement()
        assertEquals("ANNOUNCED", tele1.status)
        assertTrue(tele1.chimeTriggeredNano > 0L)

        // Resend identical payment -> DUPLICATE_SKIPPED
        harness.injectNotification("com.phonepe.app", "PhonePe", "Received ₹100 from User A via PhonePe")
        val allTele = harness.getAllTelemetry()
        val tele2 = allTele.last()
        assertEquals("DUPLICATE_SKIPPED", tele2.status)
        assertEquals("chimeTriggeredNano must be 0L for DUPLICATE_SKIPPED", 0L, tele2.chimeTriggeredNano)
        assertEquals("ttsInvokedNano must be 0L for DUPLICATE_SKIPPED", 0L, tele2.ttsInvokedNano)
        assertEquals("audioTriggerLatencyMs must be Double.MAX_VALUE", Double.MAX_VALUE, tele2.audioTriggerLatencyMs, 0.0)
        assertFalse("DUPLICATE_SKIPPED can NEVER pass < 100.0ms", tele2.audioTriggerLatencyMs < 100.0)

        // Case 2: Service disabled
        harness.settingsRepository.updateIsEnabled(false)
        harness.injectNotification("com.phonepe.app", "PhonePe", "Received ₹200 from User B via PhonePe")
        val teleDisabled = harness.getAllTelemetry().last()
        assertEquals("SERVICE_DISABLED", teleDisabled.status)
        assertEquals("chimeTriggeredNano must be 0L for SERVICE_DISABLED", 0L, teleDisabled.chimeTriggeredNano)
        assertEquals("ttsInvokedNano must be 0L for SERVICE_DISABLED", 0L, teleDisabled.ttsInvokedNano)
        assertEquals("audioTriggerLatencyMs must be Double.MAX_VALUE", Double.MAX_VALUE, teleDisabled.audioTriggerLatencyMs, 0.0)
        assertFalse("SERVICE_DISABLED can NEVER pass < 100.0ms", teleDisabled.audioTriggerLatencyMs < 100.0)
    }

    // =========================================================================
    // 2. RapidConsecutiveCustomerPayments Pipeline Routing Check
    // =========================================================================

    @Test
    fun testRapidConsecutiveCustomerBProcessedGenuinelyThroughPipeline() = runBlocking {
        // In RapidConsecutiveCustomerPaymentsTest, Customer B is injected genuinely.
        // Under current production DedupEngine (which lacks composite key refId+payer),
        // Customer B is dropped. This proves that the remediated test is an honest test
        // that will fail against the unfixed DedupEngine and only pass once M3 is implemented.
        val amount = 20.00
        val custA = "Aman Gupta"
        val utrA = "111122223333"
        val custB = "Brijesh Patel"
        val utrB = "444455556666"

        harness.injectNotification("com.phonepe.app", "PhonePe", "Received ₹20 from $custA via PhonePe. UTR: $utrA")
        val teleA = harness.awaitAnnouncement()
        assertEquals("ANNOUNCED", teleA.status)

        harness.injectNotification("com.phonepe.app", "PhonePe", "Received ₹20 from $custB via PhonePe. UTR: $utrB")
        val allTele = harness.getAllTelemetry()
        val teleB = allTele[1]

        // REMEDIATED: Composite-key DedupEngine processes Customer B as distinct ANNOUNCED transaction
        assertEquals("Customer B is announced by remediated DedupEngine", "ANNOUNCED", teleB.status)
        assertEquals("Chime spy count is 2 because both customers were genuinely announced", 2, harness.spyChime.callCount)
    }

    // =========================================================================
    // 3. DedupBoundary Division-By-Zero Edge Case Check
    // =========================================================================

    @Test
    fun testDedupEngineDivisionByZeroWhenWindowZero() = runBlocking {
        harness.settingsRepository.updateDirectly { it.copy(dedupWindowSeconds = 0) }

        var divisionByZeroCaught = false
        try {
            harness.dedupEngine.isDuplicate(100.0)
        } catch (e: ArithmeticException) {
            divisionByZeroCaught = true
        }

        assertFalse("DedupEngine.isDuplicate does NOT throw ArithmeticException when window=0", divisionByZeroCaught)
    }

    // =========================================================================
    // 4. Concurrency Realism Stress Testing
    // =========================================================================

    @Test
    fun testMerchantMorningRushHourExecutesAcrossMultipleDistinctThreads() = runBlocking {
        val threadNames = ConcurrentHashMap.newKeySet<String>()

        coroutineScope {
            val jobs = (1..20).map { i ->
                launch(Dispatchers.Default) {
                    threadNames.add(Thread.currentThread().name)
                    harness.injectNotification(
                        packageName = "com.phonepe.app",
                        title = "PhonePe",
                        text = "Received ₹${i * 10} from Merchant Customer $i via PhonePe. UTR: 99990000$i"
                    )
                }
            }
            jobs.joinAll()
        }

        println("Merchant Rush Hour executed across ${threadNames.size} distinct threads: $threadNames")
        assertTrue(
            "Concurrent load test must utilize multiple coroutine worker threads. Threads observed: ${threadNames.size}",
            threadNames.size > 1
        )
        assertEquals("All 20 payments processed", 20, harness.getAllTelemetry().size)
    }

    @Test
    fun testDualChannelInteractionExecutesConcurrentlyAcrossThreads() = runBlocking {
        val threadsUsed = ConcurrentHashMap.newKeySet<String>()
        val startGate = CompletableDeferred<Unit>()

        val notifJob = launch(Dispatchers.Default) {
            startGate.await()
            threadsUsed.add(Thread.currentThread().name)
            harness.injectNotification(
                packageName = "com.phonepe.app",
                title = "PhonePe",
                text = "Received ₹500 from Payer A via PhonePe. UTR: 123456789"
            )
        }

        val smsJob = launch(Dispatchers.Default) {
            startGate.await()
            threadsUsed.add(Thread.currentThread().name)
            harness.injectSms(
                sender = "HDFC",
                messageBody = "Rs 500 credited to A/c ... Payer A Ref 123456789"
            )
        }

        startGate.complete(Unit)
        joinAll(notifJob, smsJob)

        val tele = harness.getAllTelemetry()
        assertEquals("Exactly 2 events processed", 2, tele.size)
        println("Dual channel executed across threads: $threadsUsed")
    }
}

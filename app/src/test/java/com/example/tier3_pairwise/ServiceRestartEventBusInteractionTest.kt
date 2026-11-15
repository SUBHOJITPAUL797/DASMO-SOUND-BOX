package com.example.tier3_pairwise

import com.example.domain.model.AnnouncementStatus
import com.example.domain.model.PaymentEvent
import com.example.domain.model.SourceType
import com.example.harness.MockPaymentInjectionHarness
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tier 3: Cross-Feature Combinations — Service Restart + EventBus Ingestion.
 * Tests resolution of the dual-ingestion race condition where a stopped service
 * is started with initial Intent extras and also receives the event via PaymentEventBus.
 *
 * Expected behavior: Only 1 announcement plays; the 2nd ingestion is deduplicated.
 */
class ServiceRestartEventBusInteractionTest {

    private lateinit var harness: MockPaymentInjectionHarness

    @Before
    fun setUp() {
        harness = MockPaymentInjectionHarness()
    }

    @Test
    fun testDualIngestionPreventedOnServiceStart() = runBlocking {
        val amount = 1200.0
        val payer = "Deepak Verma"
        val refId = "112233445566"

        val sharedEvent = PaymentEvent(
            amount = amount,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "com.phonepe.app",
            sourceAppName = "PhonePe",
            payerName = payer,
            refId = refId,
            rawText = "Received ₹1200 from $payer"
        )

        // Simulate concurrent arrival: Intent extra ingestion and EventBus emit simultaneously
        val coroutineJob = launch {
            // Emulate EventBus subscription collection
            harness.paymentEventBus.events.collect { busEvent ->
                harness.executePipeline(busEvent, System.nanoTime(), System.nanoTime(), busEvent.payerName, busEvent.refId)
            }
        }
        delay(50)

        // 1. Ingest via Intent
        harness.executePipeline(sharedEvent, System.nanoTime(), System.nanoTime(), sharedEvent.payerName, sharedEvent.refId)

        // 2. Ingest via Bus emission
        harness.paymentEventBus.emit(sharedEvent)
        delay(100)

        // Verify that exactly 1 announcement was played
        assertEquals("Chime must play exactly once despite dual ingestion", 1, harness.spyChime.callCount)
        assertEquals("TTS must speak exactly once despite dual ingestion", 1, harness.spyTts.callCount)

        val txs = harness.transactionRepository.getAll()
        assertEquals(2, txs.size)
        assertEquals(AnnouncementStatus.ANNOUNCED.name, txs[0].status)
        assertEquals(AnnouncementStatus.DUPLICATE_SKIPPED.name, txs[1].status)

        coroutineJob.cancel()
    }

    @Test
    fun testReplayZeroPreventsHistoricalReplayOnRestart() = runBlocking {
        // Test that production PaymentEventBus configured with replay=0 does not resend old transactions
        val receivedOnRestart = CopyOnWriteArrayList<PaymentEvent>()
        val bus = com.example.service.PaymentEventBus()

        // 1. Prior session emits an event before restarted service collector starts
        val pastEvent = PaymentEvent(
            amount = 500.0,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "net.one97.paytm",
            sourceAppName = "Paytm",
            rawText = "Past payment"
        )
        bus.emit(pastEvent)

        // 2. Restarted service attaches new collector
        val job = launch {
            bus.events.collect { receivedOnRestart.add(it) }
        }
        delay(50)

        // Feature 8 / Milestone 3 requires replay = 0 on PaymentEventBus.
        // Newly attached collector must not receive historical events.
        assertEquals("Production PaymentEventBus with replay=0 must not replay past events to newly started collector", 0, receivedOnRestart.size)
        job.cancel()
    }
}

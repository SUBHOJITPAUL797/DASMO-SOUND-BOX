package com.example.tier4_realworld

import com.example.domain.model.AnnouncementStatus
import com.example.domain.model.PaymentEvent
import com.example.domain.model.SourceType
import com.example.harness.MockPaymentInjectionHarness
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tier 4: Real-World Scenarios — Service Low-Memory Kill & Sticky Restart Recovery.
 * Simulates Android OS terminating SoundBoxForegroundService under RAM pressure,
 * followed by a START_STICKY process recreation.
 *
 * Success criteria:
 * - Replay = 0 prevents re-announcement of past transactions upon subscriber recreation.
 * - Service resumes live listening and announces subsequent payments immediately.
 */
class ServiceStickyRestartRecoveryTest {

    private lateinit var harness: MockPaymentInjectionHarness

    @Before
    fun setUp() {
        harness = MockPaymentInjectionHarness()
    }

    @Test
    fun testStickyRestartPreventsReplayAndResumesProcessing() = runBlocking {
        // Test production PaymentEventBus from harness (Feature 8: replay = 0)
        val productionBus = harness.paymentEventBus

        // 1. Initial service session: processes 3 transactions
        val activeSubscriberEvents = CopyOnWriteArrayList<PaymentEvent>()
        var sessionJob = launch {
            productionBus.events.collect { activeSubscriberEvents.add(it) }
        }
        delay(50)

        for (i in 1..3) {
            val event = PaymentEvent(
                amount = (i * 100).toDouble(),
                sourceType = SourceType.NOTIFICATION,
                sourceApp = "com.phonepe.app",
                sourceAppName = "PhonePe",
                payerName = "Payer $i",
                rawText = "Received ₹${i * 100}"
            )
            productionBus.emit(event)
            harness.executePipeline(event, System.nanoTime(), System.nanoTime(), "Payer $i", null)
        }
        delay(100)

        assertEquals("First session processed 3 events", 3, activeSubscriberEvents.size)
        assertEquals(3, harness.spyChime.callCount)

        // 2. Simulate OS low-memory termination (coroutine cancelled, service destroyed)
        sessionJob.cancel()
        delay(50)

        // 3. Service restarted by OS via START_STICKY
        // New subscriber attaches to production event bus
        val recoveredSubscriberEvents = CopyOnWriteArrayList<PaymentEvent>()
        sessionJob = launch {
            productionBus.events.collect { recoveredSubscriberEvents.add(it) }
        }
        delay(50)

        // CRITICAL: Production bus with replay=0 must ensure NO historical events are replayed!
        assertEquals("Recovered service must NOT receive replayed past events from production PaymentEventBus", 0, recoveredSubscriberEvents.size)

        // 4. New incoming payment arrives after recovery
        val newEvent = PaymentEvent(
            amount = 500.0,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "net.one97.paytm",
            sourceAppName = "Paytm",
            payerName = "New Customer",
            rawText = "Received ₹500"
        )
        productionBus.emit(newEvent)
        val tele = harness.executePipeline(newEvent, System.nanoTime(), System.nanoTime(), "New Customer", null)
        delay(50)

        assertEquals("New event received by recovered service", 1, recoveredSubscriberEvents.size)
        assertEquals(AnnouncementStatus.ANNOUNCED.name, tele.status)
        assertEquals("Total chimes must be 4 (3 prior + 1 new)", 4, harness.spyChime.callCount)

        sessionJob.cancel()
    }
}

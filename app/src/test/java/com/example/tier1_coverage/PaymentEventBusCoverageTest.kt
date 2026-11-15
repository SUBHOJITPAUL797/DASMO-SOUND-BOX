package com.example.tier1_coverage

import com.example.domain.model.PaymentEvent
import com.example.domain.model.SourceType
import com.example.service.PaymentEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tier 1: Feature Coverage for PaymentEventBus.
 * Validates live event delivery, multi-subscriber distribution, FIFO ordering,
 * and buffer drop oldest non-blocking behavior.
 */
class PaymentEventBusCoverageTest {

    @Test
    fun testLiveEventDelivery() = runBlocking {
        val bus = PaymentEventBus()
        val received = CopyOnWriteArrayList<PaymentEvent>()

        val job = launch {
            bus.events.collect { received.add(it) }
        }
        delay(50) // allow subscriber to attach

        val event = PaymentEvent(
            amount = 100.0,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "com.phonepe.app",
            sourceAppName = "PhonePe",
            payerName = "Amit Shah",
            refId = "123456789012",
            rawText = "Received ₹100 from Amit Shah"
        )
        bus.emit(event)
        delay(50)

        assertEquals("Expected exactly 1 event received", 1, received.size)
        assertEquals(100.0, received[0].amount, 0.001)
        assertEquals("Amit Shah", received[0].payerName)
        job.cancel()
    }

    @Test
    fun testMultiSubscriberBroadcast() = runBlocking {
        val bus = PaymentEventBus()
        val sub1Events = CopyOnWriteArrayList<PaymentEvent>()
        val sub2Events = CopyOnWriteArrayList<PaymentEvent>()

        val job1 = launch { bus.events.collect { sub1Events.add(it) } }
        val job2 = launch { bus.events.collect { sub2Events.add(it) } }
        delay(50)

        val event = PaymentEvent(
            amount = 50.0,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "net.one97.paytm",
            sourceAppName = "Paytm",
            rawText = "Rs 50 received"
        )
        bus.emit(event)
        delay(50)

        assertEquals("Subscriber 1 must receive event", 1, sub1Events.size)
        assertEquals("Subscriber 2 must receive event", 1, sub2Events.size)
        assertEquals(50.0, sub1Events[0].amount, 0.001)
        assertEquals(50.0, sub2Events[0].amount, 0.001)

        job1.cancel()
        job2.cancel()
    }

    @Test
    fun testBufferDropOldestNoSuspend() = runBlocking {
        val bus = PaymentEventBus()
        var completedEmits = 0

        // Emit 100 events rapidly without active subscriber; buffer capacity is 64 with DROP_OLDEST
        val elapsedMs = kotlin.system.measureTimeMillis {
            kotlinx.coroutines.withTimeout(1000L) {
                for (i in 1..100) {
                    val event = PaymentEvent(
                        amount = i.toDouble(),
                        sourceType = SourceType.NOTIFICATION,
                        sourceApp = "com.phonepe.app",
                        sourceAppName = "PhonePe",
                        rawText = "Event $i"
                    )
                    bus.emit(event)
                    completedEmits++
                }
            }
        }

        assertEquals("All 100 events must be emitted into DROP_OLDEST buffer without suspending", 100, completedEmits)
        assertEquals("Buffer test starts and completes with 0 active subscribers", 0, bus.events.subscriptionCount.value)
        assertTrue("Rapid non-blocking emission of 100 events must complete in < 500ms, took ${elapsedMs}ms", elapsedMs < 500L)
    }

    @Test
    fun testSequentialFifoDelivery() = runBlocking {
        val bus = PaymentEventBus()
        val received = CopyOnWriteArrayList<Double>()

        val job = launch {
            bus.events.collect { received.add(it.amount) }
        }
        delay(50)

        val amounts = listOf(10.0, 20.0, 30.0, 40.0, 50.0)
        for (amt in amounts) {
            bus.emit(
                PaymentEvent(
                    amount = amt,
                    sourceType = SourceType.NOTIFICATION,
                    sourceApp = "com.phonepe.app",
                    sourceAppName = "PhonePe",
                    rawText = "Paid $amt"
                )
            )
        }
        delay(100)

        assertEquals("Should receive all 5 amounts in FIFO order", amounts, received.toList())
        job.cancel()
    }

    @Test
    fun testEventFieldsIntegrity() = runBlocking {
        val bus = PaymentEventBus()
        var receivedEvent: PaymentEvent? = null

        val job = launch {
            bus.events.collect { receivedEvent = it }
        }
        delay(50)

        val original = PaymentEvent(
            amount = 999.99,
            currency = "INR",
            sourceType = SourceType.SMS,
            sourceApp = "SBI",
            sourceAppName = "SBI Bank SMS",
            payerName = "Vikram Aditya",
            refId = "998877665544",
            rawText = "A/c credited with 999.99",
            timestamp = 1700000000000L,
            dedupKey = "999.99_17000"
        )
        bus.emit(original)
        delay(50)

        assertNotNull(receivedEvent)
        assertEquals(original.amount, receivedEvent!!.amount, 0.001)
        assertEquals(original.currency, receivedEvent!!.currency)
        assertEquals(original.sourceType, receivedEvent!!.sourceType)
        assertEquals(original.sourceApp, receivedEvent!!.sourceApp)
        assertEquals(original.sourceAppName, receivedEvent!!.sourceAppName)
        assertEquals(original.payerName, receivedEvent!!.payerName)
        assertEquals(original.refId, receivedEvent!!.refId)
        assertEquals(original.rawText, receivedEvent!!.rawText)
        assertEquals(original.timestamp, receivedEvent!!.timestamp)
        assertEquals(original.dedupKey, receivedEvent!!.dedupKey)

        job.cancel()
    }
}

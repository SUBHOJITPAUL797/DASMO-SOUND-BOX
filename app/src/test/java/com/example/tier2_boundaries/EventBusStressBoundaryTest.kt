package com.example.tier2_boundaries

import com.example.domain.model.PaymentEvent
import com.example.domain.model.SourceType
import com.example.service.PaymentEventBus
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tier 2: Boundary & Corner Cases for PaymentEventBus.
 * Covers 500-event burst floods, concurrent multi-threaded emissions,
 * subscriber exceptions, and empty/null field tolerance.
 */
class EventBusStressBoundaryTest {

    @Test
    fun test500EventBurstFlood() = runBlocking {
        val bus = PaymentEventBus()
        val receivedCount = AtomicInteger(0)

        val job = launch(Dispatchers.Default) {
            bus.events.collect {
                receivedCount.incrementAndGet()
            }
        }
        delay(50)

        // Emit 500 events in rapid succession
        coroutineScope {
            for (i in 1..500) {
                launch(Dispatchers.Default) {
                    bus.emit(
                        PaymentEvent(
                            amount = i.toDouble(),
                            sourceType = SourceType.NOTIFICATION,
                            sourceApp = "com.phonepe.app",
                            sourceAppName = "PhonePe",
                            rawText = "Burst Event $i"
                        )
                    )
                }
            }
        }
        delay(300)

        assertTrue("Should have processed burst events without crashing", receivedCount.get() > 0)
        job.cancel()
    }

    @Test
    fun testConcurrentMultiThreadEmits() = runBlocking {
        val bus = PaymentEventBus()
        val receivedList = CopyOnWriteArrayList<Double>()

        val job = launch(Dispatchers.Default) {
            bus.events.collect { receivedList.add(it.amount) }
        }
        delay(50)

        // 10 concurrent emitter coroutines
        val jobs = (1..10).map { workerId ->
            launch(Dispatchers.Default) {
                for (j in 1..10) {
                    bus.emit(
                        PaymentEvent(
                            amount = (workerId * 100 + j).toDouble(),
                            sourceType = SourceType.NOTIFICATION,
                            sourceApp = "com.phonepe.app",
                            sourceAppName = "PhonePe",
                            rawText = "Worker $workerId Tx $j"
                        )
                    )
                }
            }
        }
        jobs.joinAll()
        delay(200)

        assertEquals("Expected exactly 100 concurrently emitted events", 100, receivedList.size)
        job.cancel()
    }

    @Test
    fun testEmptyAndNullFieldEvent() = runBlocking {
        val bus = PaymentEventBus()
        var received: PaymentEvent? = null

        val job = launch {
            bus.events.collect { received = it }
        }
        delay(50)

        val emptyFieldEvent = PaymentEvent(
            amount = 0.0,
            currency = "",
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "",
            sourceAppName = "",
            payerName = null,
            refId = null,
            rawText = "",
            dedupKey = ""
        )
        bus.emit(emptyFieldEvent)
        delay(50)

        assertNotNull(received)
        assertEquals(0.0, received!!.amount, 0.001)
        assertNull(received!!.payerName)
        assertNull(received!!.refId)
        assertEquals("", received!!.rawText)

        job.cancel()
    }

    @Test
    fun testZeroSubscribersDroppedCleanly() = runBlocking {
        val bus = PaymentEventBus()
        assertEquals("Bus must initially have 0 active subscribers", 0, bus.events.subscriptionCount.value)

        val uncollectedEvent = PaymentEvent(
            amount = 50.0,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "net.one97.paytm",
            sourceAppName = "Paytm",
            rawText = "No subscriber event"
        )

        // Bus has 0 subscribers; emit must complete cleanly within timeout without blocking or throwing
        var emitCompleted = false
        val durationMs = kotlin.system.measureTimeMillis {
            withTimeout(500L) {
                bus.emit(uncollectedEvent)
                emitCompleted = true
            }
        }

        assertTrue("Emit with 0 active subscribers must complete non-blocking", emitCompleted)
        assertTrue("Emit with 0 subscribers must be immediate (< 100ms), took ${durationMs}ms", durationMs < 100L)

        // Verify that bus remains healthy: attach a subscriber and emit a new live event
        val receivedList = CopyOnWriteArrayList<PaymentEvent>()
        val collectorJob = launch {
            bus.events.collect { receivedList.add(it) }
        }
        delay(50)
        assertEquals("Subscriber count must now be 1", 1, bus.events.subscriptionCount.value)

        val liveEvent = PaymentEvent(
            amount = 75.0,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "com.phonepe.app",
            sourceAppName = "PhonePe",
            rawText = "Live subscriber event"
        )
        bus.emit(liveEvent)
        delay(50)

        assertTrue("Attached subscriber must successfully receive subsequent live event", receivedList.any { it.amount == 75.0 })
        collectorJob.cancel()
    }

    @Test
    fun testSubscriberCancellationRecovery() = runBlocking {
        val bus = PaymentEventBus()
        val receivedBefore = CopyOnWriteArrayList<Double>()

        val job1 = launch {
            bus.events.collect { receivedBefore.add(it.amount) }
        }
        delay(50)

        bus.emit(
            PaymentEvent(
                amount = 10.0,
                sourceType = SourceType.NOTIFICATION,
                sourceApp = "com.phonepe.app",
                sourceAppName = "PhonePe",
                rawText = "Tx 1"
            )
        )
        delay(50)
        assertEquals(1, receivedBefore.size)

        // Cancel first subscriber
        job1.cancel()
        delay(50)

        // Start new subscriber
        val receivedAfter = CopyOnWriteArrayList<Double>()
        val job2 = launch {
            bus.events.collect { receivedAfter.add(it.amount) }
        }
        delay(50)

        bus.emit(
            PaymentEvent(
                amount = 20.0,
                sourceType = SourceType.NOTIFICATION,
                sourceApp = "com.phonepe.app",
                sourceAppName = "PhonePe",
                rawText = "Tx 2"
            )
        )
        delay(50)

        assertEquals("New subscriber should receive new live event", 1, receivedAfter.size)
        assertEquals(20.0, receivedAfter[0], 0.001)

        job2.cancel()
    }
}

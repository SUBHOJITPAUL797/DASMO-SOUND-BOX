package com.example.tier4_realworld

import com.example.domain.model.AnnouncementStatus
import com.example.harness.MockPaymentInjectionHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tier 4: Real-World Scenarios — Merchant Morning Rush Hour Load Test.
 * Simulates a high-traffic morning rush at a retail merchant store where 20 customer
 * payments arrive across PhonePe, Google Pay, and Paytm concurrently.
 *
 * Success criteria:
 * - 20 payments processed without dropped frames or deadlock.
 * - All 20 payments saved in Room database.
 * - Queue worker never blocks or deadlocks under concurrent bursts.
 * - Sub-100ms hard latency ceiling respected for every payment.
 */
class MerchantMorningRushHourLoadTest {

    private lateinit var harness: MockPaymentInjectionHarness

    @Before
    fun setUp() {
        harness = MockPaymentInjectionHarness()
    }

    @Test
    fun testMorningRushHour20PaymentsBurstConcurrent() = runBlocking {
        val paymentApps = listOf(
            Triple("com.phonepe.app", "PhonePe", "Received ₹%d from Customer %d via PhonePe. UTR: 1000000000%02d"),
            Triple("com.google.android.apps.nbu.paisa.user", "Google Pay", "Customer %d sent you ₹ %d. UPI Ref 2000000000%02d"),
            Triple("net.one97.paytm", "Paytm", "Payment of Rs %d.00 received on Paytm QR from Customer %d. Ref 3000000000%02d")
        )

        // Launch 20 concurrent coroutines across Default thread pool
        coroutineScope {
            val jobs = (1..20).map { i ->
                launch(Dispatchers.Default) {
                    val app = paymentApps[(i - 1) % paymentApps.size]
                    val amount = (i * 15 + 10).toDouble()
                    val text = when (app.second) {
                        "PhonePe" -> String.format(app.third, amount.toInt(), i, i)
                        "Google Pay" -> String.format(app.third, i, amount.toInt(), i)
                        else -> String.format(app.third, amount.toInt(), i, i)
                    }
                    val title = if (app.second == "Google Pay") "Customer $i" else app.second

                    harness.injectNotification(
                        packageName = app.first,
                        title = title,
                        text = text
                    )
                }
            }
            jobs.joinAll()
        }

        // Verify all 20 payments processed without dropped frames or deadlock
        val allTelemetry = harness.getAllTelemetry()
        assertEquals("All 20 concurrent payments must be processed", 20, allTelemetry.size)

        val allTxs = harness.transactionRepository.getAll()
        assertEquals("Database must contain all 20 transactions", 20, allTxs.size)

        // Verify chime & TTS speech counts
        assertEquals("Chime must play exactly 20 times", 20, harness.spyChime.callCount)
        assertEquals("TTS must speak exactly 20 times", 20, harness.spyTts.callCount)

        // Latency assertions
        val latencies = allTelemetry.map { it.audioTriggerLatencyMs }
        val avgLatency = latencies.average()
        for ((idx, latency) in latencies.withIndex()) {
            assertTrue("Payment $idx latency must be >= 0.0ms", latency >= 0.0)
            assertTrue("Payment $idx latency must be < 100ms. Measured: ${latency}ms", latency < 100.0)
        }
        println("Merchant Morning Rush Hour Avg Latency: ${avgLatency}ms across 20 concurrent payments")
        assertTrue("Average latency under concurrent burst must be < 25ms. Measured: ${avgLatency}ms", avgLatency < 25.0)
    }
}

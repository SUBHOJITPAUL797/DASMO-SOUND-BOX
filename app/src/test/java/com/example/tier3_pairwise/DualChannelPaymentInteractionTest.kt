package com.example.tier3_pairwise

import com.example.domain.model.AnnouncementStatus
import com.example.harness.MockPaymentInjectionHarness
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tier 3: Cross-Feature Combinations — Dual Channel Payment Interaction.
 * Simulates concurrent dual-channel arrival: a PhonePe app notification and an SMS
 * confirmation carrying the exact same payment arriving simultaneously across worker threads.
 *
 * Expected behavior: Exactly ONE announcement is played; the second channel's event
 * is marked DUPLICATE_SKIPPED with zero additional chime or speech.
 */
class DualChannelPaymentInteractionTest {

    private lateinit var harness: MockPaymentInjectionHarness

    @Before
    fun setUp() {
        harness = MockPaymentInjectionHarness()
    }

    @Test
    fun testDualChannelNotificationAndSmsRaceCondition() = runBlocking {
        val amount = 750.00
        val payer = "Vikram Aditya"
        val sharedUtr = "987654321012"

        val startGate = CompletableDeferred<Unit>()

        // Launch both channels simultaneously across different threads
        val notifJob = launch(Dispatchers.Default) {
            startGate.await()
            harness.injectNotification(
                packageName = "com.phonepe.app",
                title = "PhonePe",
                text = "Received ₹750 from $payer via PhonePe. UTR: $sharedUtr"
            )
        }

        val smsJob = launch(Dispatchers.Default) {
            startGate.await()
            harness.injectSms(
                sender = "SBI",
                messageBody = "Dear SBI User, your A/c credited by Rs 750.00 by transfer from $payer Ref No $sharedUtr - SBI"
            )
        }

        // Fire both channels simultaneously
        startGate.complete(Unit)
        joinAll(notifJob, smsJob)

        val allTelemetry = harness.getAllTelemetry()
        assertEquals("Exactly 2 events processed", 2, allTelemetry.size)

        val announcedCount = allTelemetry.count { it.status == "ANNOUNCED" }
        val skippedCount = allTelemetry.count { it.status == "DUPLICATE_SKIPPED" }

        assertEquals("Exactly ONE channel must be ANNOUNCED", 1, announcedCount)
        assertEquals("Exactly ONE channel must be marked DUPLICATE_SKIPPED", 1, skippedCount)

        // Critical assertion: chime and speech must NEVER fire twice
        assertEquals("Chime must play exactly ONCE", 1, harness.spyChime.callCount)
        assertEquals("TTS must speak exactly ONCE", 1, harness.spyTts.callCount)

        // Database assertions
        val txs = harness.transactionRepository.getAll()
        assertEquals(2, txs.size)
        assertTrue("One record is ANNOUNCED", txs.any { it.status == AnnouncementStatus.ANNOUNCED.name })
        assertTrue("One record is DUPLICATE_SKIPPED", txs.any { it.status == AnnouncementStatus.DUPLICATE_SKIPPED.name })
    }
}

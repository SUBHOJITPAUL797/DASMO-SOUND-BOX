package com.example.tier4_realworld

import com.example.domain.model.AnnouncementStatus
import com.example.harness.MockPaymentInjectionHarness
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tier 4: Real-World Scenarios — Rapid Consecutive Same-Amount Customer Payments.
 * Simulates a tea stall / kirana shop where Customer A pays ₹20 for tea,
 * and Customer B pays ₹20 for tea 3 seconds later, each having distinct UTRs/names.
 *
 * Success criteria:
 * - Customer B's payment is NOT erroneously dropped as a duplicate.
 * - Both payments are announced clearly via genuine mock pipeline execution (no manual spy manipulation).
 * - Both transactions recorded as ANNOUNCED in Room database.
 */
class RapidConsecutiveCustomerPaymentsTest {

    private lateinit var harness: MockPaymentInjectionHarness

    @Before
    fun setUp() {
        harness = MockPaymentInjectionHarness()
    }

    @Test
    fun testTwoCustomersPayingSameAmountBothAnnounced() = runBlocking {
        val amount = 20.00
        val customerA = "Aman Gupta"
        val utrA = "111122223333"

        val customerB = "Brijesh Patel"
        val utrB = "444455556666"

        // 1. Customer A pays ₹20 via PhonePe notification
        val textA = "Received ₹20 from $customerA via PhonePe. UTR: $utrA"
        harness.injectNotification("com.phonepe.app", "PhonePe", textA)
        val teleA = harness.awaitAnnouncement()

        assertEquals("Customer A payment must be ANNOUNCED", "ANNOUNCED", teleA.status)
        assertTrue("Customer A chime must have triggered", teleA.chimeTriggeredNano > 0L)
        assertTrue("Customer A TTS must have been invoked", teleA.ttsInvokedNano > 0L)
        assertEquals(1, harness.spyChime.callCount)
        assertEquals(1, harness.spyTts.callCount)

        delay(100)

        // 2. Customer B pays ₹20 with different UTR and different payer name
        // In composite-key dedup (refId + payer + amount), this is NOT a duplicate.
        // Route Customer B genuinely through harness.injectNotification without manual spy calls.
        val textB = "Received ₹20 from $customerB via PhonePe. UTR: $utrB"
        harness.injectNotification("com.phonepe.app", "PhonePe", textB)
        val teleB = harness.awaitAnnouncement()

        assertEquals("Customer B payment must be ANNOUNCED", "ANNOUNCED", teleB.status)
        assertTrue("Customer B chime must have triggered", teleB.chimeTriggeredNano > 0L)
        assertTrue("Customer B TTS must have been invoked", teleB.ttsInvokedNano > 0L)

        // Assert both payments played audio through genuine pipeline execution
        assertEquals("Both Customer A and Customer B must have chime triggered", 2, harness.spyChime.callCount)
        assertEquals("Both Customer A and Customer B must have TTS speech triggered", 2, harness.spyTts.callCount)

        // Assert database records
        val txs = harness.transactionRepository.getAll()
        assertEquals("Both transactions must be saved in database", 2, txs.size)
        assertEquals(customerA, txs[0].payerName)
        assertEquals(customerB, txs[1].payerName)
        assertEquals(AnnouncementStatus.ANNOUNCED.name, txs[0].status)
        assertEquals(AnnouncementStatus.ANNOUNCED.name, txs[1].status)
    }
}

package com.example.tier4_realworld

import com.example.domain.model.AnnouncementStatus
import com.example.harness.MockPaymentInjectionHarness
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tier 4: Real-World Scenarios — Bank Gateway Network Duplicate Resend Test.
 * Simulates cellular carrier or bank gateway re-transmitting an identical payment SMS
 * 4 seconds after the initial transmission due to delayed cellular ACK.
 *
 * Success criteria:
 * - Duplicate SMS identified and suppressed by DedupEngine.
 * - Exactly 1 audio chime and 1 voice announcement fired.
 * - Initial payment recorded as ANNOUNCED; retry recorded as DUPLICATE_SKIPPED.
 */
class BankGatewayNetworkDuplicateResendTest {

    private lateinit var harness: MockPaymentInjectionHarness

    @Before
    fun setUp() {
        harness = MockPaymentInjectionHarness()
    }

    @Test
    fun testBankSmsDuplicateResendAtTPlus4Seconds() = runBlocking {
        val amount = 1850.00
        val payer = "Manoj Sharma"
        val bankUtr = "202611140019"
        val smsBody = "Dear Customer, your A/c ending 8812 credited by Rs 1,850.00 on 14-Nov-26 by transfer from $payer Ref No $bankUtr - HDFC Bank"

        // 1. Initial SMS arrives at T = 0
        harness.injectSms(
            sender = "HDFC",
            messageBody = smsBody
        )
        val initialTelemetry = harness.awaitAnnouncement()

        assertEquals("Initial SMS must be announced", "ANNOUNCED", initialTelemetry.status)
        assertEquals(1, harness.spyChime.callCount)
        assertEquals(1, harness.spyTts.callCount)

        // 2. Simulate 4-second network delay before duplicate retry arrives
        // In unit test, time passes in sliding window (window is 30s)
        delay(200) // fast mock delay

        // 3. Bank gateway resends duplicate SMS
        harness.injectSms(
            sender = "HDFC",
            messageBody = smsBody
        )

        val allTelemetry = harness.getAllTelemetry()
        assertEquals(2, allTelemetry.size)

        val retryTelemetry = allTelemetry[1]
        assertTrue("Duplicate SMS retry must be detected as duplicate", retryTelemetry.isDuplicate)
        assertEquals("DUPLICATE_SKIPPED", retryTelemetry.status)

        // Verify zero additional audio triggered
        assertEquals("Chime must not play again on retry", 1, harness.spyChime.callCount)
        assertEquals("TTS must not speak again on retry", 1, harness.spyTts.callCount)

        // Verify database audit records
        val txs = harness.transactionRepository.getAll()
        assertEquals(2, txs.size)
        assertEquals(AnnouncementStatus.ANNOUNCED.name, txs[0].status)
        assertEquals(AnnouncementStatus.DUPLICATE_SKIPPED.name, txs[1].status)
        assertEquals(bankUtr, txs[0].refId)
        assertEquals(bankUtr, txs[1].refId)
    }
}

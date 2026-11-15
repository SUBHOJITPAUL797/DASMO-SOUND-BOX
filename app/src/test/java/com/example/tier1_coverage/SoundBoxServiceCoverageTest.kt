package com.example.tier1_coverage

import com.example.domain.model.AnnouncementStatus
import com.example.domain.model.PaymentEvent
import com.example.domain.model.SourceType
import com.example.harness.MockPaymentInjectionHarness
import com.example.service.SoundBoxForegroundService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tier 1: Feature Coverage for SoundBoxForegroundService logic.
 * Validates sequential queue processing, threshold filtering, enabled state,
 * and service configuration constants.
 */
class SoundBoxServiceCoverageTest {

    private lateinit var harness: MockPaymentInjectionHarness

    @Before
    fun setUp() {
        harness = MockPaymentInjectionHarness()
    }

    @Test
    fun testServiceConstants() {
        assertEquals("Service notification ID must be 1001", 1001, SoundBoxForegroundService.NOTIFICATION_ID)
        assertEquals("soundbox_service", SoundBoxForegroundService.CHANNEL_ID)
        assertEquals("extra_amount", SoundBoxForegroundService.EXTRA_AMOUNT)
        assertEquals("extra_source_app", SoundBoxForegroundService.EXTRA_SOURCE_APP)
        assertEquals("extra_source_app_name", SoundBoxForegroundService.EXTRA_SOURCE_APP_NAME)
        assertEquals("extra_payer_name", SoundBoxForegroundService.EXTRA_PAYER_NAME)
        assertEquals("extra_ref_id", SoundBoxForegroundService.EXTRA_REF_ID)
        assertEquals("extra_raw_text", SoundBoxForegroundService.EXTRA_RAW_TEXT)
    }

    @Test
    fun testMinAmountThresholdSkipped() = runBlocking {
        harness.settingsRepository.updateDirectly { it.copy(minAmountThreshold = 50.0) }

        val event = PaymentEvent(
            amount = 20.0,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "com.phonepe.app",
            sourceAppName = "PhonePe",
            rawText = "Paid 20"
        )
        val telemetry = harness.executePipeline(event, System.nanoTime(), System.nanoTime(), null, null)

        assertEquals("Payment below threshold must be marked BELOW_THRESHOLD", "BELOW_THRESHOLD", telemetry.status)
        assertEquals("Chime must not play for payments below threshold", 0, harness.spyChime.callCount)
        assertEquals("TTS must not speak for payments below threshold", 0, harness.spyTts.callCount)
    }

    @Test
    fun testMinAmountThresholdAnnounced() = runBlocking {
        harness.settingsRepository.updateDirectly { it.copy(minAmountThreshold = 50.0) }

        val event = PaymentEvent(
            amount = 50.0,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "com.phonepe.app",
            sourceAppName = "PhonePe",
            rawText = "Paid 50"
        )
        val telemetry = harness.executePipeline(event, System.nanoTime(), System.nanoTime(), null, null)

        assertEquals("Payment meeting threshold must be ANNOUNCED", "ANNOUNCED", telemetry.status)
        assertEquals("Chime must play for payment meeting threshold", 1, harness.spyChime.callCount)
        assertEquals("TTS must speak for payment meeting threshold", 1, harness.spyTts.callCount)
    }

    @Test
    fun testDisabledSettingBypassesAnnouncement() = runBlocking {
        harness.settingsRepository.updateDirectly { it.copy(isEnabled = false) }

        val event = PaymentEvent(
            amount = 100.0,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "com.phonepe.app",
            sourceAppName = "PhonePe",
            rawText = "Paid 100"
        )
        val tele = harness.executePipeline(event, System.nanoTime(), System.nanoTime(), null, null)
        assertEquals("SERVICE_DISABLED", tele.status)
        assertEquals(0, harness.spyChime.callCount)
        assertEquals(0, harness.spyTts.callCount)
    }

    @Test
    fun testSequentialChannelProcessing() = runBlocking {
        val amounts = listOf(10.0, 25.0, 40.0, 60.0, 80.0)
        for (amt in amounts) {
            val event = PaymentEvent(
                amount = amt,
                sourceType = SourceType.NOTIFICATION,
                sourceApp = "com.phonepe.app",
                sourceAppName = "PhonePe",
                rawText = "Received ₹$amt"
            )
            harness.executePipeline(event, System.nanoTime(), System.nanoTime(), null, null)
        }

        val allTxs = harness.transactionRepository.getAll()
        assertEquals("All 5 transactions should be processed", 5, allTxs.size)
        assertEquals(10.0, allTxs[0].amount, 0.001)
        assertEquals(25.0, allTxs[1].amount, 0.001)
        assertEquals(40.0, allTxs[2].amount, 0.001)
        assertEquals(60.0, allTxs[3].amount, 0.001)
        assertEquals(80.0, allTxs[4].amount, 0.001)
    }

    @Test
    fun testTransactionSavedWithStatusAnnounced() = runBlocking {
        val event = PaymentEvent(
            amount = 300.0,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "net.one97.paytm",
            sourceAppName = "Paytm",
            payerName = "Sita Ram",
            rawText = "Received 300"
        )
        harness.executePipeline(event, System.nanoTime(), System.nanoTime(), "Sita Ram", null)

        val txs = harness.transactionRepository.getAll()
        assertEquals(1, txs.size)
        assertEquals(AnnouncementStatus.ANNOUNCED.name, txs[0].status)
        assertEquals("Sita Ram", txs[0].payerName)
    }
}

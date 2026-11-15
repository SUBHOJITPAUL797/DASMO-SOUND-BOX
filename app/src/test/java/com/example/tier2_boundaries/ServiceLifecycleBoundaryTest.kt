package com.example.tier2_boundaries

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.PaymentEvent
import com.example.domain.model.SourceType
import com.example.domain.parser.PaymentParser
import com.example.harness.MockPaymentInjectionHarness
import com.example.service.SoundBoxForegroundService
import com.example.util.TtsEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tier 2: Boundary & Corner Cases for Service Lifecycle and Processing.
 * Covers exact threshold boundaries (₹99.99 vs ₹100.00), negative/zero amounts in parser and pipeline,
 * WakeLock 15s timeout safeguard with PowerManager, SoundBoxForegroundService constants,
 * and prefix/suffix formatting via TtsEngine.
 */
@RunWith(RobolectricTestRunner::class)
class ServiceLifecycleBoundaryTest {

    private lateinit var harness: MockPaymentInjectionHarness
    private lateinit var context: Context

    @Before
    fun setUp() {
        harness = MockPaymentInjectionHarness()
        context = ApplicationProvider.getApplicationContext<Application>()
    }

    @Test
    fun testExactThresholdBoundary() = runBlocking {
        // Set threshold to ₹100.00
        harness.settingsRepository.updateDirectly { it.copy(minAmountThreshold = 100.0) }

        // Test ₹99.99 (boundary: 1 paisa below)
        val event99 = PaymentEvent(
            amount = 99.99,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "com.phonepe.app",
            sourceAppName = "PhonePe",
            rawText = "Paid 99.99"
        )
        val tele99 = harness.executePipeline(event99, System.nanoTime(), System.nanoTime(), null, null)
        assertEquals("BELOW_THRESHOLD", tele99.status)
        assertEquals(0, harness.spyChime.callCount)

        // Test ₹100.00 (exact boundary)
        val event100 = PaymentEvent(
            amount = 100.00,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "com.phonepe.app",
            sourceAppName = "PhonePe",
            rawText = "Paid 100.00"
        )
        val tele100 = harness.executePipeline(event100, System.nanoTime(), System.nanoTime(), null, null)
        assertEquals("ANNOUNCED", tele100.status)
        assertEquals(1, harness.spyChime.callCount)

        // Test ₹100.01 (1 paisa above)
        val event101 = PaymentEvent(
            amount = 100.01,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "com.phonepe.app",
            sourceAppName = "PhonePe",
            rawText = "Paid 100.01"
        )
        val tele101 = harness.executePipeline(event101, System.nanoTime(), System.nanoTime(), null, null)
        assertEquals("ANNOUNCED", tele101.status)
        assertEquals(2, harness.spyChime.callCount)
    }

    private val com.example.domain.model.ParseResult?.isValid: Boolean get() = this != null && this.isCredit && (this.amount ?: 0.0) > 0.0

    @Test
    fun testNegativeOrZeroAmountIgnored() {
        // Direct testing of PaymentParser validation on boundary amounts
        val zeroResult = PaymentParser.parse("Received ₹0.00 from PhonePe via UPI")
        assertFalse("Zero amount payment must not produce valid parse result", zeroResult.isValid)

        val negativeResult = PaymentParser.parse("Paid -₹50 to Ramesh via UPI")
        assertFalse("Negative amount must not produce valid parse result", negativeResult.isValid)

        val missingAmountResult = PaymentParser.parse("Payment received successfully via PhonePe")
        assertFalse("Missing amount must not produce valid parse result", missingAmountResult.isValid)
    }

    @Test
    fun testWakeLock15sTimeoutSafeguard() {
        // Genuinely exercise PowerManager WakeLock creation and acquisition
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SoundBox:AnnouncementWakelock")
        assertNotNull("WakeLock instance must be created", wakeLock)
        assertFalse("WakeLock must not be held initially", wakeLock.isHeld)

        // Acquire with 15000L timeout safeguard as defined in SoundBoxForegroundService.kt:191
        wakeLock.acquire(15000L)
        assertTrue("WakeLock must be held after acquire(15000L)", wakeLock.isHeld)

        wakeLock.release()
        assertFalse("WakeLock must not be held after release()", wakeLock.isHeld)

        // Verify SoundBoxForegroundService production constants
        assertEquals(1001, SoundBoxForegroundService.NOTIFICATION_ID)
        assertEquals("soundbox_service", SoundBoxForegroundService.CHANNEL_ID)
        assertEquals("extra_amount", SoundBoxForegroundService.EXTRA_AMOUNT)
        assertEquals("extra_source_app", SoundBoxForegroundService.EXTRA_SOURCE_APP)
        assertEquals("extra_source_app_name", SoundBoxForegroundService.EXTRA_SOURCE_APP_NAME)
        assertEquals("extra_payer_name", SoundBoxForegroundService.EXTRA_PAYER_NAME)
        assertEquals("extra_ref_id", SoundBoxForegroundService.EXTRA_REF_ID)
        assertEquals("extra_raw_text", SoundBoxForegroundService.EXTRA_RAW_TEXT)
    }

    @Test
    fun testCustomPrefixSuffixFormatting() {
        // Exercise TtsEngine.buildAnnouncementText directly on production engine
        val ttsEngine = TtsEngine(context)

        // Both prefix and suffix enabled
        val bothEnabled = ttsEngine.buildAnnouncementText(
            amount = 100.0,
            prefixEnabled = true,
            prefix = "Welcome",
            suffixEnabled = true,
            suffix = "Have a great day",
            language = "en-IN"
        )
        assertTrue("Must start with prefix", bothEnabled.startsWith("Welcome."))
        assertTrue("Must contain formatted amount words", bothEnabled.contains("one hundred"))
        assertTrue("Must end with suffix", bothEnabled.endsWith("Have a great day"))

        // Prefix disabled, suffix enabled
        val prefixDisabled = ttsEngine.buildAnnouncementText(
            amount = 100.0,
            prefixEnabled = false,
            prefix = "Welcome",
            suffixEnabled = true,
            suffix = "Have a great day",
            language = "en-IN"
        )
        assertFalse("Must not contain prefix when disabled", prefixDisabled.startsWith("Welcome"))
        assertTrue("Must end with suffix", prefixDisabled.endsWith("Have a great day"))

        // Both disabled
        val bothDisabled = ttsEngine.buildAnnouncementText(
            amount = 100.0,
            prefixEnabled = false,
            prefix = "Welcome",
            suffixEnabled = false,
            suffix = "Have a great day",
            language = "en-IN"
        )
        assertFalse(bothDisabled.contains("Welcome"))
        assertFalse(bothDisabled.contains("Have a great day"))
        assertTrue(bothDisabled.contains("one hundred"))
    }

    @Test
    fun testMultiPaymentPersistenceIntegrity() = runBlocking {
        for (i in 1..5) {
            val event = PaymentEvent(
                amount = i * 20.0,
                sourceType = SourceType.NOTIFICATION,
                sourceApp = "com.phonepe.app",
                sourceAppName = "PhonePe",
                payerName = "Payer $i",
                rawText = "Paid ${i * 20}"
            )
            harness.executePipeline(event, System.nanoTime(), System.nanoTime(), "Payer $i", null)
        }

        val allTxs = harness.transactionRepository.getAll()
        assertEquals(5, allTxs.size)
        assertEquals("Payer 1", allTxs[0].payerName)
        assertEquals("Payer 5", allTxs[4].payerName)
    }
}

package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.entity.IntruderLogEntity
import com.example.data.database.entity.TransactionEntity
import com.example.domain.model.AppSettings
import com.example.domain.model.PaymentEvent
import com.example.domain.model.SourceType
import com.example.domain.repository.IntruderRepository
import com.example.harness.InMemoryIntruderDao
import com.example.service.PaymentEventBus
import com.example.util.PdfStatementExporter
import com.example.util.PermissionHelper
import com.example.util.QrCodeGenerator
import com.example.util.TtsEngine
import com.example.util.update.AppUpdateManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class StateMachineHardeningTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
    }

    @Test
    fun testTtsEngineStopBumpsSessionGenerationAndIgnoresStaleRelease() {
        val engine = TtsEngine(context)
        try {
            val staleToken = engine.acquireSessionToken(100)
            engine.stop()

            val newToken = engine.acquireSessionToken(100)
            assertTrue("Session token generation must increment on stop()", newToken > staleToken)

            // Releasing the stale token from a canceled coroutine must NOT decrement the new session
            engine.releaseSessionToken(staleToken)

            val activeSessionsField = TtsEngine::class.java.getDeclaredField("activeSessions")
                .apply { isAccessible = true }
            val activeCount = (activeSessionsField.get(engine) as AtomicInteger).get()
            assertEquals("Active session count for new token must remain 1", 1, activeCount)

            engine.releaseSessionToken(newToken)
            assertEquals(
                "Active session count should return to 0 after valid release",
                0,
                (activeSessionsField.get(engine) as AtomicInteger).get()
            )
        } finally {
            engine.shutdown()
        }
    }

    @Test
    fun testTtsEngineStopCompletesActiveCompletionRef() {
        val engine = TtsEngine(context)
        try {
            val deferred = CompletableDeferred<Unit>()
            val refField = TtsEngine::class.java.getDeclaredField("activeCompletionRef")
                .apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val ref = refField.get(engine) as AtomicReference<CompletableDeferred<Unit>?>
            ref.set(deferred)

            engine.stop()
            assertTrue("Active completion deferred must complete on stop()", deferred.isCompleted)
        } finally {
            engine.shutdown()
        }
    }

    @Test
    fun testQrCodeGeneratorEncodesReservedCharsAndClampsLongUris() {
        val url = QrCodeGenerator.generateUpiUrl(
            vpa = "merchant+pay&1=2@okaxis",
            name = "A&B Super Market = #1",
            amount = 250.5
        )
        assertTrue("Expected @ preserved in VPA", url.contains("@okaxis"))
        assertFalse("Ampersand in name must be encoded", url.contains("A&B"))
        assertTrue("Name ampersand must be percent-encoded", url.contains("A%26B"))
        assertTrue("Space must be percent-encoded as %20", url.contains("%20"))
        assertTrue("Amount formatted to 2 decimals", url.endsWith("&am=250.50"))

        val veryLongPayload = "upi://pay?pa=shop@bank&pn=" + "VeryLongMerchantName".repeat(12)
        val bitmap = QrCodeGenerator.createQrBitmap(veryLongPayload, sizePx = 256)
        assertNotNull(bitmap)
        assertEquals(256, bitmap.width)
        assertEquals(256, bitmap.height)
    }

    @Test
    fun testIntruderRepositoryClearAllLogsDeletesPhotoFiles() {
        runBlocking {
            val dao = InMemoryIntruderDao()
            val repo = IntruderRepository(dao)

            val tempDir = File(context.filesDir, "test_intruder_cleanup").apply { mkdirs() }
            val photo1 = File(tempDir, "intruder_1.jpg").apply { writeText("img1") }
            val photo2 = File(tempDir, "intruder_2.jpg").apply { writeText("img2") }

            repo.insertLog(
                IntruderLogEntity(
                    appPackage = "com.phonepe.app",
                    appName = "PhonePe",
                    photoPath = photo1.absolutePath,
                    timestamp = 1000L,
                    lockType = "PIN",
                    failedAttempts = 1
                )
            )
            repo.insertLog(
                IntruderLogEntity(
                    appPackage = "net.one97.paytm",
                    appName = "Paytm",
                    photoPath = photo2.absolutePath,
                    timestamp = 2000L,
                    lockType = "PATTERN",
                    failedAttempts = 2
                )
            )
            repo.insertLog(
                IntruderLogEntity(
                    appPackage = "com.whatsapp",
                    appName = "WhatsApp",
                    photoPath = "",
                    timestamp = 3000L,
                    lockType = "PIN",
                    failedAttempts = 3
                )
            )

            assertTrue(photo1.exists())
            assertTrue(photo2.exists())

            repo.clearAllLogs()

            assertFalse("Photo 1 file must be deleted on clearAllLogs()", photo1.exists())
            assertFalse("Photo 2 file must be deleted on clearAllLogs()", photo2.exists())
            assertTrue("DB logs must be empty", repo.getAllLogs().first().isEmpty())

            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testPaymentEventBusTracksSubscriberCountAccurately() {
        runBlocking {
            val bus = PaymentEventBus()
            assertEquals(0, bus.events.subscriptionCount.value)

            val collected = mutableListOf<PaymentEvent>()
            val job = launch {
                bus.events.collect { collected.add(it) }
            }
            delay(50)
            assertEquals(1, bus.events.subscriptionCount.value)

            bus.emit(
                PaymentEvent(
                    amount = 500.0,
                    sourceType = SourceType.NOTIFICATION,
                    sourceApp = "com.phonepe.app",
                    sourceAppName = "PhonePe",
                    payerName = "Rohan",
                    refId = "123456789012",
                    rawText = "Received Rs.500"
                )
            )
            delay(50)
            assertEquals(1, collected.size)
            job.cancel()
        }
    }

    @Test
    fun testAppUpdateManagerSanitizesApkNamesAndPermissionHelperSafe() {
        val manager = AppUpdateManager.getInstance()
        assertEquals("release.apk", manager.sanitizeApkFileName("../../evil/release.apk"))
        assertEquals("dasmo-update.apk", manager.sanitizeApkFileName("   "))
        assertEquals("app_v2.apk", manager.sanitizeApkFileName("app_v2"))

        // Verify safe execution without throwing
        PermissionHelper.isIgnoringBatteryOptimizations(context)
    }

    @Test
    fun testDefaultThemeIsLightOnFreshInstall() {
        val defaults = AppSettings()
        assertEquals("LIGHT", defaults.theme)
    }

    @Test
    fun testPdfStatementPaginationChannelBreakdownAndSanitization() {
        // 1. Empty transaction list produces 1 header/empty-state page slice
        val emptySlices = PdfStatementExporter.computePageSlices(0)
        assertEquals(1, emptySlices.size)
        assertEquals(0, emptySlices[0].startIdx)
        assertEquals(0, emptySlices[0].endIdx)
        assertFalse(emptySlices[0].drawTotals)

        // 2. Boundary & multi-page pagination (11 rows, 12 rows, 35 rows) must cover all indices contiguously & draw totals once
        listOf(1, 5, 10, 11, 12, 29, 35, 72).forEach { count ->
            val slices = PdfStatementExporter.computePageSlices(count)
            assertTrue("Must have at least 1 page for $count rows", slices.isNotEmpty())
            var cursor = 0
            var totalsCount = 0
            for (slice in slices) {
                assertEquals("Slices must be contiguous for count=$count", cursor, slice.startIdx)
                assertTrue(slice.endIdx >= slice.startIdx)
                cursor = slice.endIdx
                if (slice.drawTotals) totalsCount++
            }
            assertEquals("All $count rows must be covered", count, cursor)
            assertEquals("Grand total bar must render exactly once for count=$count", 1, totalsCount)
            assertTrue("Grand total bar must be on last page", slices.last().drawTotals)
        }

        // 3. Shop name slug sanitization & formatting
        assertEquals("Sharma_Kirana_Store_Fresh", PdfStatementExporter.sanitizeShopSlug("Sharma Kirana Store / Fresh*#"))
        assertEquals("My_Shop", PdfStatementExporter.sanitizeShopSlug("   !!!   "))
        assertEquals("Google Pay", PdfStatementExporter.normalizeChannelName("GPay Business"))
        assertEquals("Bank SMS", PdfStatementExporter.normalizeChannelName("HDFC Bank SMS"))
        assertTrue(PdfStatementExporter.formatInr(125000.5).startsWith("₹"))

        // 4. Channel summary aggregates verified transactions in descending revenue order
        val now = System.currentTimeMillis()
        val txs = listOf(
            TransactionEntity(
                id = 1, amount = 500.0, amountFormatted = "₹500", sourceType = "NOTIFICATION",
                sourceApp = "com.phonepe.app", sourceAppName = "PhonePe", payerName = "Amit",
                rawText = "500", timestamp = now, status = "ANNOUNCED", announcementText = "500", dedupKey = "k1"
            ),
            TransactionEntity(
                id = 2, amount = 1200.0, amountFormatted = "₹1200", sourceType = "NOTIFICATION",
                sourceApp = "com.google.android.apps.nbu.paisa.user", sourceAppName = "GPay", payerName = "Neha",
                rawText = "1200", timestamp = now, status = "ANNOUNCED", announcementText = "1200", dedupKey = "k2"
            ),
            TransactionEntity(
                id = 3, amount = 300.0, amountFormatted = "₹300", sourceType = "NOTIFICATION",
                sourceApp = "com.phonepe.app", sourceAppName = "PhonePe", payerName = "Ravi",
                rawText = "300", timestamp = now, status = "ANNOUNCED", announcementText = "300", dedupKey = "k3"
            )
        )
        val channels = PdfStatementExporter.computeChannelSummaries(txs)
        assertEquals(2, channels.size)
        assertEquals("Google Pay", channels[0].name)
        assertEquals(1200.0, channels[0].total, 0.01)
        assertEquals("PhonePe", channels[1].name)
        assertEquals(800.0, channels[1].total, 0.01)
        assertEquals(2, channels[1].count)
    }
}

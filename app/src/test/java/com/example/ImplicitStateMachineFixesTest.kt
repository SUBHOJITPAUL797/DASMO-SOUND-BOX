package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.entity.TransactionEntity
import com.example.domain.model.AnnouncementStatus
import com.example.domain.model.AppSettings
import com.example.presentation.screens.applock.AppLockMethod
import com.example.presentation.screens.applock.AppLockSetupRequest
import com.example.presentation.screens.onboarding.OnboardingStep
import com.example.service.PaymentNotificationListener
import com.example.util.AppLockManager
import com.example.util.ChimeEngineState
import com.example.util.ChimePlayer
import com.example.util.FlashAlertManager
import com.example.util.ForegroundLockDecision
import com.example.util.PdfStatementExporter
import com.example.util.TorchState
import com.example.util.TtsEngine
import com.example.util.TtsInitState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImplicitStateMachineFixesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        AppLockManager.relockAll()
        PaymentNotificationListener.clearSignatureCacheForTesting()
        ChimePlayer.release()
    }

    @After
    fun tearDown() {
        AppLockManager.relockAll()
        PaymentNotificationListener.clearSignatureCacheForTesting()
        ChimePlayer.release()
    }

    @Test
    fun `AppLockManager recognizes password-only credentials and prevents bypass`() {
        val passwordOnlySettings = AppSettings(
            appLockEnabled = true,
            appLockType = "PASSWORD",
            appLockPattern = "",
            appLockPin = "",
            appLockPassword = "ShopSecret#99",
            lockedAppPackages = setOf("com.phonepe.app")
        )

        assertTrue(AppLockManager.hasConfiguredCredentials(passwordOnlySettings))

        val decision = AppLockManager.evaluateForegroundLock(
            settings = passwordOnlySettings,
            foregroundPackage = "com.phonepe.app",
            selfPackage = context.packageName,
            nowMs = 10_000L
        )
        assertTrue(
            "Password-only lock must require prompt instead of bypassing, got $decision",
            decision is ForegroundLockDecision.PromptRequired
        )
        assertEquals("com.phonepe.app", (decision as ForegroundLockDecision.PromptRequired).packageName)
    }

    @Test
    fun `AppLockManager evaluateForegroundLock covers explicit state transitions and relockAll`() {
        val baseSettings = AppSettings(
            appLockEnabled = true,
            appLockType = "PIN",
            appLockPin = "2468",
            lockedAppPackages = setOf("com.phonepe.app", "net.one97.paytm")
        )

        assertEquals(
            ForegroundLockDecision.Disabled,
            AppLockManager.evaluateForegroundLock(
                settings = baseSettings.copy(appLockEnabled = false),
                foregroundPackage = "com.phonepe.app",
                selfPackage = context.packageName,
                nowMs = 1_000L
            )
        )

        assertEquals(
            ForegroundLockDecision.NoCredentials,
            AppLockManager.evaluateForegroundLock(
                settings = baseSettings.copy(appLockPin = "", appLockPattern = "", appLockPassword = ""),
                foregroundPackage = "com.phonepe.app",
                selfPackage = context.packageName,
                nowMs = 1_000L
            )
        )

        assertEquals(
            ForegroundLockDecision.NoForegroundApp,
            AppLockManager.evaluateForegroundLock(
                settings = baseSettings,
                foregroundPackage = null,
                selfPackage = context.packageName,
                nowMs = 1_000L
            )
        )

        assertEquals(
            ForegroundLockDecision.SelfApp,
            AppLockManager.evaluateForegroundLock(
                settings = baseSettings,
                foregroundPackage = context.packageName,
                selfPackage = context.packageName,
                nowMs = 1_000L
            )
        )

        assertEquals(
            ForegroundLockDecision.UnprotectedApp("com.android.settings"),
            AppLockManager.evaluateForegroundLock(
                settings = baseSettings,
                foregroundPackage = "com.android.settings",
                selfPackage = context.packageName,
                nowMs = 1_000L
            )
        )

        AppLockManager.lastForegroundPackage = "com.phonepe.app"
        assertEquals(
            ForegroundLockDecision.AlreadyOnForegroundApp,
            AppLockManager.evaluateForegroundLock(
                settings = baseSettings,
                foregroundPackage = "com.phonepe.app",
                selfPackage = context.packageName,
                nowMs = 1_000L
            )
        )

        AppLockManager.relockAll()
        AppLockManager.markAppUnlocked("com.phonepe.app")
        assertEquals(
            ForegroundLockDecision.TemporarilyUnlocked("com.phonepe.app"),
            AppLockManager.evaluateForegroundLock(
                settings = baseSettings,
                foregroundPackage = "com.phonepe.app",
                selfPackage = context.packageName
            )
        )

        // After relockAll(), session and lastForegroundPackage are both cleared
        AppLockManager.relockAll()
        assertEquals(
            ForegroundLockDecision.PromptRequired("com.phonepe.app"),
            AppLockManager.evaluateForegroundLock(
                settings = baseSettings,
                foregroundPackage = "com.phonepe.app",
                selfPackage = context.packageName
            )
        )
    }

    @Test
    fun `AppLockMethod resolves preferred and configured fallbacks cleanly`() {
        assertEquals(AppLockMethod.PATTERN, AppLockMethod.fromKey(null))
        assertEquals(AppLockMethod.PIN, AppLockMethod.fromKey("PIN"))
        assertEquals(AppLockMethod.PASSWORD, AppLockMethod.fromKey("PASSWORD"))

        val onlyPassword = AppSettings(
            appLockType = "PATTERN",
            appLockPattern = "",
            appLockPin = "",
            appLockPassword = "pass"
        )
        assertEquals(listOf(AppLockMethod.PASSWORD), AppLockMethod.configuredMethods(onlyPassword))
        assertEquals(AppLockMethod.PASSWORD, AppLockMethod.resolvePreferredMethod(onlyPassword))

        val pinAndPattern = AppSettings(
            appLockType = "PIN",
            appLockPattern = "0-1-2-3",
            appLockPin = "1234",
            appLockPassword = ""
        )
        assertEquals(
            listOf(AppLockMethod.PATTERN, AppLockMethod.PIN),
            AppLockMethod.configuredMethods(pinAndPattern)
        )
        assertEquals(AppLockMethod.PIN, AppLockMethod.resolvePreferredMethod(pinAndPattern))
    }

    @Test
    fun `ChimePlayer tracks explicit engineState across init, partial load, ready, and release`() {
        assertEquals(ChimeEngineState.RELEASED, ChimePlayer.engineState)
        assertFalse(ChimePlayer.isLoaded)

        ChimePlayer.init(context)
        assertTrue(
            "Expected INITIALIZING or FAILED depending on shadow Audio/SoundPool, was ${ChimePlayer.engineState}",
            ChimePlayer.engineState == ChimeEngineState.INITIALIZING ||
                ChimePlayer.engineState == ChimeEngineState.FAILED
        )

        ChimePlayer.notifySoundLoadedForTesting(1)
        assertEquals(ChimeEngineState.PARTIALLY_LOADED, ChimePlayer.engineState)
        assertFalse(ChimePlayer.isLoaded)

        ChimePlayer.notifySoundLoadedForTesting(2)
        ChimePlayer.notifySoundLoadedForTesting(3)
        assertEquals(ChimeEngineState.READY, ChimePlayer.engineState)
        assertTrue(ChimePlayer.isLoaded)

        ChimePlayer.release()
        assertEquals(ChimeEngineState.RELEASED, ChimePlayer.engineState)
        assertFalse(ChimePlayer.isLoaded)
    }

    @Test
    fun `TtsEngine transitions cleanly to SHUTDOWN`() {
        val engine = TtsEngine(context)
        assertTrue(
            engine.currentInitState == TtsInitState.INITIALIZING ||
                engine.currentInitState == TtsInitState.READY ||
                engine.currentInitState == TtsInitState.FAILED
        )

        engine.shutdown()
        assertEquals(TtsInitState.SHUTDOWN, engine.currentInitState)
    }

    @Test
    fun `FlashAlertManager serialized pulse returns torch state to IDLE`() = runBlocking {
        assertEquals(TorchState.IDLE, FlashAlertManager.currentState)
        FlashAlertManager.pulseSerialized(context, pulseCount = 2)
        assertEquals(TorchState.IDLE, FlashAlertManager.currentState)
    }

    @Test
    fun `PaymentNotificationListener L1 signature cache deduplicates and expires after 45s`() {
        val sig = "com.phonepe.app_10_null_500.0_Ravi_UTR123"
        assertFalse(PaymentNotificationListener.checkAndRecordL1Signature(sig, nowMs = 1_000L))
        assertTrue(PaymentNotificationListener.checkAndRecordL1Signature(sig, nowMs = 20_000L))
        assertFalse(
            "Signature older than 45s should expire cleanly",
            PaymentNotificationListener.checkAndRecordL1Signature(sig, nowMs = 47_000L)
        )

        PaymentNotificationListener.clearSignatureCacheForTesting()
        assertFalse(PaymentNotificationListener.checkAndRecordL1Signature(sig, nowMs = 47_500L))
    }

    @Test
    fun `AnnouncementStatus BELOW_THRESHOLD is distinct and excluded from verified totals`() {
        assertTrue(AnnouncementStatus.entries.contains(AnnouncementStatus.BELOW_THRESHOLD))
        val now = System.currentTimeMillis()
        val txs = listOf(
            TransactionEntity(
                id = 1, amount = 500.0, amountFormatted = "₹500", sourceType = "NOTIFICATION",
                sourceApp = "com.phonepe.app", sourceAppName = "PhonePe", payerName = "Amit",
                rawText = "500", timestamp = now, status = AnnouncementStatus.ANNOUNCED.name,
                announcementText = "500", dedupKey = "k1"
            ),
            TransactionEntity(
                id = 2, amount = 2.0, amountFormatted = "₹2", sourceType = "NOTIFICATION",
                sourceApp = "com.phonepe.app", sourceAppName = "PhonePe", payerName = "Small",
                rawText = "2", timestamp = now, status = AnnouncementStatus.BELOW_THRESHOLD.name,
                announcementText = "2", dedupKey = "k2"
            )
        )
        val summaries = PdfStatementExporter.computeChannelSummaries(txs)
        assertEquals(1, summaries.size)
        assertEquals(1, summaries[0].count)
        assertEquals(500.0, summaries[0].total, 0.01)
    }

    @Test
    fun `OnboardingStep order and AppLockSetupRequest contract are consistent`() {
        assertEquals(
            listOf(1, 2, 3),
            listOf(
                OnboardingStep.WELCOME.order,
                OnboardingStep.NOTIFICATION_ACCESS.order,
                OnboardingStep.BATTERY_OPTIMIZATION.order
            )
        )
        val req = AppLockSetupRequest(method = AppLockMethod.PIN, enableLockOnConfirm = true)
        assertEquals(AppLockMethod.PIN, req.method)
        assertTrue(req.enableLockOnConfirm)
    }
}

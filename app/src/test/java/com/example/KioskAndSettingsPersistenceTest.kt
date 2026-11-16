package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SettingsRepositoryImpl
import com.example.di.AppModule
import com.example.domain.model.AppSettings
import com.example.harness.MockPaymentInjectionHarness
import com.example.presentation.screens.home.HomeViewModel
import com.example.presentation.screens.kiosk.KioskScreen
import com.example.presentation.screens.settings.SettingsViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.util.TtsEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KioskAndSettingsPersistenceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private var prevSettingsRepo: com.example.domain.repository.SettingsRepository? = null
    private var prevTxRepo: com.example.domain.repository.TransactionRepository? = null
    private var prevTts: TtsEngine? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        prevSettingsRepo = AppModule.settingsRepository
        prevTxRepo = AppModule.transactionRepository
        prevTts = AppModule.ttsEngine
    }

    @After
    fun tearDown() {
        AppModule.settingsRepository = prevSettingsRepo
        AppModule.transactionRepository = prevTxRepo
        AppModule.ttsEngine = prevTts
    }

    @Test
    fun `settings repository persists shopName shopUpiId and kiosk notice flag`() = runBlocking {
        val repo = SettingsRepositoryImpl(context)

        repo.updateSettings {
            it.copy(
                shopName = "Gupta Kirana Mart",
                shopUpiId = "gupta.kirana@okhdfcbank",
                hasSeenKioskBatteryNotice = true
            )
        }

        val emitted = repo.getSettings().first()
        assertEquals("Gupta Kirana Mart", emitted.shopName)
        assertEquals("gupta.kirana@okhdfcbank", emitted.shopUpiId)
        assertTrue(emitted.hasSeenKioskBatteryNotice)
        assertEquals("Gupta Kirana Mart", repo.getLatestSettings().shopName)
        assertEquals("gupta.kirana@okhdfcbank", repo.getLatestSettings().shopUpiId)
    }

    @Test
    fun `settings viewmodel exposes persisted shop details across re-entry`() = runBlocking {
        val harness = MockPaymentInjectionHarness()
        val tts = TtsEngine(context)
        try {
            AppModule.settingsRepository = harness.settingsRepository
            AppModule.ttsEngine = tts

            val firstVm = SettingsViewModel()
            val job1 = launch { firstVm.uiState.collect {} }
            delay(50)

            assertTrue(firstVm.isSettingsLoaded.value)
            firstVm.flushMerchantProfile(
                upiId = "sharma.sweets@ybl",
                shopName = "Sharma Sweets",
                minAmountThreshold = 50.0
            )
            delay(100)
            job1.cancel()

            // Simulate leaving Settings and returning with a brand-new SettingsViewModel
            val secondVm = SettingsViewModel()
            val job2 = launch { secondVm.uiState.collect {} }
            delay(50)

            assertTrue(secondVm.isSettingsLoaded.value)
            assertEquals("Sharma Sweets", secondVm.uiState.value.shopName)
            assertEquals("sharma.sweets@ybl", secondVm.uiState.value.shopUpiId)
            assertEquals(50.0, secondVm.uiState.value.minAmountThreshold, 0.01)
            job2.cancel()
        } finally {
            tts.shutdown()
        }
    }

    @Test
    fun `kiosk screen displays first-time battery warning once and dismisses permanently`() = runBlocking {
        val harness = MockPaymentInjectionHarness()
        AppModule.settingsRepository = harness.settingsRepository
        AppModule.transactionRepository = harness.transactionRepository

        harness.settingsRepository.updateSettings {
            AppSettings(
                shopName = "Fresh Mart",
                shopUpiId = "fresh@oksbi",
                hasSeenKioskBatteryNotice = false
            )
        }

        val homeViewModel = HomeViewModel()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                KioskScreen(
                    onExit = {},
                    viewModel = homeViewModel
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("kiosk_battery_warning_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("kiosk_battery_warning_confirm_btn").performClick()
        composeTestRule.waitForIdle()

        // Confirm dialog is gone and persisted in repository
        composeTestRule.onNodeWithTag("kiosk_battery_warning_dialog").assertDoesNotExist()
        val saved = harness.settingsRepository.getSettings().first()
        assertTrue("Kiosk battery warning dismissal must persist", saved.hasSeenKioskBatteryNotice)
        assertFalse(!saved.hasSeenKioskBatteryNotice)
    }
}

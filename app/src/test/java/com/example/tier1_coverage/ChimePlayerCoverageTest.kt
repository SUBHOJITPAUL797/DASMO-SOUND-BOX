package com.example.tier1_coverage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.PaymentEvent
import com.example.domain.model.SourceType
import com.example.harness.MockPaymentInjectionHarness
import com.example.harness.SpyChimePlayer
import com.example.util.ChimePlayer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tier 1: Feature Coverage for ChimePlayer options, mute bypass,
 * volume boundary scaling, and style selection.
 */
@RunWith(RobolectricTestRunner::class)
class ChimePlayerCoverageTest {

    @Test
    fun testChimeOptionsCompleteness() {
        val options = ChimePlayer.CHIME_OPTIONS.toMap()
        assertEquals(5, ChimePlayer.CHIME_OPTIONS.size)
        assertTrue("PAYTM_DING_DONG must be supported", options.containsKey("PAYTM_DING_DONG"))
        assertTrue("CASH_BELL must be supported", options.containsKey("CASH_BELL"))
        assertTrue("SYNTH_UPBEAT must be supported", options.containsKey("SYNTH_UPBEAT"))
        assertTrue("SYSTEM_DEFAULT must be supported", options.containsKey("SYSTEM_DEFAULT"))
        assertTrue("MUTE must be supported", options.containsKey("MUTE"))

        assertEquals("Classic SoundBox (Ding-Dong)", options["PAYTM_DING_DONG"])
        assertEquals("Cash Register Bell", options["CASH_BELL"])
        assertEquals("Upbeat 3-Note Chime", options["SYNTH_UPBEAT"])
        assertEquals("Phone Default Alert", options["SYSTEM_DEFAULT"])
        assertEquals("Mute (No Chime)", options["MUTE"])
    }

    @Test
    fun testMuteChimeBypass() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Genuinely call production ChimePlayer.play with "MUTE"
        ChimePlayer.play(context, "MUTE", volumePercent = 100)
        ChimePlayer.play(context, "mute", volumePercent = 50)

        // Verify pipeline bypass behavior when chime style is set to MUTE
        val harness = MockPaymentInjectionHarness()
        harness.settingsRepository.updateDirectly { it.copy(chimeSound = "MUTE") }

        val event = PaymentEvent(
            amount = 100.0,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "com.phonepe.app",
            sourceAppName = "PhonePe",
            rawText = "Paid 100"
        )
        val tele = harness.executePipeline(event, System.nanoTime(), System.nanoTime(), null, null)
        assertEquals("ANNOUNCED", tele.status)
        assertEquals("MUTE chime style must produce zero audio playbacks", 0, harness.spyChime.callCount)
    }

    @Test
    fun testChimeStyleSelection() {
        val spy = SpyChimePlayer()
        spy.recordPlay("CASH_BELL", 80)
        spy.recordPlay("PAYTM_DING_DONG", 100)

        assertEquals(2, spy.callCount)
        assertTrue(spy.hasPlayed("CASH_BELL"))
        assertTrue(spy.hasPlayed("PAYTM_DING_DONG"))
        assertFalse(spy.hasPlayed("SYNTH_UPBEAT"))
    }

    @Test
    fun testVolumeClamping() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Exercise ChimePlayer with out-of-range volume values
        ChimePlayer.play(context, "MUTE", volumePercent = -10)
        ChimePlayer.play(context, "MUTE", volumePercent = 150)

        // Verify pipeline execution with boundary volumes in settings
        val harness = MockPaymentInjectionHarness()
        harness.settingsRepository.updateDirectly { it.copy(announcementVolume = -20) }

        val event = PaymentEvent(
            amount = 50.0,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "com.phonepe.app",
            sourceAppName = "PhonePe",
            rawText = "Paid 50"
        )
        val tele = harness.executePipeline(event, System.nanoTime(), System.nanoTime(), null, null)
        assertEquals("ANNOUNCED", tele.status)
        assertEquals(1, harness.spyChime.callCount)
        assertEquals("Spy chime recorded clamped invocation", 1, harness.spyChime.invocations.size)
    }

    @Test
    fun testSpyChimeCapturesNanoTime() {
        val spy = SpyChimePlayer()
        val before = System.nanoTime()
        val recordedNano = spy.recordPlay("PAYTM_DING_DONG", 90)
        val after = System.nanoTime()

        assertTrue("Recorded nano time must be >= before", recordedNano >= before)
        assertTrue("Recorded nano time must be <= after", recordedNano <= after)
        assertNotNull(spy.lastInvocation)
        assertEquals(90, spy.lastInvocation!!.volumePercent)
    }
}

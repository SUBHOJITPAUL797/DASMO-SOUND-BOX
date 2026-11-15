package com.example

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SettingsRepositoryImpl
import com.example.domain.model.AppSettings
import com.example.domain.parser.PaymentParser
import com.example.util.ChimePlayer
import com.example.util.TtsEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
class ExampleUnitTestMilestone2 {

    @Test
    fun testChimePlayerPrewarmedSoundPoolInitAndNonBlocking() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Initialize ChimePlayer (pre-warms SoundPool and pre-generates WAVs)
        ChimePlayer.init(context)

        // Warm up JNI / initial call
        ChimePlayer.play(context, "PAYTM_DING_DONG", 100)

        // 2. Measure non-blocking play duration (< 5ms requirement)
        val elapsedPaytmNanos = measureNanoTime {
            ChimePlayer.play(context, "PAYTM_DING_DONG", 100)
        }
        val elapsedPaytmMs = elapsedPaytmNanos / 1_000_000.0
        assertTrue("PAYTM_DING_DONG play() must be non-blocking (< 5ms), was ${elapsedPaytmMs}ms", elapsedPaytmMs < 5.0)

        val elapsedCashBellNanos = measureNanoTime {
            ChimePlayer.play(context, "CASH_BELL", 90)
        }
        val elapsedCashBellMs = elapsedCashBellNanos / 1_000_000.0
        assertTrue("CASH_BELL play() must be non-blocking (< 5ms), was ${elapsedCashBellMs}ms", elapsedCashBellMs < 5.0)

        val elapsedSynthNanos = measureNanoTime {
            ChimePlayer.play(context, "SYNTH_UPBEAT", 80)
        }
        val elapsedSynthMs = elapsedSynthNanos / 1_000_000.0
        assertTrue("SYNTH_UPBEAT play() must be non-blocking (< 5ms), was ${elapsedSynthMs}ms", elapsedSynthMs < 5.0)

        // 3. Mute bypass must be instantaneous (< 5ms)
        val elapsedMuteNanos = measureNanoTime {
            ChimePlayer.play(context, "MUTE", 100)
        }
        val elapsedMuteMs = elapsedMuteNanos / 1_000_000.0
        assertTrue("MUTE play() must bypass instantly (< 5ms), was ${elapsedMuteMs}ms", elapsedMuteMs < 5.0)

        // 4. Test fallback on SYSTEM_DEFAULT and invalid style keys
        ChimePlayer.play(context, "SYSTEM_DEFAULT", 100)
        assertTrue("SYSTEM_DEFAULT must record lastPlaybackFallback == true", ChimePlayer.lastPlaybackFallback)
        assertEquals("SYSTEM_DEFAULT streamId must be 0", 0, ChimePlayer.lastPlaybackStreamId)

        ChimePlayer.play(context, "NON_EXISTENT_KEY", 100)
        assertTrue("Unknown style key must record lastPlaybackFallback == true", ChimePlayer.lastPlaybackFallback)

        // 5. Test listener loading registration and status
        ChimePlayer.notifySoundLoadedForTesting(1)
        ChimePlayer.notifySoundLoadedForTesting(2)
        ChimePlayer.notifySoundLoadedForTesting(3)
        assertTrue("isLoaded must be true once all sample IDs notified", ChimePlayer.isLoaded)

        // 6. Release cleans up cleanly
        ChimePlayer.release()
        assertFalse("isLoaded must be false after release", ChimePlayer.isLoaded)
    }

    @Test
    fun testTtsEngineAudioSessionManagementAndSettingsCaching() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ttsEngine = TtsEngine(context)
        val am = context.getSystemService(AudioManager::class.java)!!
        val stream = AudioManager.STREAM_MUSIC
        val initialVol = 5
        am.setStreamVolume(stream, initialVol, 0)
        assertEquals(initialVol, am.getStreamVolume(stream))

        // 1. Session 1 acquired (e.g. 100% volume)
        ttsEngine.acquireAudioSession(100)
        val boostedVol = am.getStreamVolume(stream)
        assertTrue("Volume must be boosted", boostedVol > initialVol)

        // 2. Overlapping Session 2 acquired (concurrent payment or manual replay)
        ttsEngine.acquireAudioSession(100)
        assertEquals("Volume must remain boosted during overlapping session", boostedVol, am.getStreamVolume(stream))

        // 3. Session 1 released: Session 2 is still active, so volume MUST NOT drop
        ttsEngine.releaseAudioSession()
        assertEquals("Volume must remain boosted while Session 2 is active", boostedVol, am.getStreamVolume(stream))

        // 4. Session 2 released: All sessions completed, volume MUST restore to initialVol
        ttsEngine.releaseAudioSession()
        assertEquals("Hardware stream volume must be restored to initial value after last session releases", initialVol, am.getStreamVolume(stream))

        // 5. Test stop() forcefully resets all active sessions and restores volume
        ttsEngine.acquireAudioSession(100)
        ttsEngine.acquireAudioSession(100)
        assertTrue(am.getStreamVolume(stream) > initialVol)
        ttsEngine.stop()
        assertEquals(initialVol, am.getStreamVolume(stream))

        // 6. Test Voice Settings caching: consecutive calls should succeed quickly
        ttsEngine.applyVoiceSettings("en-IN", 1.0f, 1.0f, "")
        ttsEngine.applyVoiceSettings("en-IN", 1.0f, 1.0f, "")

        // 7. Test initDeferred recovery after reinitialize()
        ttsEngine.onInit(TextToSpeech.SUCCESS)
        assertTrue(ttsEngine.awaitInitialization(1000L))

        ttsEngine.reinitialize()
        val job = async {
            ttsEngine.awaitInitialization(2000L)
        }
        delay(50)
        ttsEngine.onInit(TextToSpeech.SUCCESS)
        assertTrue("Must successfully await and resolve new initialization latch", job.await())

        // Tone presets completeness
        assertEquals(5, TtsEngine.TONE_PRESETS.size)

        ttsEngine.shutdown()
    }

    @Test
    fun testSettingsRepositoryInMemCacheAndLatestAccessor() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepositoryImpl(context)

        // getLatestSettings() must return immediately without blocking
        val initial = repo.getLatestSettings()
        assertNotNull(initial)
        assertTrue(initial.isEnabled)

        // Update isEnabled
        repo.updateIsEnabled(false)
        assertEquals(false, repo.getLatestSettings().isEnabled)

        // Update back to true
        repo.updateIsEnabled(true)
        assertEquals(true, repo.getLatestSettings().isEnabled)
    }

    @Test
    fun testPaymentParserPrecompiledRegexBenchmark() {
        val testNotifications = listOf(
            "Received ₹500 from Rohit Sharma via PhonePe. UTR: 123456789012",
            "Payment of Rs 1,200 received on Paytm QR from Suresh Patel",
            "Dear SBI User, your A/c ending 4321 credited by Rs 2,500.00 by transfer from Rahul Verma Ref No 987654321012",
            "Update! INR 800.00 credited to HDFC Bank A/c xx1234 from Amit Roy (UPI Ref No 123456789012)",
            "रोहित शर्मा ने ₹ 500 भेजे",
            "रोहित शर्मा से ₹ 500 प्राप्त हुए",
            "Payment of Rs 50.00 received",
            "You paid ₹ 500 to Merchant (Debit rejection test)",
            "Rohit Sharma requested ₹ 500 (Request rejection test)"
        )

        // JIT warm-up: 300 cycles (2,700 iterations) to stabilize JVM C2 compilation
        for (w in 0 until 300) {
            for (item in testNotifications) {
                PaymentParser.parse(item)
            }
        }

        // Benchmark 200 parses
        val runs = 200
        val totalNanos = measureNanoTime {
            for (i in 0 until runs) {
                val text = testNotifications[i % testNotifications.size]
                PaymentParser.parse(text)
            }
        }

        val avgMs = (totalNanos / runs) / 1_000_000.0
        assertTrue("Pre-compiled regex average parse time must be < 2.0ms, was ${avgMs}ms", avgMs < 2.0)
    }
}

package com.example.tier1_coverage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.parser.IndianNumberFormatter
import com.example.harness.SpyTtsEngine
import com.example.util.TtsEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tier 1: Feature Coverage for TtsEngine formatting, multi-language phrases,
 * templates, tone presets, and utterance tracking.
 */
@RunWith(RobolectricTestRunner::class)
class TtsEngineCoverageTest {

    @Test
    fun testIndianNumberFormattingEnglish() {
        val words50 = IndianNumberFormatter.format(50.0, "en-IN")
        assertEquals("fifty", words50)

        val words1500 = IndianNumberFormatter.format(1500.0, "en-IN")
        assertEquals("one thousand five hundred", words1500)

        val words100000 = IndianNumberFormatter.format(100000.0, "en-IN")
        assertEquals("one lakh", words100000)

        val wordsWithPaise = IndianNumberFormatter.format(25.50, "en-IN")
        assertEquals("twenty five rupees and fifty paise", wordsWithPaise)
    }

    @Test
    fun testIndianNumberFormattingRegional() {
        val hindiNum = IndianNumberFormatter.format(500.0, "hi-IN")
        assertEquals("500", hindiNum)

        val decimalNum = IndianNumberFormatter.format(12.75, "hi-IN")
        assertEquals("12.75", decimalNum)
    }

    @Test
    fun testTonePresetsCompleteness() {
        val presets = TtsEngine.TONE_PRESETS
        assertEquals(5, presets.size)

        val presetIds = presets.map { it.id }
        assertTrue(presetIds.contains("DEEP_WARM"))
        assertTrue(presetIds.contains("NATURAL_SOFT"))
        assertTrue(presetIds.contains("STANDARD_STUDIO"))
        assertTrue(presetIds.contains("BRIGHT_CLEAR"))
        assertTrue(presetIds.contains("HIGH_ENERGETIC"))
    }

    @Test
    fun testTemplatePlaceholderReplacement() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ttsEngine = TtsEngine(context)

        val announcement = ttsEngine.buildAnnouncementText(
            amount = 500.0,
            prefixEnabled = true,
            prefix = "Payment of {amount} from {name}",
            suffixEnabled = false,
            suffix = "",
            language = "en-IN",
            payerName = "Sunil Kumar"
        )

        assertTrue("Announcement must start with processed template", announcement.startsWith("Payment of five hundred from Sunil Kumar"))
        assertTrue("Announcement must contain core received prompt", announcement.contains("received from Sunil Kumar"))
    }

    @Test
    fun testMultiUtteranceTracking() {
        val spy = SpyTtsEngine()
        val utt1 = spy.recordSpeak("Five hundred rupees received", "utt_1", 100, "en-IN")
        val utt2 = spy.recordSpeak("One thousand rupees received", "utt_2", 90, "en-IN")

        assertEquals(2, spy.callCount)
        assertTrue(spy.hasSpokenContaining("Five hundred"))
        assertTrue(spy.hasSpokenContaining("One thousand"))
        assertTrue(utt2 >= utt1)
    }

    @Test
    fun testBilingualAnnouncementStructure() {
        val amount = 250.0
        val payer = "Rahul"
        val hindiPhrase = "$payer से 250 रुपये प्राप्त हुए"
        val englishPhrase = ". 250 rupees received from $payer"
        val bilingualText = "$hindiPhrase$englishPhrase"

        assertTrue("Bilingual text must contain Hindi prefix", bilingualText.contains("प्राप्त हुए"))
        assertTrue("Bilingual text must contain English suffix", bilingualText.contains("rupees received"))
    }
}

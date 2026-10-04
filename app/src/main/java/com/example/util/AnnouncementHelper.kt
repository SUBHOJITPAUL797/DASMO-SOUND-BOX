package com.example.util

import android.content.Context
import com.example.data.database.entity.TransactionEntity
import com.example.di.AppModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

object AnnouncementHelper {

    suspend fun replayTransaction(context: Context, transaction: TransactionEntity) {
        AppModule.getOrInit(context)
        val settings = AppModule.settingsRepository?.getSettings()?.first() ?: return
        val tts = AppModule.ttsEngine ?: return

        tts.applyVoiceSettings(
            language = settings.language,
            rate = settings.speechRate,
            pitch = settings.voicePitch,
            voiceName = settings.voiceName
        )

        val announcementText = transaction.announcementText ?: tts.buildAnnouncementText(
            amount = transaction.amount,
            prefixEnabled = settings.customPrefixEnabled,
            prefix = settings.customPrefix,
            suffixEnabled = settings.customSuffixEnabled,
            suffix = settings.customSuffix,
            language = settings.language,
            payerName = if (settings.announcePayerName) transaction.payerName else null,
            bilingualEnabled = settings.bilingualEnabled
        )

        if (settings.flashAlertEnabled) {
            FlashAlertManager.triggerFlash(context, 1)
        }
        if (settings.chimeEnabled) {
            ChimePlayer.play(context, settings.chimeSound, settings.announcementVolume)
            delay(250)
        }

        tts.announceSuspend(
            text = announcementText,
            volume = settings.announcementVolume,
            rate = settings.speechRate,
            pitch = settings.voicePitch,
            voiceName = settings.voiceName
        )
    }

    suspend fun announceDaySummary(context: Context, count: Int, totalAmount: Double) {
        AppModule.getOrInit(context)
        val settings = AppModule.settingsRepository?.getSettings()?.first() ?: return
        val tts = AppModule.ttsEngine ?: return

        tts.applyVoiceSettings(
            language = settings.language,
            rate = settings.speechRate,
            pitch = settings.voicePitch,
            voiceName = settings.voiceName
        )

        val summaryText = tts.buildDailySummaryText(count, totalAmount, settings.language)

        if (settings.chimeEnabled) {
            ChimePlayer.play(context, settings.chimeSound, settings.announcementVolume)
            delay(250)
        }

        tts.announceSuspend(
            text = summaryText,
            volume = settings.announcementVolume,
            rate = settings.speechRate,
            pitch = settings.voicePitch,
            voiceName = settings.voiceName
        )
    }
}

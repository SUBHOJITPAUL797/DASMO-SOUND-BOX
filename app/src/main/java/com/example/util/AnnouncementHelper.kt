package com.example.util

import android.content.Context
import com.example.data.database.entity.TransactionEntity
import com.example.di.AppModule

object AnnouncementHelper {

    suspend fun replayTransaction(context: Context, transaction: TransactionEntity) {
        AppModule.getOrInit(context)
        val settings = AppModule.settingsRepository?.getLatestSettings() ?: return
        val tts = AppModule.ttsEngine ?: return

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

        val sessionToken = tts.acquireSessionToken(settings.announcementVolume)
        try {
            if (settings.chimeEnabled) {
                ChimePlayer.play(context, settings.chimeSound, settings.announcementVolume)
            }

            tts.announceSuspend(
                text = announcementText,
                volume = settings.announcementVolume,
                rate = settings.speechRate,
                pitch = settings.voicePitch,
                voiceName = settings.voiceName,
                manageSession = false
            )
        } finally {
            tts.releaseSessionToken(sessionToken)
        }
    }

    suspend fun announceDaySummary(context: Context, count: Int, totalAmount: Double) {
        AppModule.getOrInit(context)
        val settings = AppModule.settingsRepository?.getLatestSettings() ?: return
        val tts = AppModule.ttsEngine ?: return

        val summaryText = tts.buildDailySummaryText(count, totalAmount, settings.language)

        val sessionToken = tts.acquireSessionToken(settings.announcementVolume)
        try {
            if (settings.chimeEnabled) {
                ChimePlayer.play(context, settings.chimeSound, settings.announcementVolume)
            }

            tts.announceSuspend(
                text = summaryText,
                volume = settings.announcementVolume,
                rate = settings.speechRate,
                pitch = settings.voicePitch,
                voiceName = settings.voiceName,
                manageSession = false
            )
        } finally {
            tts.releaseSessionToken(sessionToken)
        }
    }
}

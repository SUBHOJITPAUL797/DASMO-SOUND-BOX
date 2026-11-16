package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.di.AppModule
import com.example.domain.model.PaymentEvent
import com.example.domain.model.SourceType
import com.example.domain.parser.PaymentParser
import com.example.util.KnownPaymentApps
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface NotificationEvaluationResult {
    data object IgnoredDisabled : NotificationEvaluationResult
    data object IgnoredFlags : NotificationEvaluationResult
    data object IgnoredAppFilter : NotificationEvaluationResult
    data object IgnoredNotCreditPayment : NotificationEvaluationResult
    data object SkippedL1Duplicate : NotificationEvaluationResult
    data class Accepted(val event: PaymentEvent) : NotificationEvaluationResult
}

class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "SoundBoxListener"
        @Volatile
        var isConnected: Boolean = false
            private set

        private val recentNotificationSignatures = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, Long>(100, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                    return size > 250
                }
            }
        )

        fun clearSignatureCacheForTesting() {
            synchronized(recentNotificationSignatures) {
                recentNotificationSignatures.clear()
            }
        }

        fun checkAndRecordL1Signature(signature: String, nowMs: Long = System.currentTimeMillis()): Boolean {
            synchronized(recentNotificationSignatures) {
                recentNotificationSignatures.entries.removeIf { nowMs - it.value > 45000L }
                if (recentNotificationSignatures.containsKey(signature)) {
                    return true
                }
                recentNotificationSignatures[signature] = nowMs
                return false
            }
        }

        fun rebindIfDisconnected(context: android.content.Context) {
            if (isConnected) return
            val componentName = ComponentName(context, PaymentNotificationListener::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    requestRebind(componentName)
                    Log.d(TAG, "Requested rebind for PaymentNotificationListener")
                }
            } catch (e: Throwable) {
                try {
                    val pm = context.packageManager
                    pm.setComponentEnabledSetting(
                        componentName,
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP
                    )
                    pm.setComponentEnabledSetting(
                        componentName,
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP
                    )
                    Log.d(TAG, "Notification listener rebind component toggled successfully")
                } catch (e2: Throwable) {
                    Log.w(TAG, "Component toggle rebind failed: ${e2.message}")
                }
            }
        }
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled coroutine error in NotificationListener: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.d(TAG, "SoundBox Payment Notification Listener connected successfully")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.w(TAG, "SoundBox Payment Notification Listener disconnected - attempting rebind")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(ComponentName(this, PaymentNotificationListener::class.java))
            } catch (e: Throwable) {
                Log.w(TAG, "Failed requestRebind: ${e.message}")
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName ?: return

        scope.launch {
            try {
                AppModule.getOrInit(applicationContext)
                val settings = AppModule.settingsRepository?.getSettings()?.first() ?: return@launch
                when (val eval = evaluateStatusBarNotification(sbn, packageName, settings)) {
                    is NotificationEvaluationResult.Accepted -> {
                        val hasSubscriber = (AppModule.paymentEventBus?.events?.subscriptionCount?.value ?: 0) > 0
                        if ((!SoundBoxForegroundService.isRunning || !hasSubscriber) && settings.isEnabled) {
                            try {
                                SoundBoxForegroundService.start(applicationContext, eval.event)
                            } catch (e: Throwable) {
                                Log.w(TAG, "Could not start service: ${e.message}")
                            }
                        } else {
                            AppModule.paymentEventBus?.emit(eval.event)
                        }
                    }
                    NotificationEvaluationResult.SkippedL1Duplicate -> {
                        Log.d(TAG, "Duplicate notification signature skipped for $packageName")
                    }
                    NotificationEvaluationResult.IgnoredDisabled,
                    NotificationEvaluationResult.IgnoredFlags,
                    NotificationEvaluationResult.IgnoredAppFilter,
                    NotificationEvaluationResult.IgnoredNotCreditPayment -> Unit
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error in onNotificationPosted: ${e.message}", e)
            }
        }
    }

    private fun evaluateStatusBarNotification(
        sbn: StatusBarNotification,
        packageName: String,
        settings: com.example.domain.model.AppSettings
    ): NotificationEvaluationResult {
        if (!settings.isEnabled || !settings.notificationDetectionEnabled) {
            return NotificationEvaluationResult.IgnoredDisabled
        }
        if (settings.disabledAppPackages.contains(packageName)) {
            return NotificationEvaluationResult.IgnoredAppFilter
        }

        val notification = sbn.notification ?: return NotificationEvaluationResult.IgnoredFlags
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return NotificationEvaluationResult.IgnoredFlags
        }
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            return NotificationEvaluationResult.IgnoredFlags
        }
        val extras = notification.extras ?: return NotificationEvaluationResult.IgnoredFlags

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim() ?: ""
        val conversationTitle = extras.getCharSequence("android.conversationTitle")?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim() ?: ""
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.trim() ?: ""
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.trim() ?: ""
        val tickerText = notification.tickerText?.toString()?.trim() ?: ""
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" ") { it.toString().trim() } ?: ""

        val messagesRaw = extras.get("android.messages")
        val messageBundles = mutableListOf<android.os.Bundle>()
        if (messagesRaw is Array<*>) {
            for (item in messagesRaw) {
                if (item is android.os.Bundle) messageBundles.add(item)
            }
        } else if (messagesRaw is List<*>) {
            for (item in messagesRaw) {
                if (item is android.os.Bundle) messageBundles.add(item)
            }
        }

        val (result, candidateFullText) = if (messageBundles.isNotEmpty()) {
            val latestBundle = messageBundles.last()
            val latestMsgText = latestBundle.getCharSequence("text")?.toString()?.trim() ?: ""
            val latestMsgSender = latestBundle.getCharSequence("sender")?.toString()?.trim()
            val fallbackTitle = when {
                !latestMsgSender.isNullOrBlank() -> latestMsgSender
                conversationTitle.isNotBlank() -> conversationTitle
                title.isNotBlank() -> title
                bigTitle.isNotBlank() -> bigTitle
                else -> null
            }

            val candidate = if (!fallbackTitle.isNullOrBlank() && !latestMsgText.contains(fallbackTitle, ignoreCase = true)) {
                "$fallbackTitle $latestMsgText"
            } else {
                latestMsgText
            }

            var parseRes = PaymentParser.parse(
                text = candidate,
                fallbackTitle = fallbackTitle,
                packageName = packageName
            )

            if (parseRes.amount == null || !parseRes.isCredit) {
                val candidateWithContext = listOf(title, bigTitle, latestMsgSender ?: "", latestMsgText, text)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" ")
                parseRes = PaymentParser.parse(
                    text = candidateWithContext,
                    fallbackTitle = fallbackTitle,
                    packageName = packageName
                )
            }

            Pair(parseRes, candidate)
        } else {
            val allParts = listOf(
                title, bigTitle, conversationTitle,
                text, bigText, subText, summaryText, infoText, tickerText, textLines
            ).filter { it.isNotBlank() }

            val fullText = allParts.distinct().joinToString(" ").trim()
            val fallbackTitle = when {
                conversationTitle.isNotBlank() -> conversationTitle
                title.isNotBlank() -> title
                bigTitle.isNotBlank() -> bigTitle
                else -> null
            }

            val parseRes = PaymentParser.parse(
                text = fullText,
                fallbackTitle = fallbackTitle,
                packageName = packageName
            )
            Pair(parseRes, fullText)
        }

        if (!KnownPaymentApps.shouldProcessNotification(packageName, candidateFullText)) {
            return NotificationEvaluationResult.IgnoredAppFilter
        }

        if (result.amount == null || !result.isCredit) {
            return NotificationEvaluationResult.IgnoredNotCreditPayment
        }

        val notifSignature = "${packageName}_${sbn.id}_${sbn.tag}_${result.amount}_${result.payerName ?: ""}_${result.refId ?: ""}"
        if (checkAndRecordL1Signature(notifSignature)) {
            return NotificationEvaluationResult.SkippedL1Duplicate
        }

        val appName = KnownPaymentApps.packageNames[packageName] ?: run {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                val pkgLower = packageName.lowercase()
                when {
                    pkgLower.contains("bhim") || pkgLower.contains("npci") -> "BHIM UPI"
                    pkgLower.contains("kotak") -> "Kotak Bank"
                    pkgLower.contains("sbi") -> "SBI Bank"
                    pkgLower.contains("hdfc") -> "HDFC Bank"
                    pkgLower.contains("icici") -> "ICICI Bank"
                    pkgLower.contains("axis") -> "Axis Bank"
                    pkgLower.contains("pnb") -> "PNB Bank"
                    pkgLower.contains("bank") -> "Bank App"
                    else -> packageName
                }
            }
        }

        return NotificationEvaluationResult.Accepted(
            PaymentEvent(
                amount = result.amount,
                sourceType = SourceType.NOTIFICATION,
                sourceApp = packageName,
                sourceAppName = appName,
                payerName = result.payerName,
                refId = result.refId,
                rawText = candidateFullText
            )
        )
    }
}

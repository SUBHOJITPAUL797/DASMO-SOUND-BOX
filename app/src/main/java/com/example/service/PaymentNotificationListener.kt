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

class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "SoundBoxListener"
        @Volatile
        var isConnected: Boolean = false
            private set
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
                if (!settings.isEnabled || !settings.notificationDetectionEnabled) return@launch
                
                if (settings.disabledAppPackages.contains(packageName)) return@launch
                
                val notification = sbn.notification ?: return@launch
                val extras = notification.extras ?: return@launch

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

                // Extract MessagingStyle messages (Crucial for Google Pay and chat-style UPI notifications)
                val messagingTexts = mutableListOf<String>()
                var messagingSender: String? = null

                fun processMessageBundle(bundle: android.os.Bundle) {
                    val msgText = bundle.getCharSequence("text")?.toString()?.trim()
                    if (!msgText.isNullOrBlank()) {
                        messagingTexts.add(msgText)
                    }
                    val sender = bundle.getCharSequence("sender")?.toString()?.trim()
                    if (!sender.isNullOrBlank() && messagingSender == null) {
                        messagingSender = sender
                    }
                }

                val messagesRaw = extras.get("android.messages")
                if (messagesRaw is Array<*>) {
                    for (item in messagesRaw) {
                        if (item is android.os.Bundle) {
                            processMessageBundle(item)
                        }
                    }
                } else if (messagesRaw is List<*>) {
                    for (item in messagesRaw) {
                        if (item is android.os.Bundle) {
                            processMessageBundle(item)
                        }
                    }
                }

                val historicMessagesRaw = extras.get("android.messages.historic")
                if (historicMessagesRaw is Array<*>) {
                    for (item in historicMessagesRaw) {
                        if (item is android.os.Bundle) {
                            processMessageBundle(item)
                        }
                    }
                } else if (historicMessagesRaw is List<*>) {
                    for (item in historicMessagesRaw) {
                        if (item is android.os.Bundle) {
                            processMessageBundle(item)
                        }
                    }
                }

                // Gather any additional CharSequences in bundle keys
                val extraTokens = mutableListOf<String>()
                for (key in extras.keySet()) {
                    if (key.startsWith("android.messages")) continue
                    when (val obj = extras.get(key)) {
                        is CharSequence -> {
                            val str = obj.toString().trim()
                            if (str.isNotBlank() && str.length < 300) {
                                extraTokens.add(str)
                            }
                        }
                    }
                }

                val allParts = listOf(
                    title, bigTitle, conversationTitle, messagingSender ?: "",
                    messagingTexts.joinToString(" "),
                    text, bigText, subText, summaryText, infoText, tickerText, textLines,
                    extraTokens.joinToString(" ")
                ).filter { it.isNotBlank() }

                val fullText = allParts.distinct().joinToString(" ").trim()
                
                if (!KnownPaymentApps.shouldProcessNotification(packageName, fullText)) return@launch

                val fallbackTitle = when {
                    !messagingSender.isNullOrBlank() -> messagingSender
                    conversationTitle.isNotBlank() -> conversationTitle
                    title.isNotBlank() -> title
                    bigTitle.isNotBlank() -> bigTitle
                    else -> null
                }
                
                val result = PaymentParser.parse(
                    text = fullText,
                    fallbackTitle = fallbackTitle,
                    packageName = packageName
                )
                if (result.amount != null && result.isCredit) {
                    val appName = KnownPaymentApps.packageNames[packageName] ?: run {
                        try {
                            val appInfo = packageManager.getApplicationInfo(packageName, 0)
                            packageManager.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            val pkgLower = packageName.lowercase()
                            when {
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

                    val event = PaymentEvent(
                        amount = result.amount,
                        sourceType = SourceType.NOTIFICATION,
                        sourceApp = packageName,
                        sourceAppName = appName,
                        payerName = result.payerName,
                        refId = result.refId,
                        rawText = fullText
                    )

                    // Auto-start foreground service if enabled but not running, passing the event in intent
                    if (!SoundBoxForegroundService.isRunning && settings.isEnabled) {
                        try {
                            SoundBoxForegroundService.start(applicationContext, event)
                        } catch (e: Throwable) {
                            Log.w(TAG, "Could not start service: ${e.message}")
                        }
                    }

                    AppModule.paymentEventBus?.emit(event)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error in onNotificationPosted: ${e.message}", e)
            }
        }
    }
}

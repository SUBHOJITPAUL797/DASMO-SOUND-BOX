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
                
                val extras = sbn.notification?.extras ?: return@launch
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
                val bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString() ?: ""
                val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
                val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
                val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString() ?: ""
                val tickerText = sbn.notification.tickerText?.toString() ?: ""
                val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(" ") { it.toString() } ?: ""
                
                val fullText = "$title $bigTitle $subText $summaryText $infoText $bigText $text $tickerText $textLines".trim()
                
                if (!KnownPaymentApps.shouldProcessNotification(packageName, fullText)) return@launch
                
                val result = PaymentParser.parse(
                    text = fullText,
                    fallbackTitle = if (title.isNotBlank()) title else bigTitle,
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

                    // Auto-start foreground service if enabled but not running
                    if (!SoundBoxForegroundService.isRunning && settings.isEnabled) {
                        try {
                            SoundBoxForegroundService.start(applicationContext)
                        } catch (e: Throwable) {
                            Log.w(TAG, "Could not start service: ${e.message}")
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
                    AppModule.paymentEventBus?.emit(event)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error in onNotificationPosted: ${e.message}", e)
            }
        }
    }
}

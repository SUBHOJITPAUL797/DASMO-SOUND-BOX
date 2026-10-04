package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.MainActivity
import com.example.R
import com.example.di.AppModule
import com.example.domain.model.AnnouncementStatus
import com.example.util.ChimePlayer
import com.example.util.FlashAlertManager
import com.example.domain.model.PaymentEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SoundBoxForegroundService : LifecycleService() {

    companion object {
        private const val TAG = "SoundBoxService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "soundbox_service"

        @Volatile
        var isRunning: Boolean = false
            private set
        
        fun start(context: Context) {
            try {
                val intent = Intent(context, SoundBoxForegroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start foreground service: ${e.message}")
            }
        }
        
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, SoundBoxForegroundService::class.java))
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to stop foreground service: ${e.message}")
            }
        }
    }

    private var paymentChannel = Channel<PaymentEvent>(Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        // Call startForeground immediately in onCreate to prevent ForegroundServiceDidNotStartInTimeException
        startForegroundSafely()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                AppModule.getOrInit(applicationContext)
                val settings = AppModule.settingsRepository?.getSettings()?.first()
                if (settings != null) {
                    AppModule.ttsEngine?.setLanguage(settings.language)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error initializing settings in service onCreate: ${e.message}")
            }
        }
        
        startQueueWorker()
        observePaymentEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        isRunning = true
        startForegroundSafely()
        return START_STICKY
    }

    private fun startForegroundSafely() {
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or 
                                  ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error starting foreground notification: ${e.message}")
            try {
                startForeground(NOTIFICATION_ID, buildNotification())
            } catch (e2: Throwable) {
                Log.e(TAG, "Fallback startForeground failed: ${e2.message}")
            }
        }
    }

    private fun observePaymentEvents() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                AppModule.getOrInit(applicationContext)
                AppModule.paymentEventBus?.events?.collect { event ->
                    paymentChannel.send(event)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error in observePaymentEvents: ${e.message}")
            }
        }
    }

    private fun startQueueWorker() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                for (event in paymentChannel) {
                    try {
                        processPaymentEventSequential(event)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error processing payment event: ${e.message}", e)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Payment channel loop terminated: ${e.message}")
            }
        }
    }

    private suspend fun processPaymentEventSequential(event: PaymentEvent) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SoundBox:AnnouncementWakelock")
        try {
            wakeLock?.acquire(15000L) // Keep CPU awake max 15 seconds for audio announcement
        } catch (e: Throwable) {
            Log.w(TAG, "Could not acquire wakelock: ${e.message}")
        }

        try {
            AppModule.getOrInit(applicationContext)
            val settings = AppModule.settingsRepository?.getSettings()?.first() ?: return
            if (!settings.isEnabled) return

            val dedupEngine = AppModule.dedupEngine ?: return
            val ttsEngine = AppModule.ttsEngine ?: return
            val saveTransaction = AppModule.saveTransactionUseCase ?: return
            
            val dedupKey = dedupEngine.generateKey(event.amount)
            val isDuplicate = dedupEngine.isDuplicate(event.amount)
            
            val enrichedEvent = event.copy(dedupKey = dedupKey)
            
            if (isDuplicate) {
                saveTransaction(enrichedEvent, AnnouncementStatus.DUPLICATE_SKIPPED, null)
                return
            }

            if (settings.minAmountThreshold > 0 && event.amount < settings.minAmountThreshold) {
                Log.d(TAG, "Skipping payment below threshold: ${event.amount} < ${settings.minAmountThreshold}")
                saveTransaction(enrichedEvent, AnnouncementStatus.DUPLICATE_SKIPPED, "Below threshold ₹${settings.minAmountThreshold}")
                return
            }
            
            // Apply TTS voice tone, speed, pitch and voice name
            ttsEngine.applyVoiceSettings(
                language = settings.language,
                rate = settings.speechRate,
                pitch = settings.voicePitch,
                voiceName = settings.voiceName
            )

            val announcementText = ttsEngine.buildAnnouncementText(
                amount = event.amount,
                prefixEnabled = settings.customPrefixEnabled,
                prefix = settings.customPrefix,
                suffixEnabled = settings.customSuffixEnabled,
                suffix = settings.customSuffix,
                language = settings.language,
                payerName = if (settings.announcePayerName) event.payerName else null,
                bilingualEnabled = settings.bilingualEnabled
            )
            
            if (settings.flashAlertEnabled) {
                FlashAlertManager.triggerFlash(applicationContext, 2)
            }

            if (settings.chimeEnabled) {
                ChimePlayer.play(applicationContext, settings.chimeSound, settings.announcementVolume)
                delay(300) // Allow chime audio to play clearly
            }

            // Suspend until TTS announcement finishes completely (NO overlap)
            ttsEngine.announceSuspend(
                text = announcementText,
                volume = settings.announcementVolume,
                rate = settings.speechRate,
                pitch = settings.voicePitch,
                voiceName = settings.voiceName
            )
            
            saveTransaction(enrichedEvent, AnnouncementStatus.ANNOUNCED, announcementText)
            
            // Small pause between consecutive payments
            delay(300)
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (e: Throwable) {
                // Ignore wake lock release error
            }
        }
    }

    private fun buildNotification(): Notification {
        createNotificationChannel()
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("SoundBox Pro")
            .setContentText("Listening for UPI & Bank payments...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SoundBox Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SoundBox Pro running in background"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            paymentChannel.close()
        } catch (e: Throwable) {}
        try {
            AppModule.ttsEngine?.stop()
        } catch (e: Throwable) {}
        // Do NOT call AppModule.ttsEngine?.shutdown() on singleton!
    }
}

package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import com.example.domain.model.PaymentEvent
import com.example.util.ChimePlayer
import com.example.util.FlashAlertManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ServiceLifecycleState {
    STOPPED,
    STARTING,
    RUNNING,
    DESTROYING
}

sealed interface PaymentPipelineOutcome {
    data object MissingDependencies : PaymentPipelineOutcome
    data object ServiceDisabled : PaymentPipelineOutcome
    data object DuplicateSkipped : PaymentPipelineOutcome
    data object BelowThreshold : PaymentPipelineOutcome
    data class Announced(val announcementText: String) : PaymentPipelineOutcome
}

class SoundBoxForegroundService : LifecycleService() {

    companion object {
        private const val TAG = "SoundBoxService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "soundbox_service"
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_SOURCE_APP = "extra_source_app"
        const val EXTRA_SOURCE_APP_NAME = "extra_source_app_name"
        const val EXTRA_PAYER_NAME = "extra_payer_name"
        const val EXTRA_REF_ID = "extra_ref_id"
        const val EXTRA_RAW_TEXT = "extra_raw_text"
        const val EXTRA_SOURCE_TYPE = "extra_source_type"

        @Volatile
        var lifecycleState: ServiceLifecycleState = ServiceLifecycleState.STOPPED
            private set

        @Volatile
        var isRunning: Boolean = false
            private set

        private fun updateLifecycleState(newState: ServiceLifecycleState) {
            lifecycleState = newState
            isRunning = (newState == ServiceLifecycleState.RUNNING)
        }
        
        fun start(context: Context, initialEvent: PaymentEvent? = null) {
            try {
                val intent = Intent(context, SoundBoxForegroundService::class.java).apply {
                    if (initialEvent != null) {
                        putExtra(EXTRA_AMOUNT, initialEvent.amount)
                        putExtra(EXTRA_SOURCE_APP, initialEvent.sourceApp)
                        putExtra(EXTRA_SOURCE_APP_NAME, initialEvent.sourceAppName)
                        putExtra(EXTRA_PAYER_NAME, initialEvent.payerName)
                        putExtra(EXTRA_REF_ID, initialEvent.refId)
                        putExtra(EXTRA_RAW_TEXT, initialEvent.rawText)
                        putExtra(EXTRA_SOURCE_TYPE, initialEvent.sourceType.name)
                    }
                }
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
    private var queueWorkerJob: Job? = null
    private var eventCollectorJob: Job? = null
    private var appLockMonitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        updateLifecycleState(ServiceLifecycleState.STARTING)

        // Call startForeground immediately in onCreate to prevent ForegroundServiceDidNotStartInTimeException
        startForegroundSafely()
        updateLifecycleState(ServiceLifecycleState.RUNNING)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                AppModule.getOrInit(applicationContext)
                ChimePlayer.init(applicationContext)
                val settings = AppModule.settingsRepository?.getLatestSettings()
                if (settings != null) {
                    AppModule.ttsEngine?.applyVoiceSettings(
                        language = settings.language,
                        rate = settings.speechRate,
                        pitch = settings.voicePitch,
                        voiceName = settings.voiceName
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error initializing settings in service onCreate: ${e.message}")
            }
        }
        
        startQueueWorker()
        observePaymentEvents()
        startAppLockMonitor()
    }

    private fun startAppLockMonitor() {
        appLockMonitorJob?.cancel()
        appLockMonitorJob = lifecycleScope.launch(Dispatchers.Default) {
            while (isActive && lifecycleState == ServiceLifecycleState.RUNNING) {
                try {
                    com.example.util.AppLockManager.checkForegroundAndLock(applicationContext)
                } catch (e: Throwable) {
                    Log.e(TAG, "Error in AppLock monitoring loop: ${e.message}")
                }
                delay(600)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        updateLifecycleState(ServiceLifecycleState.RUNNING)
        startForegroundSafely()

        if (intent != null && intent.hasExtra(EXTRA_AMOUNT)) {
            val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
            if (amount > 0.0) {
                val sourceTypeStr = intent.getStringExtra(EXTRA_SOURCE_TYPE)
                val sourceType = try {
                    if (!sourceTypeStr.isNullOrBlank()) com.example.domain.model.SourceType.valueOf(sourceTypeStr)
                    else com.example.domain.model.SourceType.NOTIFICATION
                } catch (_: Exception) {
                    com.example.domain.model.SourceType.NOTIFICATION
                }
                val event = PaymentEvent(
                    amount = amount,
                    sourceType = sourceType,
                    sourceApp = intent.getStringExtra(EXTRA_SOURCE_APP) ?: "",
                    sourceAppName = intent.getStringExtra(EXTRA_SOURCE_APP_NAME) ?: "",
                    payerName = intent.getStringExtra(EXTRA_PAYER_NAME),
                    refId = intent.getStringExtra(EXTRA_REF_ID),
                    rawText = intent.getStringExtra(EXTRA_RAW_TEXT) ?: ""
                )
                // Consume extra so if the same Intent is re-delivered, it does not re-process
                intent.removeExtra(EXTRA_AMOUNT)
                paymentChannel.trySend(event)
            }
        }

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
        eventCollectorJob?.cancel()
        eventCollectorJob = lifecycleScope.launch(Dispatchers.IO) {
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
        queueWorkerJob?.cancel()
        queueWorkerJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                for (event in paymentChannel) {
                    if (lifecycleState == ServiceLifecycleState.DESTROYING) break
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

    private suspend fun processPaymentEventSequential(event: PaymentEvent): PaymentPipelineOutcome {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SoundBox:AnnouncementWakelock")
        try {
            wakeLock?.acquire(15000L) // Keep CPU awake max 15 seconds for audio announcement
        } catch (e: Throwable) {
            Log.w(TAG, "Could not acquire wakelock: ${e.message}")
        }

        try {
            AppModule.getOrInit(applicationContext)
            val settings = AppModule.settingsRepository?.getLatestSettings()
                ?: return PaymentPipelineOutcome.MissingDependencies
            if (!settings.isEnabled) return PaymentPipelineOutcome.ServiceDisabled

            val dedupEngine = AppModule.dedupEngine ?: return PaymentPipelineOutcome.MissingDependencies
            val ttsEngine = AppModule.ttsEngine ?: return PaymentPipelineOutcome.MissingDependencies
            val saveTransaction = AppModule.saveTransactionUseCase ?: return PaymentPipelineOutcome.MissingDependencies
            
            val dedupKey = dedupEngine.generateKey(event.amount, event.payerName, event.refId)
            val isDuplicate = dedupEngine.isDuplicate(event.amount, event.payerName, event.refId)
            
            val enrichedEvent = event.copy(dedupKey = dedupKey)
            
            if (isDuplicate) {
                saveTransaction(enrichedEvent, AnnouncementStatus.DUPLICATE_SKIPPED, null)
                return PaymentPipelineOutcome.DuplicateSkipped
            }

            if (settings.minAmountThreshold > 0 && event.amount < settings.minAmountThreshold) {
                Log.d(TAG, "Skipping payment below threshold: ${event.amount} < ${settings.minAmountThreshold}")
                saveTransaction(enrichedEvent, AnnouncementStatus.BELOW_THRESHOLD, "Below threshold ₹${settings.minAmountThreshold}")
                return PaymentPipelineOutcome.BelowThreshold
            }

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

            // Coordinate AudioFocus and stream volume BEFORE chime playback
            val sessionToken = ttsEngine.acquireSessionToken(settings.announcementVolume)
            try {
                if (settings.chimeEnabled) {
                    ChimePlayer.play(applicationContext, settings.chimeSound, settings.announcementVolume)
                }

                // Suspend until TTS announcement finishes completely (NO overlap)
                ttsEngine.announceSuspend(
                    text = announcementText,
                    volume = settings.announcementVolume,
                    rate = settings.speechRate,
                    pitch = settings.voicePitch,
                    voiceName = settings.voiceName,
                    manageSession = false
                )
            } finally {
                ttsEngine.releaseSessionToken(sessionToken)
            }
            
            saveTransaction(enrichedEvent, AnnouncementStatus.ANNOUNCED, announcementText)
            return PaymentPipelineOutcome.Announced(announcementText)
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
        val settings = AppModule.settingsRepository?.getLatestSettings()
        val statusText = when {
            settings?.isEnabled == true && settings.appLockEnabled -> "SoundBox & App Lock active"
            settings?.isEnabled == true -> "Listening for UPI & Bank payments..."
            settings?.appLockEnabled == true -> "App Lock & Intruder Shield active"
            else -> "SoundBox Pro standby"
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("DASMO Sound Box")
            .setContentText(statusText)
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
        updateLifecycleState(ServiceLifecycleState.DESTROYING)
        appLockMonitorJob?.cancel()
        eventCollectorJob?.cancel()
        queueWorkerJob?.cancel()
        super.onDestroy()
        updateLifecycleState(ServiceLifecycleState.STOPPED)
        try {
            paymentChannel.close()
        } catch (e: Throwable) {}
        try {
            AppModule.ttsEngine?.stop()
        } catch (e: Throwable) {}
        try {
            ChimePlayer.release()
        } catch (e: Throwable) {}
        // Do NOT call AppModule.ttsEngine?.shutdown() on singleton!
    }
}

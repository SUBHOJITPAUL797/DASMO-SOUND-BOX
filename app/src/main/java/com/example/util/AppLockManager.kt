package com.example.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.di.AppModule
import com.example.presentation.screens.applock.AppLockActivity
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.ConcurrentHashMap

sealed interface ForegroundLockDecision {
    data object Disabled : ForegroundLockDecision
    data object NoCredentials : ForegroundLockDecision
    data object NoForegroundApp : ForegroundLockDecision
    data object SelfApp : ForegroundLockDecision
    data object AlreadyOnForegroundApp : ForegroundLockDecision
    data class UnprotectedApp(val packageName: String) : ForegroundLockDecision
    data class TemporarilyUnlocked(val packageName: String) : ForegroundLockDecision
    data class CooldownDebounced(val packageName: String) : ForegroundLockDecision
    data class PromptRequired(val packageName: String) : ForegroundLockDecision
}

object AppLockManager {

    private val unlockedSessionMap = ConcurrentHashMap<String, Long>()
    private const val UNLOCK_TIMEOUT_MS = 60_000L // 60 seconds grace period while switching
    private const val PROMPT_COOLDOWN_MS = 2000L

    @Volatile
    var lastForegroundPackage: String? = null
    @Volatile private var lastLockPromptPackage: String? = null
    @Volatile private var lastLockPromptTimeMs: Long = 0L

    fun hasConfiguredCredentials(settings: com.example.domain.model.AppSettings): Boolean {
        return settings.appLockPattern.isNotBlank() ||
            settings.appLockPin.isNotBlank() ||
            settings.appLockPassword.isNotBlank()
    }

    fun evaluateForegroundLock(
        settings: com.example.domain.model.AppSettings,
        foregroundPackage: String?,
        selfPackage: String,
        nowMs: Long = System.currentTimeMillis()
    ): ForegroundLockDecision {
        if (!settings.appLockEnabled) return ForegroundLockDecision.Disabled
        if (!hasConfiguredCredentials(settings)) return ForegroundLockDecision.NoCredentials
        if (foregroundPackage.isNullOrBlank()) return ForegroundLockDecision.NoForegroundApp
        if (foregroundPackage == selfPackage) return ForegroundLockDecision.SelfApp
        if (foregroundPackage == lastForegroundPackage) {
            return ForegroundLockDecision.AlreadyOnForegroundApp
        }
        if (!settings.lockedAppPackages.contains(foregroundPackage)) {
            return ForegroundLockDecision.UnprotectedApp(foregroundPackage)
        }
        if (isAppTemporarilyUnlocked(foregroundPackage, nowMs)) {
            return ForegroundLockDecision.TemporarilyUnlocked(foregroundPackage)
        }
        if (foregroundPackage == lastLockPromptPackage && nowMs - lastLockPromptTimeMs < PROMPT_COOLDOWN_MS) {
            return ForegroundLockDecision.CooldownDebounced(foregroundPackage)
        }
        return ForegroundLockDecision.PromptRequired(foregroundPackage)
    }

    fun isAppTemporarilyUnlocked(packageName: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val unlockTime = unlockedSessionMap[packageName] ?: return false
        if (nowMs - unlockTime < UNLOCK_TIMEOUT_MS) {
            return true
        }
        unlockedSessionMap.remove(packageName)
        return false
    }

    fun markAppUnlocked(packageName: String) {
        unlockedSessionMap[packageName] = System.currentTimeMillis()
    }

    fun relockAll() {
        unlockedSessionMap.clear()
        lastForegroundPackage = null
        lastLockPromptPackage = null
        lastLockPromptTimeMs = 0L
    }

    fun hasUsageAccessPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getForegroundPackage(context: Context): String? {
        if (!hasUsageAccessPermission(context)) return null

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 60_000 // Last 60 seconds

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var currentForeground: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentForeground = event.packageName
            }
        }
        return currentForeground
    }

    fun getAppName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            KnownPaymentApps.packageNames[packageName] ?: packageName
        }
    }

    fun getAppIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Called by background monitor to check foreground app and prompt lock activity if needed.
     */
    suspend fun checkForegroundAndLock(context: Context) {
        val settings = AppModule.settingsRepository?.getSettings()?.firstOrNull() ?: return
        val foreground = getForegroundPackage(context)
        val now = System.currentTimeMillis()

        when (val decision = evaluateForegroundLock(settings, foreground, context.packageName, now)) {
            is ForegroundLockDecision.PromptRequired -> {
                lastForegroundPackage = decision.packageName
                lastLockPromptPackage = decision.packageName
                lastLockPromptTimeMs = now
                val intent = Intent(context, AppLockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(AppLockActivity.EXTRA_PACKAGE_NAME, decision.packageName)
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // May fail silently if overlay/background activity start is restricted
                }
            }
            is ForegroundLockDecision.UnprotectedApp -> {
                lastForegroundPackage = decision.packageName
            }
            ForegroundLockDecision.Disabled,
            ForegroundLockDecision.NoCredentials,
            ForegroundLockDecision.NoForegroundApp,
            ForegroundLockDecision.SelfApp,
            ForegroundLockDecision.AlreadyOnForegroundApp,
            is ForegroundLockDecision.TemporarilyUnlocked,
            is ForegroundLockDecision.CooldownDebounced -> Unit
        }
    }
}


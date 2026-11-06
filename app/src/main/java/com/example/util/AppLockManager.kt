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

object AppLockManager {

    private val unlockedSessionMap = ConcurrentHashMap<String, Long>()
    private const val UNLOCK_TIMEOUT_MS = 60_000L // 60 seconds grace period while switching

    var lastForegroundPackage: String? = null

    fun isAppTemporarilyUnlocked(packageName: String): Boolean {
        val unlockTime = unlockedSessionMap[packageName] ?: return false
        if (System.currentTimeMillis() - unlockTime < UNLOCK_TIMEOUT_MS) {
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
        val startTime = endTime - 10_000 // Last 10 seconds

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
        if (!settings.appLockEnabled) return

        val foreground = getForegroundPackage(context) ?: return
        if (foreground == context.packageName) return // We handle our own internal locks via activity/screen
        if (foreground == lastForegroundPackage) return // Already showing or active

        if (settings.lockedAppPackages.contains(foreground)) {
            if (!isAppTemporarilyUnlocked(foreground)) {
                lastForegroundPackage = foreground
                val intent = Intent(context, AppLockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(AppLockActivity.EXTRA_PACKAGE_NAME, foreground)
                }
                context.startActivity(intent)
            }
        } else {
            lastForegroundPackage = foreground
        }
    }
}


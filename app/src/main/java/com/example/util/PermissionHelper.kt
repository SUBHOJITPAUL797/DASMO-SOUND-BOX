package com.example.util

import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat

object PermissionHelper {
    fun isNotificationListenerEnabled(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}

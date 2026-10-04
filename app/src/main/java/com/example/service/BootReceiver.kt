package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON") {
            try {
                SoundBoxForegroundService.start(context)
                scheduleWatchdog(context)
            } catch (e: Throwable) {
                // Prevent boot crash
            }
        }
    }

    private fun scheduleWatchdog(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, WatchdogReceiver::class.java).apply {
            action = "com.soundboxpro.WATCHDOG_CHECK"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val interval = AlarmManager.INTERVAL_FIFTEEN_MINUTES
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + interval,
            interval,
            pendingIntent
        )
    }
}

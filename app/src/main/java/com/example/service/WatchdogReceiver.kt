package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.di.AppModule

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.soundboxpro.WATCHDOG_CHECK") {
            try {
                if (!SoundBoxForegroundService.isRunning) {
                    AppModule.getOrInit(context)
                    SoundBoxForegroundService.start(context)
                }
            } catch (e: Throwable) {
                Log.w("WatchdogReceiver", "Watchdog check failed: ${e.message}")
            }
        }
    }
}

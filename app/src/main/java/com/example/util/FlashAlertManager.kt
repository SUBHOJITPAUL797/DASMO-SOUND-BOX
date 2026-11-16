package com.example.util

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

enum class TorchState {
    IDLE,
    PULSING
}

object FlashAlertManager {

    private const val TAG = "FlashAlertManager"
    private val torchMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateRef = AtomicReference(TorchState.IDLE)

    @VisibleForTesting
    val currentState: TorchState
        get() = stateRef.get()

    fun triggerFlash(context: Context, pulseCount: Int = 2) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pulses = pulseCount.coerceIn(1, 5)
        val appContext = context.applicationContext

        scope.launch {
            pulseSerialized(appContext, pulses)
        }
    }

    internal suspend fun pulseSerialized(context: Context, pulseCount: Int = 2) {
        torchMutex.withLock {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } catch (_: Exception) {
                    false
                }
            } ?: return

            stateRef.set(TorchState.PULSING)
            try {
                repeat(pulseCount) { index ->
                    cameraManager.setTorchMode(cameraId, true)
                    delay(120)
                    cameraManager.setTorchMode(cameraId, false)
                    if (index < pulseCount - 1) {
                        delay(100)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Flash alert error: ${e.message}")
            } finally {
                try {
                    cameraManager.setTorchMode(cameraId, false)
                } catch (_: Exception) {
                    // Best-effort turn off to ensure camera LED never stays stuck ON
                }
                stateRef.set(TorchState.IDLE)
            }
        }
    }
}

package com.example.util

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object FlashAlertManager {

    private const val TAG = "FlashAlertManager"

    fun triggerFlash(context: Context, pulseCount: Int = 2) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return@launch
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    try {
                        val chars = cameraManager.getCameraCharacteristics(id)
                        chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } catch (e: Exception) {
                        false
                    }
                } ?: return@launch

                repeat(pulseCount) {
                    try {
                        cameraManager.setTorchMode(cameraId, true)
                        delay(120)
                        cameraManager.setTorchMode(cameraId, false)
                        delay(100)
                    } catch (e: Exception) {
                        Log.w(TAG, "Torch toggle error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Flash alert unavailable: ${e.message}")
            }
        }
    }
}

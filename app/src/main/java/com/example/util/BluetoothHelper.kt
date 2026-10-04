package com.example.util

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

object BluetoothHelper {

    fun isBluetoothAudioConnected(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                devices.any { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER) ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
            }
        } catch (e: Exception) {
            false
        }
    }
}

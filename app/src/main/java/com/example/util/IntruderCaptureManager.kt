package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import androidx.core.content.ContextCompat
import com.example.data.database.entity.IntruderLogEntity
import com.example.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

object IntruderCaptureManager {

    private const val TAG = "IntruderCaptureManager"
    private val isCapturing = AtomicBoolean(false)

    /**
     * Silently captures an intruder selfie using the front camera and logs it.
     */
    fun captureIntruderPhoto(
        context: Context,
        targetPackage: String,
        targetAppName: String,
        lockType: String = "PATTERN",
        failedAttempts: Int = 1
    ) {
        val appContext = context.applicationContext

        // Check camera permission
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Camera permission not granted. Creating text-only security incident log.")
            recordIncidentWithoutPhoto(appContext, targetPackage, targetAppName, lockType, failedAttempts)
            return
        }

        // Prevent overlapping captures
        if (!isCapturing.compareAndSet(false, true)) {
            Log.d(TAG, "Capture already in progress, recording incident log without photo.")
            recordIncidentWithoutPhoto(appContext, targetPackage, targetAppName, lockType, failedAttempts)
            return
        }

        val handlerThread = HandlerThread("IntruderCaptureThread").apply { start() }
        val handler = Handler(handlerThread.looper)

        val isCleanedUp = AtomicBoolean(false)
        var cameraDevice: CameraDevice? = null
        var captureSession: CameraCaptureSession? = null
        var imageReader: ImageReader? = null

        fun cleanup() {
            if (isCleanedUp.compareAndSet(false, true)) {
                handler.removeCallbacksAndMessages(null)
                try { captureSession?.close() } catch (_: Exception) {}
                try { cameraDevice?.close() } catch (_: Exception) {}
                try { imageReader?.close() } catch (_: Exception) {}
                try { handlerThread.quitSafely() } catch (_: Exception) {}
                isCapturing.set(false)
                Log.d(TAG, "Intruder capture resources cleaned up.")
            }
        }

        // 8-second watchdog timer to prevent indefinite lockup
        val timeoutRunnable = Runnable {
            Log.w(TAG, "Intruder capture timed out (8s limit reached). Forcing cleanup.")
            cleanup()
            recordIncidentWithoutPhoto(appContext, targetPackage, targetAppName, lockType, failedAttempts)
        }
        handler.postDelayed(timeoutRunnable, 8000L)

        try {
            val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var selectedCameraId: String? = null
            var sensorOrientation = 0

            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    selectedCameraId = id
                    sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
                    break
                }
            }

            // Fallback to back camera if no front camera is found
            if (selectedCameraId == null && cameraManager.cameraIdList.isNotEmpty()) {
                selectedCameraId = cameraManager.cameraIdList[0]
                val characteristics = cameraManager.getCameraCharacteristics(selectedCameraId)
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            }

            if (selectedCameraId == null) {
                Log.e(TAG, "No camera device available.")
                cleanup()
                recordIncidentWithoutPhoto(appContext, targetPackage, targetAppName, lockType, failedAttempts)
                return
            }

            // Query supported output sizes from StreamConfigurationMap
            val characteristics = cameraManager.getCameraCharacteristics(selectedCameraId)
            val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val supportedSizes = streamMap?.getOutputSizes(ImageFormat.JPEG)

            val targetWidth = 640
            val targetHeight = 480
            val chosenSize = supportedSizes?.minByOrNull { size ->
                val dW = size.width - targetWidth
                val dH = size.height - targetHeight
                dW * dW + dH * dH
            } ?: Size(targetWidth, targetHeight)

            val reader = ImageReader.newInstance(chosenSize.width, chosenSize.height, ImageFormat.JPEG, 1)
            imageReader = reader

            reader.setOnImageAvailableListener({ ir ->
                try {
                    val image = ir.acquireLatestImage()
                    if (image != null) {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)

                            // Save image to files dir directly (no double rotation)
                            val photoFile = savePhotoWithOrientation(appContext, bytes, sensorOrientation)

                            // Insert into DB
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    AppModule.getOrInit(appContext)
                                    AppModule.intruderRepository?.insertLog(
                                        IntruderLogEntity(
                                            appPackage = targetPackage,
                                            appName = targetAppName,
                                            photoPath = photoFile.absolutePath,
                                            timestamp = System.currentTimeMillis(),
                                            lockType = lockType,
                                            failedAttempts = failedAttempts
                                        )
                                    )
                                    Log.i(TAG, "Intruder photo successfully logged: ${photoFile.absolutePath} for $targetAppName")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to insert intruder log entity", e)
                                }
                            }
                        } finally {
                            image.close()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing intruder photo bytes", e)
                } finally {
                    cleanup()
                }
            }, handler)

            cameraManager.openCamera(selectedCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    try {
                        val surface = reader.surface
                        val surfaces = listOf(surface)

                        camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                try {
                                    val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                        addTarget(surface)
                                        set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                                        set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation)
                                    }

                                    session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureCompleted(
                                            session: CameraCaptureSession,
                                            request: CaptureRequest,
                                            result: TotalCaptureResult
                                        ) {
                                            super.onCaptureCompleted(session, request, result)
                                            try {
                                                session.close()
                                                camera.close()
                                            } catch (_: Exception) {}
                                        }

                                        override fun onCaptureFailed(
                                            session: CameraCaptureSession,
                                            request: CaptureRequest,
                                            failure: CaptureFailure
                                        ) {
                                            super.onCaptureFailed(session, request, failure)
                                            Log.e(TAG, "Capture failed: ${failure.reason}")
                                            cleanup()
                                            recordIncidentWithoutPhoto(appContext, targetPackage, targetAppName, lockType, failedAttempts)
                                        }
                                    }, handler)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to build or start capture request", e)
                                    cleanup()
                                    recordIncidentWithoutPhoto(appContext, targetPackage, targetAppName, lockType, failedAttempts)
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e(TAG, "Camera capture session configuration failed")
                                cleanup()
                                recordIncidentWithoutPhoto(appContext, targetPackage, targetAppName, lockType, failedAttempts)
                            }
                        }, handler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed creating capture session", e)
                        cleanup()
                        recordIncidentWithoutPhoto(appContext, targetPackage, targetAppName, lockType, failedAttempts)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraDevice = camera
                    cleanup()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera device error: $error")
                    cameraDevice = camera
                    cleanup()
                    recordIncidentWithoutPhoto(appContext, targetPackage, targetAppName, lockType, failedAttempts)
                }
            }, handler)

        } catch (e: Exception) {
            Log.e(TAG, "Exception during silent intruder photo capture", e)
            cleanup()
            recordIncidentWithoutPhoto(appContext, targetPackage, targetAppName, lockType, failedAttempts)
        }
    }

    // Direct byte stream persistence — eliminates double rotation and avoids Bitmap allocation
    private fun savePhotoWithOrientation(context: Context, rawBytes: ByteArray, orientation: Int = 0): File {
        val dir = File(context.filesDir, "intruder_photos").apply { if (!exists()) mkdirs() }
        val file = File(dir, "intruder_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            out.write(rawBytes)
        }
        return file
    }

    private fun recordIncidentWithoutPhoto(
        context: Context,
        targetPackage: String,
        targetAppName: String,
        lockType: String,
        failedAttempts: Int
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppModule.getOrInit(context)
                AppModule.intruderRepository?.insertLog(
                    IntruderLogEntity(
                        appPackage = targetPackage,
                        appName = targetAppName,
                        photoPath = "",
                        timestamp = System.currentTimeMillis(),
                        lockType = lockType,
                        failedAttempts = failedAttempts
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record incident without photo", e)
            }
        }
    }
}


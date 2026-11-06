package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
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
            Log.d(TAG, "Capture already in progress, skipping duplicate trigger.")
            return
        }

        val handlerThread = HandlerThread("IntruderCaptureThread").apply { start() }
        val handler = Handler(handlerThread.looper)

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
                isCapturing.set(false)
                handlerThread.quitSafely()
                recordIncidentWithoutPhoto(appContext, targetPackage, targetAppName, lockType, failedAttempts)
                return
            }

            // Choose 640x480 or closest available resolution for quick silent capture
            val imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1)

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)

                        // Save image to files dir
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
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing intruder photo bytes", e)
                    } finally {
                        image.close()
                        reader.close()
                        isCapturing.set(false)
                        handlerThread.quitSafely()
                    }
                }
            }, handler)

            cameraManager.openCamera(selectedCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        val surface = imageReader.surface
                        val surfaces = listOf(surface)

                        camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
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
                                            try {
                                                session.close()
                                                camera.close()
                                            } catch (_: Exception) {}
                                            isCapturing.set(false)
                                            handlerThread.quitSafely()
                                        }
                                    }, handler)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to build or start capture request", e)
                                    camera.close()
                                    isCapturing.set(false)
                                    handlerThread.quitSafely()
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e(TAG, "Camera capture session configuration failed")
                                camera.close()
                                isCapturing.set(false)
                                handlerThread.quitSafely()
                            }
                        }, handler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed creating capture session", e)
                        camera.close()
                        isCapturing.set(false)
                        handlerThread.quitSafely()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    isCapturing.set(false)
                    handlerThread.quitSafely()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera device error: $error")
                    camera.close()
                    isCapturing.set(false)
                    handlerThread.quitSafely()
                }
            }, handler)

        } catch (e: Exception) {
            Log.e(TAG, "Exception during silent intruder photo capture", e)
            isCapturing.set(false)
            handlerThread.quitSafely()
            recordIncidentWithoutPhoto(appContext, targetPackage, targetAppName, lockType, failedAttempts)
        }
    }

    private fun savePhotoWithOrientation(context: Context, rawBytes: ByteArray, orientation: Int): File {
        val dir = File(context.filesDir, "intruder_photos").apply { if (!exists()) mkdirs() }
        val file = File(dir, "intruder_${System.currentTimeMillis()}.jpg")

        return try {
            val originalBitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            if (originalBitmap != null) {
                val matrix = Matrix()
                // Front cameras are mirrored; rotate to look natural
                if (orientation != 0) {
                    matrix.postRotate(orientation.toFloat())
                }
                val rotatedBitmap = Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.width,
                    originalBitmap.height,
                    matrix,
                    true
                )

                FileOutputStream(file).use { out ->
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                originalBitmap.recycle()
                if (rotatedBitmap != originalBitmap) {
                    rotatedBitmap.recycle()
                }
                file
            } else {
                FileOutputStream(file).use { it.write(rawBytes) }
                file
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting photo orientation, writing raw bytes", e)
            FileOutputStream(file).use { it.write(rawBytes) }
            file
        }
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


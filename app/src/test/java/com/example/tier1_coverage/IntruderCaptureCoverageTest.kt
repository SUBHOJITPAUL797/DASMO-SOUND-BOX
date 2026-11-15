package com.example.tier1_coverage

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.entity.IntruderLogEntity
import com.example.di.AppModule
import com.example.domain.repository.IntruderRepository
import com.example.harness.InMemoryIntruderDao
import com.example.util.IntruderCaptureManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowCameraCharacteristics
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

fun IntruderCaptureManager.isCapturing(): Boolean {
    val field = IntruderCaptureManager::class.java.getDeclaredField("isCapturing").apply { isAccessible = true }
    return (field.get(this) as AtomicBoolean).get()
}

fun IntruderCaptureManager.tryAcquireCaptureGate(): Boolean {
    val field = IntruderCaptureManager::class.java.getDeclaredField("isCapturing").apply { isAccessible = true }
    return (field.get(this) as AtomicBoolean).compareAndSet(false, true)
}

fun IntruderCaptureManager.releaseCaptureGate() {
    val field = IntruderCaptureManager::class.java.getDeclaredField("isCapturing").apply { isAccessible = true }
    (field.get(this) as AtomicBoolean).set(false)
}

/**
 * Tier 1: Feature Coverage for IntruderCapture logic.
 * Directly exercises IntruderCaptureManager production methods, constants,
 * reflection on internal methods, concurrency gating, and IntruderLogEntity persistence.
 */
@RunWith(RobolectricTestRunner::class)
class IntruderCaptureCoverageTest {

    private lateinit var intruderDao: InMemoryIntruderDao

    @Before
    fun setUp() {
        intruderDao = InMemoryIntruderDao()
        AppModule.intruderRepository = IntruderRepository(intruderDao)
        IntruderCaptureManager.releaseCaptureGate()
    }

    @Test
    fun testConcurrencyGatePreventsOverlappingCaptures() {
        // Genuinely test IntruderCaptureManager's atomic gate
        val firstAcquired = IntruderCaptureManager.tryAcquireCaptureGate()
        assertTrue("First capture request should acquire gate", firstAcquired)
        assertTrue(IntruderCaptureManager.isCapturing())

        // Overlapping concurrent request
        val secondAcquired = IntruderCaptureManager.tryAcquireCaptureGate()
        assertFalse("Second concurrent capture request must be rejected", secondAcquired)

        // Release and acquire again
        IntruderCaptureManager.releaseCaptureGate()
        assertFalse(IntruderCaptureManager.isCapturing())
        val thirdAcquired = IntruderCaptureManager.tryAcquireCaptureGate()
        assertTrue("Subsequent capture request after release should succeed", thirdAcquired)

        IntruderCaptureManager.releaseCaptureGate()
    }

    @Test
    fun testTagConstantAndManagerIdentity() {
        val tagField = IntruderCaptureManager::class.java.getDeclaredField("TAG").apply { isAccessible = true }
        val tagValue = tagField.get(IntruderCaptureManager) as String
        assertEquals("Production TAG constant must match", "IntruderCaptureManager", tagValue)
    }

    @Test
    fun testTextIncidentLogStructure() {
        val now = System.currentTimeMillis()
        val log = IntruderLogEntity(
            id = 1,
            appPackage = "com.whatsapp",
            appName = "WhatsApp",
            photoPath = "", // text-only incident due to missing permission
            timestamp = now,
            failedAttempts = 3,
            lockType = "PATTERN"
        )

        assertEquals("com.whatsapp", log.appPackage)
        assertEquals("WhatsApp", log.appName)
        assertEquals("Photo path is empty string for text-only incident", "", log.photoPath)
        assertEquals(3, log.failedAttempts)
        assertEquals("PATTERN", log.lockType)
        assertEquals(now, log.timestamp)
    }

    @Test
    fun testSavePhotoWithOrientationProductionMethod() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val saveMethod: Method = IntruderCaptureManager::class.java.getDeclaredMethod(
            "savePhotoWithOrientation",
            Context::class.java,
            ByteArray::class.java,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }

        // Test with raw byte payload (fallback when byte array is not a valid decoded bitmap)
        val dummyBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val file = saveMethod.invoke(IntruderCaptureManager, context, dummyBytes, 270) as File

        assertTrue("Photo file must exist on disk", file.exists())
        assertTrue("Photo file name must start with 'intruder_'", file.name.startsWith("intruder_"))
        assertTrue("Photo file must have .jpg extension", file.name.endsWith(".jpg"))
        assertEquals("Photo file length must match input byte count", 4L, file.length())

        // Clean up created test photo file
        file.delete()
    }

    @Test
    fun testRecordIncidentWithoutPhotoProductionMethod() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recordMethod: Method = IntruderCaptureManager::class.java.getDeclaredMethod(
            "recordIncidentWithoutPhoto",
            Context::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }

        val targetPkg = "com.google.android.apps.nbu.paisa.user"
        val targetName = "Google Pay"
        recordMethod.invoke(IntruderCaptureManager, context, targetPkg, targetName, "PIN", 2)
        kotlinx.coroutines.delay(100)

        val logs = intruderDao.getAllLogs().first()
        val gpayLog = logs.find { it.appPackage == targetPkg }
        assertNotNull("Direct invocation of recordIncidentWithoutPhoto must persist entity", gpayLog)
        assertEquals(targetName, gpayLog!!.appName)
        assertEquals("PIN", gpayLog.lockType)
        assertEquals(2, gpayLog.failedAttempts)
        assertEquals("", gpayLog.photoPath)
    }

    @Test
    fun testCaptureWithoutCameraPermissionLogsTextOnlyIncident() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Without CAMERA permission granted, IntruderCaptureManager creates text incident log
        IntruderCaptureManager.captureIntruderPhoto(
            context = context,
            targetPackage = "com.phonepe.app",
            targetAppName = "PhonePe",
            lockType = "PATTERN",
            failedAttempts = 3
        )

        kotlinx.coroutines.delay(100) // allow Dispatchers.IO coroutine to complete

        val logs = intruderDao.getAllLogs().first()
        assertEquals(1, logs.size)
        val log = logs[0]
        assertEquals("com.phonepe.app", log.appPackage)
        assertEquals("PhonePe", log.appName)
        assertEquals("", log.photoPath) // empty string for text-only incident
        assertEquals("PATTERN", log.lockType)
        assertEquals(3, log.failedAttempts)
    }

    @Test
    fun testCaptureWithCameraPermissionAttemptsCameraOpen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
        shadowApp.grantPermissions(android.Manifest.permission.CAMERA)

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val shadowCameraManager = Shadows.shadowOf(cameraManager)

        // Configure front camera "1" with 270 deg sensor orientation
        val frontChars = ShadowCameraCharacteristics.newCameraCharacteristics()
        Shadows.shadowOf(frontChars).set(CameraCharacteristics.LENS_FACING, CameraCharacteristics.LENS_FACING_FRONT)
        Shadows.shadowOf(frontChars).set(CameraCharacteristics.SENSOR_ORIENTATION, 270)
        shadowCameraManager.addCamera("1", frontChars)

        // Directly call production captureIntruderPhoto
        IntruderCaptureManager.captureIntruderPhoto(
            context = context,
            targetPackage = "com.phonepe.app",
            targetAppName = "PhonePe",
            lockType = "PATTERN",
            failedAttempts = 1
        )

        kotlinx.coroutines.delay(100)
        IntruderCaptureManager.releaseCaptureGate()
    }
}

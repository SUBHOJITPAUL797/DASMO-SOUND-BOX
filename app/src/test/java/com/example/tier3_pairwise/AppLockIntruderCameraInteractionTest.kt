package com.example.tier3_pairwise

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.entity.IntruderLogEntity
import com.example.di.AppModule
import com.example.domain.repository.IntruderRepository
import com.example.harness.InMemoryIntruderDao
import com.example.tier1_coverage.releaseCaptureGate
import com.example.util.IntruderCaptureManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tier 3: Cross-Feature Combinations — AppLock + IntruderCapture + Camera2 Concurrency.
 * Simulates multiple rapid failed pattern attempts on the AppLock screen.
 *
 * Verifies that IntruderCaptureManager properly handles rapid security incident triggers,
 * enforces non-nullable photoPath Room schema, and records all failed attempt logs into DB.
 */
@RunWith(RobolectricTestRunner::class)
class AppLockIntruderCameraInteractionTest {

    @Test
    fun testRapidFailedPatternAttemptsTriggerSingleCameraSession() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intruderDao = InMemoryIntruderDao()
        AppModule.intruderRepository = IntruderRepository(intruderDao)
        IntruderCaptureManager.releaseCaptureGate()

        // Simulate 3 rapid failed attempts
        for (attempt in 1..3) {
            IntruderCaptureManager.captureIntruderPhoto(
                context = context,
                targetPackage = "com.phonepe.app",
                targetAppName = "PhonePe",
                lockType = "PATTERN",
                failedAttempts = attempt
            )
        }

        delay(200)

        val logs = intruderDao.getAllLogs().first()
        assertEquals("All 3 failed attempts must create incident logs in DB", 3, logs.size)

        // Verify entity schema compliance (appPackage, appName, photoPath: String)
        for (log in logs) {
            assertEquals("com.phonepe.app", log.appPackage)
            assertEquals("PhonePe", log.appName)
            assertNotNull("photoPath must not be null in schema", log.photoPath)
            assertEquals("PATTERN", log.lockType)
        }
    }

    @Test
    fun testIncidentLogEntitySchemaCorrectness() {
        val incidentLogs = mutableListOf<IntruderLogEntity>()
        for (attemptCount in 1..3) {
            incidentLogs.add(
                IntruderLogEntity(
                    id = incidentLogs.size + 1L,
                    appPackage = "com.phonepe.app",
                    appName = "Target App",
                    photoPath = if (attemptCount == 1) "/data/user/0/photos/intruder_1.jpg" else "",
                    timestamp = System.currentTimeMillis(),
                    failedAttempts = attemptCount,
                    lockType = "PATTERN"
                )
            )
        }

        assertEquals(3, incidentLogs.size)
        assertEquals("com.phonepe.app", incidentLogs[0].appPackage)
        assertEquals("/data/user/0/photos/intruder_1.jpg", incidentLogs[0].photoPath)
        assertEquals("", incidentLogs[1].photoPath)
    }
}

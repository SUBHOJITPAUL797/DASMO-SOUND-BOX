package com.example.tier1_coverage

import com.example.data.database.entity.DedupEntity
import com.example.harness.MockPaymentInjectionHarness
import com.example.util.DedupEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tier 1: Feature Coverage for DedupEngine.
 * Covers identical deduplication, different amounts, window expiration,
 * key generation, and database cleanup.
 */
class DedupEngineCoverageTest {

    private lateinit var dedupDao: MockPaymentInjectionHarness.InMemoryDedupDao
    private lateinit var settingsRepo: MockPaymentInjectionHarness.InMemorySettingsRepository
    private lateinit var dedupEngine: DedupEngine

    @Before
    fun setUp() {
        dedupDao = MockPaymentInjectionHarness.InMemoryDedupDao()
        settingsRepo = MockPaymentInjectionHarness.InMemorySettingsRepository()
        dedupEngine = DedupEngine(dedupDao, settingsRepo)
    }

    @Test
    fun testIdenticalPaymentDeduplicated() = runBlocking {
        val amount = 150.00
        val isFirstDup = dedupEngine.isDuplicate(amount)
        assertFalse("First payment must not be a duplicate", isFirstDup)

        val isSecondDup = dedupEngine.isDuplicate(amount)
        assertTrue("Second identical payment within window must be identified as duplicate", isSecondDup)
    }

    @Test
    fun testDifferentAmountsNotDeduplicated() = runBlocking {
        val firstDup = dedupEngine.isDuplicate(100.00)
        val secondDup = dedupEngine.isDuplicate(200.00)

        assertFalse("First payment of ₹100 should not be duplicate", firstDup)
        assertFalse("Second payment of ₹200 should not be duplicate", secondDup)
    }

    @Test
    fun testDedupKeyGenerationFormat() = runBlocking {
        val key = dedupEngine.generateKey(49.99)
        assertTrue("Dedup key should start with formatted amount", key.startsWith("49.99_"))
        val parts = key.split("_")
        assertEquals("Expected amount and timestamp bucket", 2, parts.size)
        assertTrue("Bucket should be a positive number", parts[1].toLong() > 0L)
    }

    @Test
    fun testSlidingWindowExpiry() = runBlocking {
        val now = System.currentTimeMillis()
        val expiredEntity = DedupEntity(
            dedupKey = "50.00_1000",
            timestamp = now - 60_000L,
            amount = 50.0,
            expiresAt = now - 10_000L // expired 10s ago
        )
        dedupDao.insertRecord(expiredEntity)

        val record = dedupDao.getValidRecord("50.00_1000", now)
        assertNull("Expired record should not be retrieved as valid", record)

        dedupDao.cleanupExpiredRecords(now)
        assertEquals("Expired record must be purged", 0, dedupDao.size())
    }

    @Test
    fun testCleanupRemovesOnlyExpired() = runBlocking {
        val now = System.currentTimeMillis()
        dedupDao.insertRecord(
            DedupEntity("old_key", now - 40_000L, 10.0, expiresAt = now - 5000L)
        )
        dedupDao.insertRecord(
            DedupEntity("active_key", now, 20.0, expiresAt = now + 25_000L)
        )

        dedupDao.cleanupExpiredRecords(now)
        assertEquals("Active record should remain after cleanup", 1, dedupDao.size())
        assertNotNull("Active record should be found", dedupDao.getValidRecord("active_key", now))
    }

    @Test
    fun testCustomWindowSecondsRespected() = runBlocking {
        settingsRepo.updateDirectly { it.copy(dedupWindowSeconds = 5) }
        val amount = 75.0
        val isFirst = dedupEngine.isDuplicate(amount)
        assertFalse(isFirst)

        val isSecond = dedupEngine.isDuplicate(amount)
        assertTrue(isSecond)
    }
}

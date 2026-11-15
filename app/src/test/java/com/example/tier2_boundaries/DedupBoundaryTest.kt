package com.example.tier2_boundaries

import com.example.data.database.entity.DedupEntity
import com.example.domain.parser.PaymentParser
import com.example.harness.MockPaymentInjectionHarness
import com.example.util.DedupEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tier 2: Boundary & Corner Cases for DedupEngine.
 * Covers exact millisecond collisions, sliding window time boundaries,
 * case sensitivity, null fallbacks, and window edge cases.
 */
class DedupBoundaryTest {

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
    fun testSlidingWindowExactBoundary() = runBlocking {
        val now = 100_000L
        val windowMs = 30_000L // 30s window

        // Record expires at 100_000L.
        // Active at 99_990L (10ms before expiry)
        val activeEntity = DedupEntity("100.00_active", 70_000L, 100.0, expiresAt = 100_000L)
        dedupDao.insertRecord(activeEntity)

        val validJustBefore = dedupDao.getValidRecord("100.00_active", 99_990L)
        assertNotNull("Record must be valid 10ms before expiry", validJustBefore)

        // Expired at 100_001L (1ms after expiry)
        val validJustAfter = dedupDao.getValidRecord("100.00_active", 100_001L)
        assertNull("Record must be invalid after expiry", validJustAfter)
    }

    @Test
    fun testExactMillisecondCollisionDifferentAmounts() = runBlocking {
        // Two payments received at the exact same millisecond
        val amount1 = 100.0
        val amount2 = 200.0

        val dup1 = dedupEngine.isDuplicate(amount1)
        val dup2 = dedupEngine.isDuplicate(amount2)

        assertFalse("₹100 should not be duplicate", dup1)
        assertFalse("₹200 should not be duplicate even if simultaneous", dup2)
    }

    @Test
    fun testPayerNameNormalizationInDedup() {
        val parsed1 = PaymentParser.parse("Rohit Sharma paid ₹ 500")
        val parsed2 = PaymentParser.parse("rohit sharma paid ₹ 500")
        val parsed3 = PaymentParser.parse("  Rohit   Sharma   paid ₹ 500")

        assertEquals("Rohit Sharma", parsed1.payerName)
        assertEquals("Rohit Sharma", parsed2.payerName)
        assertEquals("Rohit Sharma", parsed3.payerName)
        assertEquals("Normalized names should match", parsed1.payerName, parsed2.payerName)
        assertEquals("Normalized whitespace names should match", parsed1.payerName, parsed3.payerName)
    }

    @Test
    fun testNullRefIdAndNullPayerFallback() = runBlocking {
        // Amount-only payment key generation
        val key = dedupEngine.generateKey(45.0)
        assertTrue(key.startsWith("45.00_"))

        val isDup1 = dedupEngine.isDuplicate(45.0)
        assertFalse(isDup1)

        val isDup2 = dedupEngine.isDuplicate(45.0)
        assertTrue("Subsequent duplicate with null refId must still be detected", isDup2)
    }

    @Test
    fun testZeroDedupWindowEdgeCase() = runBlocking {
        // Configure dedup window to 0 seconds (user disables deduplication or boundary edge-case)
        settingsRepo.updateDirectly { it.copy(dedupWindowSeconds = 0) }

        // Must safely evaluate without crashing with java.lang.ArithmeticException: / by zero
        val isDup = try {
            dedupEngine.isDuplicate(100.0)
        } catch (e: ArithmeticException) {
            fail("DedupEngine.isDuplicate threw ArithmeticException: / by zero when dedupWindowSeconds = 0. Division-by-zero protection is missing!")
            return@runBlocking
        }

        assertFalse("Initial payment with dedupWindowSeconds=0 must not be flagged as duplicate", isDup)

        // Verify key generation also does not throw division by zero
        val key = try {
            dedupEngine.generateKey(100.0)
        } catch (e: ArithmeticException) {
            fail("DedupEngine.generateKey threw ArithmeticException: / by zero when dedupWindowSeconds = 0")
            return@runBlocking
        }
        assertNotNull("Generated key must not be null when dedupWindowSeconds=0", key)
    }

    @Test
    fun testBucketBoundaryLeakAcrossFixedTimeBuckets() = runBlocking {
        // Illustrates the boundary defect in fixed-bucket deduplication:
        // When dedupKey = "${amount}_$bucket", two payments separated by milliseconds
        // across the bucket boundary generate distinct keys.
        val windowMs = 30_000L
        val t1 = 29_999L // Bucket 0
        val t2 = 30_001L // Bucket 30000 (only 2ms after t1!)

        val bucket1 = (t1 / windowMs) * windowMs
        val bucket2 = (t2 / windowMs) * windowMs

        val key1 = "100.00_$bucket1"
        val key2 = "100.00_$bucket2"

        // In fixed bucket scheme, keys diverge even though t2 - t1 < windowMs
        assertNotEquals("Fixed buckets produce divergent keys across boundary", key1, key2)

        // Under Milestone 3 sliding window design (Feature 7), deduplication checks
        // composite key (amount + payer + refId) with expiresAt = now + windowMs,
        // eliminating the bucket boundary leak.
        dedupDao.insertRecord(DedupEntity(key1, t1, 100.0, expiresAt = t1 + windowMs))
        val recordAtT2 = dedupDao.getValidRecord(key1, t2)
        assertNotNull("Record inserted at t1 must still be valid at t2 (sliding window)", recordAtT2)
    }
}

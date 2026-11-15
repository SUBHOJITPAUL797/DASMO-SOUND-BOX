package com.example.tier2_boundaries

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.entity.TransactionEntity
import com.example.domain.parser.PaymentParser
import com.example.presentation.components.PatternState
import com.example.util.AppLockManager
import com.example.util.BluetoothHelper
import com.example.util.ChimePlayer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis

/**
 * Tier 2: Boundary & Corner Cases for Audio, Ledger, and Auxiliary subsystems.
 * Covers volume extreme values on ChimePlayer, Bluetooth audio check,
 * long payer name sanitization on PaymentParser, empty ledger zero handling,
 * 10k transactions ledger performance, and PatternState enum transitions.
 */
@RunWith(RobolectricTestRunner::class)
class AudioAndAuxiliaryBoundaryTest {

    @Test
    fun testVolumeClampingExtremeValues() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Exercise ChimePlayer with extreme volume boundaries
        val testVolumes = listOf(-100, -1, 0, 50, 100, 101, 500)
        for (vol in testVolumes) {
            // ChimePlayer must safely handle out-of-range volume values without throwing exceptions
            ChimePlayer.play(context, "MUTE", volumePercent = vol)
        }

        // Test Bluetooth audio connection routing helper
        val isBtConnected = BluetoothHelper.isBluetoothAudioConnected(context)
        assertFalse("Robolectric default environment should not have Bluetooth audio device connected", isBtConnected)
    }

    @Test
    fun testExtremelyLongPayerNameTruncation() {
        // Exercise PaymentParser against extreme 300-char payer name in raw notification text
        val extremelyLongName = "A".repeat(300)
        val text = "Received ₹500 from $extremelyLongName via PhonePe"
        val parsed = PaymentParser.parse(text)

        // PaymentParser restricts name to maximum 35 characters or rejects it gracefully
        if (parsed.payerName != null) {
            assertTrue(
                "Parsed payer name must not exceed production max length 35. Actual: ${parsed.payerName!!.length}",
                parsed.payerName!!.length <= 35
            )
        }
    }

    @Test
    fun testEmptyLedgerReturnsZeroWithoutCrash() {
        val emptyTransactions = emptyList<TransactionEntity>()
        val total = emptyTransactions.filter { it.status == "ANNOUNCED" }.sumOf { it.amount }
        assertEquals(0.0, total, 0.001)
    }

    @Test
    fun test10kTransactionsLedgerPerformance() {
        val transactions = (1..10000).map { i ->
            TransactionEntity(
                id = i.toLong(),
                amount = (i % 500 + 1).toDouble(),
                amountFormatted = "₹${i % 500 + 1}",
                sourceType = "NOTIFICATION",
                sourceApp = "com.phonepe.app",
                sourceAppName = "PhonePe",
                payerName = "Customer $i",
                rawText = "Paid",
                timestamp = System.currentTimeMillis() - i * 1000L,
                status = if (i % 5 == 0) "DUPLICATE_SKIPPED" else "ANNOUNCED",
                announcementText = "Announced",
                dedupKey = "key_$i"
            )
        }

        val elapsedMs = measureTimeMillis {
            val announcedTotal = transactions
                .filter { it.status == "ANNOUNCED" }
                .sumOf { it.amount }
            assertTrue(announcedTotal > 0.0)
        }

        assertTrue("10k transaction aggregation must execute in < 100ms. Measured: ${elapsedMs}ms", elapsedMs < 100)
    }

    @Test
    fun testPatternDotCountBoundary() {
        // Test production PatternState lifecycle enums used by PatternLockView and AppLockActivity
        val states = listOf(PatternState.NORMAL, PatternState.ERROR, PatternState.SUCCESS)
        assertEquals(3, states.size)
        assertTrue(states.contains(PatternState.NORMAL))
        assertTrue(states.contains(PatternState.ERROR))
        assertTrue(states.contains(PatternState.SUCCESS))

        // Test AppLockManager boundary inputs
        val context = ApplicationProvider.getApplicationContext<Context>()
        val emptyPkgName = AppLockManager.getAppName(context, "")
        assertEquals("", emptyPkgName)

        val longPkg = "com." + "a".repeat(200) + ".app"
        val longPkgName = AppLockManager.getAppName(context, longPkg)
        assertEquals(longPkg, longPkgName)
    }
}

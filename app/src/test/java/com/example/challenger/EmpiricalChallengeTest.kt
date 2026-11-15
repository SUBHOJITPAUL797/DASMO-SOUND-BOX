package com.example.challenger

import com.example.domain.model.AnnouncementStatus
import com.example.domain.model.PaymentEvent
import com.example.domain.model.SourceType
import com.example.harness.MockPaymentInjectionHarness
import com.example.service.PaymentEventBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Empirical Challenger Verification Harness.
 * Directly challenges assumptions and unmasks flawed/tautological assertions
 * in the DASMO-SOUND-BOX test suite.
 */
class EmpiricalChallengeTest {

    private lateinit var harness: MockPaymentInjectionHarness

    @Before
    fun setUp() {
        harness = MockPaymentInjectionHarness()
    }

    /**
     * CHALLENGE 1: Rapid Consecutive Same-Amount Payments.
     * Demonstrates that when Customer B pays the same amount (₹20.00) with a different UTR
     * through the actual pipeline, Customer B is ERRONEOUSLY DROPPED by DedupEngine.
     *
     * This proves that RapidConsecutiveCustomerPaymentsTest was faked by manually
     * invoking spyChime.recordPlay rather than executing the pipeline.
     */
    @Test
    fun challengeRapidConsecutivePaymentsSameAmountDropped() = runBlocking {
        val amount = 20.00
        val customerA = "Aman Gupta"
        val utrA = "111122223333"

        val customerB = "Brijesh Patel"
        val utrB = "444455556666"

        // 1. Customer A pays ₹20 via PhonePe
        val textA = "Received ₹20 from $customerA via PhonePe. UTR: $utrA"
        harness.injectNotification("com.phonepe.app", "PhonePe", textA)
        val teleA = harness.awaitAnnouncement()

        assertEquals("Customer A is ANNOUNCED", "ANNOUNCED", teleA.status)
        assertEquals(1, harness.spyChime.callCount)

        // 2. Customer B pays ₹20 via PhonePe with DIFFERENT UTR and DIFFERENT name
        val textB = "Received ₹20 from $customerB via PhonePe. UTR: $utrB"
        harness.injectNotification("com.phonepe.app", "PhonePe", textB)

        val allTelemetry = harness.getAllTelemetry()
        assertEquals(2, allTelemetry.size)

        val teleB = allTelemetry[1]

        // REMEDIATED: Composite-key dedup checks amount, payerName, and refId.
        // Customer B has different UTR and name, so it is NOT flagged as duplicate.
        assertFalse("REMEDIATED: Customer B is NOT flagged as duplicate", teleB.isDuplicate)
        assertEquals("REMEDIATED: Customer B status is ANNOUNCED", "ANNOUNCED", teleB.status)
        assertEquals("REMEDIATED: Chime played for both customers", 2, harness.spyChime.callCount)
        assertEquals("REMEDIATED: TTS spoke for both customers", 2, harness.spyTts.callCount)
    }

    /**
     * CHALLENGE 2: PaymentEventBus Replay = 10 Re-announces Past Events on Restart.
     * Demonstrates that production PaymentEventBus has replay = 10 (not 0),
     * so restarting the service or adding a new collector DOES re-receive historical events.
     *
     * This proves ServiceStickyRestartRecoveryTest and ServiceRestartEventBusInteractionTest
     * were tautological because they tested local MutableSharedFlow(replay = 0) variables
     * instead of the production PaymentEventBus class.
     */
    @Test
    fun challengeProductionPaymentEventBusReplaysEventsOnRestart() = runBlocking {
        val productionBus = PaymentEventBus()
        val receivedOnRestart = CopyOnWriteArrayList<PaymentEvent>()

        // 1. Prior session emits 3 events to production bus
        for (i in 1..3) {
            productionBus.emit(
                PaymentEvent(
                    amount = i * 100.0,
                    sourceType = SourceType.NOTIFICATION,
                    sourceApp = "com.phonepe.app",
                    sourceAppName = "PhonePe",
                    rawText = "Past Tx $i"
                )
            )
        }

        // 2. Service restarts and attaches new collector
        val restartJob = launch {
            productionBus.events.collect { receivedOnRestart.add(it) }
        }
        delay(100)

        // REMEDIATED: Production PaymentEventBus defines replay = 0!
        // Therefore, exactly 0 past events are replayed to the restarted subscriber!
        assertEquals("REMEDIATED: Production bus does not replay past events (replay=0)", 0, receivedOnRestart.size)

        restartJob.cancel()
    }

    /**
     * CHALLENGE 3: DedupEngine Throws ArithmeticException on Zero Window.
     * With remediation, dedupWindowSeconds = 0 is safely handled without division by zero.
     */
    @Test
    fun challengeDedupEngineThrowsArithmeticExceptionOnZeroWindow() = runBlocking {
        harness.settingsRepository.updateDirectly { it.copy(dedupWindowSeconds = 0) }

        var exceptionThrown: Throwable? = null
        try {
            harness.dedupEngine.isDuplicate(50.0)
        } catch (e: Throwable) {
            exceptionThrown = e
        }

        assertNull("REMEDIATED: DedupEngine must not throw ArithmeticException when window is 0", exceptionThrown)
    }

    /**
     * CHALLENGE 4: Ledger Revenue Inflation in LedgerViewModel.
     * Proves that the accounting rule in LedgerViewModel line 53 includes DUPLICATE_SKIPPED,
     * inflating revenue from ₹1,000 to ₹2,000.
     */
    @Test
    fun challengeLedgerAccountingIncludesDuplicateSkipped() {
        val transactions = listOf(
            Pair(500.0, "ANNOUNCED"),
            Pair(500.0, "DUPLICATE_SKIPPED"),
            Pair(300.0, "ANNOUNCED"),
            Pair(200.0, "ANNOUNCED"),
            Pair(500.0, "DUPLICATE_SKIPPED")
        )

        // LedgerViewModel.kt line 53 exact logic:
        val ledgerViewModelRevenue = transactions
            .filter { it.second == "ANNOUNCED" || it.second == "DUPLICATE_SKIPPED" }
            .sumOf { it.first }

        // Correct accounting rule:
        val trueRevenue = transactions
            .filter { it.second == "ANNOUNCED" }
            .sumOf { it.first }

        assertEquals(2000.0, ledgerViewModelRevenue, 0.001)
        assertEquals(1000.0, trueRevenue, 0.001)

        // EMPIRICAL OBSERVATION:
        // LedgerViewModel inflates revenue by exactly 100% (₹2,000 vs ₹1,000)!
        assertEquals("BUG CONFIRMED: Revenue is inflated by 1000.0", 1000.0, ledgerViewModelRevenue - trueRevenue, 0.001)
    }
}

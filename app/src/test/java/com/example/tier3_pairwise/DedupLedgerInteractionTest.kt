package com.example.tier3_pairwise

import com.example.di.AppModule
import com.example.domain.model.AnnouncementStatus
import com.example.domain.model.PaymentEvent
import com.example.domain.model.SourceType
import com.example.domain.repository.CashbookRepository
import com.example.harness.InMemoryCashbookDao
import com.example.harness.MockPaymentInjectionHarness
import com.example.presentation.screens.ledger.LedgerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tier 3: Cross-Feature Combinations — DedupEngine + Ledger Accounting.
 * Directly exercises LedgerViewModel with AppModule injection.
 * Verifies that payments marked DUPLICATE_SKIPPED are properly excluded from
 * the ledger revenue balance, preventing accounting inflation.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DedupLedgerInteractionTest {

    private lateinit var harness: MockPaymentInjectionHarness

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        harness = MockPaymentInjectionHarness()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDuplicatesExcludedFromLedgerOnlineBalance() = runBlocking {
        // Customer 1 pays ₹500
        val event1 = PaymentEvent(
            amount = 500.0,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "com.phonepe.app",
            sourceAppName = "PhonePe",
            payerName = "Ramesh",
            rawText = "Received ₹500"
        )
        harness.executePipeline(event1, System.nanoTime(), System.nanoTime(), "Ramesh", null)

        // Customer 1's bank resends duplicate notification for ₹500
        val event1Dup = PaymentEvent(
            amount = 500.0,
            sourceType = SourceType.SMS,
            sourceApp = "SBI",
            sourceAppName = "SBI Bank SMS",
            payerName = "Ramesh",
            rawText = "Credited ₹500"
        )
        harness.executePipeline(event1Dup, System.nanoTime(), System.nanoTime(), "Ramesh", null)

        // Customer 2 pays ₹300
        val event2 = PaymentEvent(
            amount = 300.0,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "net.one97.paytm",
            sourceAppName = "Paytm",
            payerName = "Pooja",
            rawText = "Received ₹300"
        )
        harness.executePipeline(event2, System.nanoTime(), System.nanoTime(), "Pooja", null)

        // Customer 3 pays ₹200
        val event3 = PaymentEvent(
            amount = 200.0,
            sourceType = SourceType.NOTIFICATION,
            sourceApp = "com.google.android.apps.nbu.paisa.user",
            sourceAppName = "Google Pay",
            payerName = "Vijay",
            rawText = "Received ₹200"
        )
        harness.executePipeline(event3, System.nanoTime(), System.nanoTime(), "Vijay", null)

        // Customer 1's duplicate arrives yet again
        harness.executePipeline(event1Dup, System.nanoTime(), System.nanoTime(), "Ramesh", null)

        val allTxs = harness.transactionRepository.getAll()
        assertEquals("Total records in database should be 5", 5, allTxs.size)

        val announcedCount = allTxs.count { it.status == AnnouncementStatus.ANNOUNCED.name }
        val skippedCount = allTxs.count { it.status == AnnouncementStatus.DUPLICATE_SKIPPED.name }
        assertEquals(3, announcedCount)
        assertEquals(2, skippedCount)

        // Connect harness repository to AppModule
        AppModule.transactionRepository = harness.transactionRepository
        AppModule.cashbookRepository = CashbookRepository(InMemoryCashbookDao())

        val ledgerViewModel = LedgerViewModel()
        val collectJob = launch(Dispatchers.Unconfined) { ledgerViewModel.uiState.collect() }
        yield()

        val state = ledgerViewModel.uiState.value
        assertEquals("Ledger online balance must be 500 + 300 + 200 = ₹1,000", 1000.0, state.onlineBalance, 0.001)
        assertNotEquals("Ledger online balance must not be inflated to ₹2,000", 2000.0, state.onlineBalance)
        collectJob.cancel()
    }
}

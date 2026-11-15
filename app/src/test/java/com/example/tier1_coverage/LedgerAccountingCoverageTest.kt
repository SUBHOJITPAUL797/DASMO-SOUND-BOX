package com.example.tier1_coverage

import com.example.data.database.entity.CustomerBorrowEntity
import com.example.data.database.entity.TransactionEntity
import com.example.data.database.entity.WalletTransactionEntity
import com.example.di.AppModule
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
 * Tier 1: Feature Coverage for Ledger Accounting logic.
 * Directly exercises LedgerViewModel, CashbookRepository, and TransactionRepository.
 * Validates revenue aggregation, cashbook drawer operations, customer borrows,
 * and duplicate exclusion.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerAccountingCoverageTest {

    private lateinit var txRepo: MockPaymentInjectionHarness.InMemoryTransactionRepository
    private lateinit var cashbookDao: InMemoryCashbookDao
    private lateinit var cashbookRepo: CashbookRepository
    private lateinit var viewModel: LedgerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        txRepo = MockPaymentInjectionHarness.InMemoryTransactionRepository()
        cashbookDao = InMemoryCashbookDao()
        cashbookRepo = CashbookRepository(cashbookDao)

        AppModule.transactionRepository = txRepo
        AppModule.cashbookRepository = cashbookRepo

        viewModel = LedgerViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDuplicateSkippedExcludedFromRevenue() = runBlocking {
        // Active collection to start WhileSubscribed flow
        val collectJob = launch(Dispatchers.Unconfined) {
            viewModel.uiState.collect()
        }

        // Insert announced online transaction ₹500
        txRepo.insertTransaction(
            TransactionEntity(
                id = 1,
                amount = 500.0,
                amountFormatted = "₹500.00",
                sourceType = "NOTIFICATION",
                sourceApp = "com.phonepe.app",
                sourceAppName = "PhonePe",
                payerName = "Ramesh",
                rawText = "Received ₹500",
                timestamp = 1000L,
                status = "ANNOUNCED",
                announcementText = "500 received",
                dedupKey = "500.00_1000"
            )
        )

        // Insert duplicate transaction ₹500 marked DUPLICATE_SKIPPED
        txRepo.insertTransaction(
            TransactionEntity(
                id = 2,
                amount = 500.0,
                amountFormatted = "₹500.00",
                sourceType = "SMS",
                sourceApp = "SBI",
                sourceAppName = "SBI Bank SMS",
                payerName = "Ramesh",
                rawText = "Credited ₹500",
                timestamp = 1200L,
                status = "DUPLICATE_SKIPPED",
                announcementText = null,
                dedupKey = "500.00_1000"
            )
        )

        // Insert second announced online transaction ₹250
        txRepo.insertTransaction(
            TransactionEntity(
                id = 3,
                amount = 250.0,
                amountFormatted = "₹250.00",
                sourceType = "NOTIFICATION",
                sourceApp = "net.one97.paytm",
                sourceAppName = "Paytm",
                payerName = "Suresh",
                rawText = "Received ₹250",
                timestamp = 2000L,
                status = "ANNOUNCED",
                announcementText = "250 received",
                dedupKey = "250.00_2000"
            )
        )

        yield()

        // Assert directly on LedgerViewModel.uiState
        val state = viewModel.uiState.value
        assertEquals("True online balance must be 500 + 250 = ₹750.00", 750.00, state.onlineBalance, 0.001)
        assertNotEquals("DUPLICATE_SKIPPED must NOT inflate balance to ₹1250.00", 1250.00, state.onlineBalance)

        collectJob.cancel()
    }

    @Test
    fun testManualIncomeAndExpenseCalculations() = runBlocking {
        val collectJob = launch(Dispatchers.Unconfined) { viewModel.uiState.collect() }

        // Test real LedgerViewModel methods
        viewModel.addManualTransaction("INCOME", 1000.0, "DRAWER", "Opening cash")
        viewModel.addManualTransaction("EXPENSE", 150.0, "DRAWER", "Tea & Snacks")
        viewModel.addManualTransaction("INCOME", 500.0, "DRAWER", "Cash sale")

        yield()

        val state = viewModel.uiState.value
        assertEquals("Drawer balance should be 1000 - 150 + 500 = ₹1350.00", 1350.0, state.drawerBalance, 0.001)
        assertEquals(3, state.manualTransactions.size)

        // Direct entity verification
        val manualTxs = listOf(
            WalletTransactionEntity(id = 1, walletType = "DRAWER", amount = 1000.0, category = "INCOME", note = "Opening cash", timestamp = 100L),
            WalletTransactionEntity(id = 2, walletType = "DRAWER", amount = -150.0, category = "EXPENSE", note = "Tea & Snacks", timestamp = 200L),
            WalletTransactionEntity(id = 3, walletType = "DRAWER", amount = 500.0, category = "INCOME", note = "Cash sale", timestamp = 300L)
        )
        val drawerTotal = manualTxs.filter { it.walletType == "DRAWER" }.sumOf { it.amount }
        assertEquals("Drawer balance should be 1000 - 150 + 500 = 1350", 1350.0, drawerTotal, 0.001)

        collectJob.cancel()
    }

    @Test
    fun testCustomerBorrowDecrementsDrawerBalance() = runBlocking {
        val collectJob = launch(Dispatchers.Unconfined) { viewModel.uiState.collect() }

        // Start with ₹1000 in drawer
        viewModel.addManualTransaction("INCOME", 1000.0, "DRAWER", "Initial Cash")
        yield()
        assertEquals(1000.0, viewModel.uiState.value.drawerBalance, 0.001)

        // Give borrow of ₹300
        viewModel.addCustomerBorrow("Mahesh Grocery", 300.0, "Stock purchase", 5000L)
        yield()

        val borrowState = viewModel.uiState.value
        assertEquals("Drawer balance decrements to 700", 700.0, borrowState.drawerBalance, 0.001)
        assertEquals("Pending borrow is 300", 300.0, borrowState.totalPendingBorrows, 0.001)
        assertEquals(1, borrowState.customerBorrows.size)

        // Verify entity schema compliance
        val borrow = CustomerBorrowEntity(
            id = 1,
            customerName = "Mahesh Grocery",
            amount = 300.0,
            purpose = "Stock purchase",
            expectedReturnDate = 5000L,
            timestamp = 100L,
            isCleared = false
        )
        val walletDebit = WalletTransactionEntity(
            id = 1,
            walletType = "DRAWER",
            amount = -borrow.amount,
            category = "BORROW_GIVEN",
            note = "Given to ${borrow.customerName}",
            timestamp = 100L
        )
        assertEquals(-300.0, walletDebit.amount, 0.001)
        assertEquals("DRAWER", walletDebit.walletType)
        assertFalse(borrow.isCleared)

        collectJob.cancel()
    }

    @Test
    fun testMarkBorrowClearedRestoresBalance() = runBlocking {
        val collectJob = launch(Dispatchers.Unconfined) { viewModel.uiState.collect() }

        viewModel.addManualTransaction("INCOME", 1000.0, "DRAWER", "Initial Cash")
        viewModel.addCustomerBorrow("Mahesh Grocery", 300.0, "Stock purchase", 5000L)
        yield()

        val borrowState = viewModel.uiState.value
        assertEquals(1, borrowState.customerBorrows.size)
        val borrowEntity = borrowState.customerBorrows[0]

        // Clear borrow
        viewModel.markBorrowCleared(borrowEntity)
        yield()

        val clearedState = viewModel.uiState.value
        assertEquals("Drawer balance restored to 1000", 1000.0, clearedState.drawerBalance, 0.001)
        assertEquals("Pending borrow cleared to 0", 0.0, clearedState.totalPendingBorrows, 0.001)

        // Verify repayment entity schema
        val repayment = WalletTransactionEntity(
            id = 2,
            walletType = "DRAWER",
            amount = borrowEntity.amount,
            category = "BORROW_REPAID",
            note = "Repaid by ${borrowEntity.customerName}",
            timestamp = 200L
        )
        assertEquals(300.0, repayment.amount, 0.001)
        assertEquals("DRAWER", repayment.walletType)

        collectJob.cancel()
    }

    @Test
    fun testEmptyLedgerInitialState() = runBlocking {
        val collectJob = launch(Dispatchers.Unconfined) { viewModel.uiState.collect() }
        yield()

        val state = viewModel.uiState.value
        assertEquals(0.0, state.onlineBalance, 0.001)
        assertEquals(0.0, state.drawerBalance, 0.001)
        assertEquals(0.0, state.homeBalance, 0.001)
        assertEquals(0.0, state.totalPendingBorrows, 0.001)
        assertTrue(state.manualTransactions.isEmpty())
        assertTrue(state.customerBorrows.isEmpty())

        collectJob.cancel()
    }
}

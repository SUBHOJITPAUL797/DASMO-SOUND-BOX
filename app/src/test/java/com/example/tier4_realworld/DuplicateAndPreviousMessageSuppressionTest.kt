package com.example.tier4_realworld

import android.os.Bundle
import com.example.harness.MockPaymentInjectionHarness
import com.example.domain.parser.PaymentParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DuplicateAndPreviousMessageSuppressionTest {

    private lateinit var harness: MockPaymentInjectionHarness

    @Before
    fun setUp() {
        harness = MockPaymentInjectionHarness()
    }

    @Test
    fun testCrossChannelNotificationAndBankSmsDeduplication() = runBlocking {
        val dedup = harness.dedupEngine
        val amount = 500.0

        // 1. UPI App notification arrives (has payer name, no ref ID)
        val isDupNotification = dedup.isDuplicate(amount = amount, payerName = "Rohit Sharma", refId = null)
        assertFalse("First notification from UPI app must not be duplicate", isDupNotification)

        // 2. Bank SMS arrives 2 seconds later (has ref ID, no payer name)
        val isDupSms = dedup.isDuplicate(amount = amount, payerName = null, refId = "987654321012")
        assertTrue("Bank SMS confirmation of the same payment must be suppressed as duplicate", isDupSms)
    }

    @Test
    fun testDifferentCustomersPayingSameAmountWithinWindowAreBothAnnounced() = runBlocking {
        val dedup = harness.dedupEngine
        val amount = 100.0

        // Customer A pays ₹100
        val isDupA = dedup.isDuplicate(amount = amount, payerName = "Rohit Sharma", refId = "UTR001")
        assertFalse("Customer A payment must be announced", isDupA)

        // Customer B pays ₹100 within 5 seconds
        val isDupB = dedup.isDuplicate(amount = amount, payerName = "Pooja Verma", refId = "UTR002")
        assertFalse("Customer B with different name/UTR paying same amount must also be announced", isDupB)
    }

    @Test
    fun testSameCustomerDuplicateRetryWithinWindowIsSuppressed() = runBlocking {
        val dedup = harness.dedupEngine
        val amount = 250.0

        val isDup1 = dedup.isDuplicate(amount = amount, payerName = "Aman Gupta", refId = "REF999")
        assertFalse("Initial payment must be announced", isDup1)

        val isDup2 = dedup.isDuplicate(amount = amount, payerName = "Aman Gupta", refId = "REF999")
        assertTrue("Identical payment retry from same customer must be suppressed", isDup2)
    }

    @Test
    fun testLatestIncomingMessageParsedAndPreviousMessageNotReannounced() {
        // Simulating Google Pay / MessagingStyle scenario:
        // Message 1 (old): "₹100.00"
        // Message 2 (new): "₹50.00"
        val oldMessage = "₹100.00"
        val newMessage = "₹50.00"

        val oldResult = PaymentParser.parse(oldMessage, fallbackTitle = "Ramesh", packageName = "com.google.android.apps.nbu.paisa.user")
        assertEquals(100.0, oldResult.amount)

        val newResult = PaymentParser.parse(newMessage, fallbackTitle = "Ramesh", packageName = "com.google.android.apps.nbu.paisa.user")
        assertEquals(50.0, newResult.amount)

        // Non-payment conversational message in chat history (e.g. "Thanks")
        val chatReply = "Thanks for the payment!"
        val chatResult = PaymentParser.parse(chatReply, fallbackTitle = "Ramesh", packageName = "com.google.android.apps.nbu.paisa.user")
        assertNull("Chat reply must not parse as payment and must not trigger old payment announcement", chatResult.amount)
        assertFalse("Chat reply is not a credit", chatResult.isCredit)
    }
}

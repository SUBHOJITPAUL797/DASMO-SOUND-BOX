package com.example.tier1_coverage

import com.example.domain.parser.PaymentParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Tier 1: Feature Coverage for PaymentParser.
 * Validates extraction of amount, payer name, refId (UTR/RRN), source app,
 * Hindi text handling, and payer sanitization.
 */
class PaymentParserCoverageTest {

    @Test
    fun testPhonePeCreditParsing() {
        val text = "Received ₹500 from Rohit Sharma via PhonePe"
        val result = PaymentParser.parse(text = text, fallbackTitle = "PhonePe", packageName = "com.phonepe.app")
        assertTrue("Expected credit transaction", result.isCredit)
        assertEquals(500.0, result.amount!!, 0.001)
        assertEquals("Rohit Sharma", result.payerName)
    }

    @Test
    fun testGooglePayMessagingStyleParsing() {
        val text = "₹ 1,250.50 received"
        val result = PaymentParser.parse(
            text = text,
            fallbackTitle = "Suresh Patel",
            packageName = "com.google.android.apps.nbu.paisa.user"
        )
        assertTrue("Expected credit transaction", result.isCredit)
        assertEquals(1250.50, result.amount!!, 0.001)
        assertEquals("Suresh Patel", result.payerName)
    }

    @Test
    fun testPaytmQrCreditParsing() {
        val text = "Payment of Rs 350.00 received on Paytm QR from Pooja Jain"
        val result = PaymentParser.parse(text = text, fallbackTitle = "Paytm", packageName = "net.one97.paytm")
        assertTrue("Expected credit transaction", result.isCredit)
        assertEquals(350.00, result.amount!!, 0.001)
        assertEquals("Pooja Jain", result.payerName)
    }

    @Test
    fun testBankSmsWithUtrParsing() {
        val sms = "Dear SBI User, your A/c ending 4321 credited by Rs 2,500.00 on 26-Sep-26 by transfer from Rahul Verma Ref No 987654321012 - SBI"
        val result = PaymentParser.parse(sms)
        assertTrue("Expected credit transaction", result.isCredit)
        assertEquals(2500.00, result.amount!!, 0.001)
        assertEquals("Rahul Verma", result.payerName)
        assertEquals("987654321012", result.refId)
    }

    @Test
    fun testHindiNotificationParsing() {
        val hindiText = "रोहित शर्मा से ₹ 500 प्राप्त हुए"
        val result = PaymentParser.parse(hindiText)
        assertTrue("Expected credit transaction", result.isCredit)
        assertEquals(500.00, result.amount!!, 0.001)
        assertEquals("रोहित शर्मा", result.payerName)
    }

    @Test
    fun testHindiNameFirstPatternParsing() {
        val hindiText = "अमित कुमार ने ₹ 1,000 भेजे"
        val result = PaymentParser.parse(hindiText)
        assertTrue("Expected credit transaction", result.isCredit)
        assertEquals(1000.00, result.amount!!, 0.001)
        assertEquals("अमित कुमार", result.payerName)
    }

    @Test
    fun testPayerNameSanitization() {
        val text = "Payment received of ₹200 from VIKRAM SINGH via UPI"
        val result = PaymentParser.parse(text)
        assertTrue(result.isCredit)
        assertEquals(200.00, result.amount!!, 0.001)
        assertEquals("Vikram Singh", result.payerName)
    }

    @Test
    fun testDebitNotificationRejection() {
        val debitText = "Debited Rs 400.00 from your A/c ending 1234 on 14-Nov-26"
        val result = PaymentParser.parse(debitText)
        assertFalse("Debit should not be marked as credit", result.isCredit)
        assertNull("Amount must be null for debits", result.amount)
    }

    @Test
    fun testCollectRequestRejection() {
        val requestText = "Collect request: Ramesh has requested Rs 150 from your UPI ID"
        val result = PaymentParser.parse(requestText)
        assertFalse("Collect request should not be treated as payment received", result.isCredit)
        assertNull("Amount must be null for payment requests", result.amount)
    }
}

package com.example.tier2_boundaries

import com.example.domain.parser.PaymentParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Tier 2: Boundary & Corner Cases for PaymentParser.
 * Covers non-breaking spaces (\u00A0, \u202F, \u200B), extreme amounts,
 * credit card debits, collect requests, and malformed/emoji text.
 */
class ParserBoundaryTest {

    @Test
    fun testNonBreakingSpacesInAmount() {
        // Non-breaking space \u00A0, narrow no-break space \u202F, zero-width space \u200B
        val textWithNbsp = "Received\u00A0₹\u202F500.00\u200B from\u00A0Rohit Sharma via PhonePe"
        val result = PaymentParser.parse(textWithNbsp)
        assertTrue("Must detect credit despite non-breaking spaces", result.isCredit)
        assertEquals(500.00, result.amount!!, 0.001)
        assertEquals("Rohit Sharma", result.payerName)
    }

    @Test
    fun testExtremeLargeAmount() {
        val largeAmountText = "Received ₹99,99,999.00 from Mukesh Ambani via PhonePe"
        val result = PaymentParser.parse(largeAmountText)
        assertTrue(result.isCredit)
        assertEquals(9999999.00, result.amount!!, 0.001)
        assertEquals("Mukesh Ambani", result.payerName)
    }

    @Test
    fun testFractionalSmallAmount() {
        val smallAmountText = "Received ₹0.50 from Priya via PhonePe"
        val result = PaymentParser.parse(smallAmountText)
        assertTrue(result.isCredit)
        assertEquals(0.50, result.amount!!, 0.001)
        assertEquals("Priya", result.payerName)
    }

    @Test
    fun testCreditCardDebitExclusion() {
        // "Credit card" mention must not cause credit false positive when it's a debit
        val ccDebitText = "Your SBI Credit Card ending 5678 was debited for INR 1,500.00 at AMAZON"
        val result = PaymentParser.parse(ccDebitText)
        assertFalse("Credit card debit must be rejected", result.isCredit)
        assertNull("Amount must be null for CC debit", result.amount)
    }

    @Test
    fun testCollectRequestKeywordsExclusion() {
        val collectRequestText = "Collect request: Rahul Sharma has requested you ₹ 500 on Google Pay"
        val result = PaymentParser.parse(collectRequestText)
        assertFalse("Collect request must be rejected", result.isCredit)
        assertNull(result.amount)
    }

    @Test
    fun testTruncatedAndEmojiOnlyText() {
        val emojiOnly = "💸💰🎉 ₹ 👍"
        val resultEmoji = PaymentParser.parse(emojiOnly)
        assertFalse(resultEmoji.isCredit)
        assertNull(resultEmoji.amount)

        val singleSymbol = "₹"
        val resultSymbol = PaymentParser.parse(singleSymbol)
        assertFalse(resultSymbol.isCredit)
        assertNull(resultSymbol.amount)

        val emptyText = ""
        val resultEmpty = PaymentParser.parse(emptyText)
        assertFalse(resultEmpty.isCredit)
        assertNull(resultEmpty.amount)
    }
}

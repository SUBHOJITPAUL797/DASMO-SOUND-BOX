package com.example

import com.example.domain.parser.PaymentParser
import com.example.util.KnownPaymentApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionHardeningTest {

    @Test
    fun whatsappLoanOfferWith3500_isRejected() {
        val samples = listOf(
            "Instant personal loan offer of ₹3500 approved for you! Apply now",
            "Congratulations! Pre-approved loan ₹ 3,500 ready in 2 minutes",
            "WhatsApp Loan Offer: Get Rs 3500 instantly at low interest rate",
            "You are eligible for ₹3500 credit limit. Claim now!",
            "ऑफर! आपको ₹3500 का लोन मिल सकता है, अभी अप्लाई करें"
        )
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            for (msg in samples) {
                assertFalse("Listener gate must ignore WhatsApp loan spam: $msg", KnownPaymentApps.shouldProcessNotification(pkg, msg))
                val parsed = PaymentParser.parse(msg, fallbackTitle = "Loan Agent", packageName = pkg)
                assertFalse("Parser must reject WhatsApp loan spam: $msg", parsed.isCredit)
                assertNull("Parser amount must be null for loan spam: $msg", parsed.amount)
            }
        }
    }

    @Test
    fun paymentAppDepositPromosAndAlerts_areRejected() {
        val depositPromos = listOf(
            "Deposit ₹3,500 to get flat ₹100 cashback",
            "Make a deposit of Rs 3500 in your wallet today",
            "Fixed deposit of ₹50,000 booked successfully",
            "Recurring deposit ₹3,500 reminder: pay before 5th",
            "Deposit ₹3500 now and unlock rewards",
            "Deposit alert: Add money to your wallet ₹3500"
        )
        val paymentApps = listOf(
            "com.phonepe.app",
            "net.one97.paytm",
            "com.google.android.apps.nbu.paisa.user",
            "in.org.npci.upiapp"
        )
        for (pkg in paymentApps) {
            for (msg in depositPromos) {
                val parsed = PaymentParser.parse(msg, fallbackTitle = "Payment App", packageName = pkg)
                assertFalse("Deposit promo must not announce as received payment ($pkg): $msg", parsed.isCredit)
                assertNull("Amount must be null for deposit promo: $msg", parsed.amount)
            }
        }
    }

    @Test
    fun casualWhatsappMessagesWithAmounts_areRejected() {
        val casualChats = listOf(
            "Bhai 3500 send kar do",
            "Send ₹3500 to my number",
            "Need ₹3500 urgently",
            "Bill split is Rs 3500 each",
            "Let's pay 3500 tomorrow"
        )
        for (msg in casualChats) {
            assertFalse("Casual WhatsApp chat must not pass notification gate: $msg", KnownPaymentApps.shouldProcessNotification("com.whatsapp", msg))
            val parsed = PaymentParser.parse(msg, fallbackTitle = "Rohan", packageName = "com.whatsapp")
            assertFalse("Casual WhatsApp chat must not parse as credit: $msg", parsed.isCredit)
            assertNull(parsed.amount)
        }
    }

    @Test
    fun scratchCardCreditLimitAndBillDue_areRejected() {
        val spamAndNonCredits = listOf(
            "You won a scratch card worth up to ₹3500 on Paytm!",
            "Increase your credit score & unlock ₹3500 voucher",
            "Your Credit Card bill of ₹3,500.00 is generated. Due date 15-Oct.",
            "Autopay mandate of ₹3500 scheduled for tomorrow",
            "OTP is 458912 for transaction of ₹3500. Do not share OTP.",
            "Payment of ₹3500 is pending confirmation",
            "Your transaction of ₹3500 failed. Any amount debited will be reversed.",
            "Available balance in your A/c XX1234 is Rs 3500.00"
        )
        for (msg in spamAndNonCredits) {
            val parsed = PaymentParser.parse(msg, packageName = "com.phonepe.app")
            assertFalse("Non-credit notification must be rejected: $msg", parsed.isCredit)
            assertNull("Amount must be null for non-credit notification: $msg", parsed.amount)
        }
    }

    @Test
    fun nonPaymentBrowsersSocialAndLoanPackages_areBlockedByGate() {
        val spamPackages = listOf(
            "com.android.chrome",
            "com.instagram.android",
            "com.kreditbee.android",
            "com.zomato.delivery",
            "in.swiggy.android"
        )
        for (pkg in spamPackages) {
            assertFalse(
                "Spam/non-payment package $pkg must be blocked even with rupee text",
                KnownPaymentApps.shouldProcessNotification(pkg, "Received ₹3500 loan offer")
            )
        }
    }

    @Test
    fun genuineWhatsappPayAndBankDepositCredits_areStillDetectedAccurately() {
        val whatsappPay = "Rahul Sharma sent you ₹500"
        assertTrue(KnownPaymentApps.shouldProcessNotification("com.whatsapp", whatsappPay))
        val waResult = PaymentParser.parse(whatsappPay, fallbackTitle = "Rahul Sharma", packageName = "com.whatsapp")
        assertTrue(waResult.isCredit)
        assertEquals(500.0, waResult.amount!!, 0.01)
        assertEquals("Rahul Sharma", waResult.payerName)

        val bankDeposited = "Dear Customer, Rs. 3,500.00 deposited in your A/c XX4321 by UPI/CR/123456789012/Suresh Patel"
        val bankResult = PaymentParser.parse(bankDeposited)
        assertTrue("Genuine bank credit with 'deposited in your' must still succeed", bankResult.isCredit)
        assertEquals(3500.0, bankResult.amount!!, 0.01)
        assertEquals("123456789012", bankResult.refId)
    }
}

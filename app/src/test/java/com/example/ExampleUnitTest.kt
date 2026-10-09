package com.example

import com.example.domain.parser.IndianNumberFormatter
import com.example.domain.parser.PaymentParser
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testPhonePePaymentDetection() {
        val text = "Received ₹500 from Rohit Sharma via PhonePe"
        val result = PaymentParser.parse(text)
        assertTrue(result.isCredit)
        assertEquals(500.0, result.amount!!, 0.01)
        assertEquals("Rohit Sharma", result.payerName)
    }

    @Test
    fun testGooglePayPaymentDetection() {
        val text = "₹1,250 received"
        val result = PaymentParser.parse(text, fallbackTitle = "Suresh Patel")
        assertTrue(result.isCredit)
        assertEquals(1250.0, result.amount!!, 0.01)
        assertEquals("Suresh Patel", result.payerName)
    }

    @Test
    fun testPaytmQrPaymentDetection() {
        val text = "Payment of Rs 350.00 received on Paytm QR from Pooja Jain"
        val result = PaymentParser.parse(text)
        assertTrue(result.isCredit)
        assertEquals(350.0, result.amount!!, 0.01)
        assertEquals("Pooja Jain", result.payerName)
    }

    @Test
    fun testSbiBankSmsDetection() {
        val text = "Dear SBI User, your A/c ending 4321 credited by Rs 2,500.00 on 26-Sep-26 by transfer from Rahul Verma Ref No 987654321012 - SBI"
        val result = PaymentParser.parse(text)
        assertTrue(result.isCredit)
        assertEquals(2500.0, result.amount!!, 0.01)
        assertEquals("Rahul Verma", result.payerName)
        assertEquals("987654321012", result.refId)
    }

    @Test
    fun testHdfcBankSmsDetection() {
        val text = "Update! INR 800.00 credited to HDFC Bank A/c xx1234 on 26-SEP-26 by transfer from Amit Roy (UPI Ref No 123456789012)"
        val result = PaymentParser.parse(text)
        assertTrue(result.isCredit)
        assertEquals(800.0, result.amount!!, 0.01)
        assertEquals("Amit Roy", result.payerName)
        assertEquals("123456789012", result.refId)
    }

    @Test
    fun testIciciBankSmsDetection() {
        val text = "Dear Customer, your ICICI Bank Account ending in 789 has been credited with INR 1000.00 on 26-Sep-26. Info: UPI/123456789012/Pooja Jain/Paytm."
        val result = PaymentParser.parse(text)
        assertTrue(result.isCredit)
        assertEquals(1000.0, result.amount!!, 0.01)
        assertEquals("Pooja Jain", result.payerName)
        assertEquals("123456789012", result.refId)
    }

    @Test
    fun testDebitRejection() {
        val text = "Rs 500 debited from your A/c ending 1234 on 26-Sep-26 to merchant Tea Stall. Avl Bal Rs 5000."
        val result = PaymentParser.parse(text)
        assertFalse(result.isCredit)
        assertNull(result.amount)
    }

    @Test
    fun testCreditCardDebitRejection() {
        val text = "Your SBI Credit Card ending 5678 is debited for Rs 1,200.00 at Amazon on 26-Sep-26. Avl Limit Rs 45000."
        val result = PaymentParser.parse(text)
        assertFalse(result.isCredit)
        assertNull(result.amount)
    }

    @Test
    fun testIndianNumberFormatter() {
        val formattedEn = IndianNumberFormatter.format(1500.0, "en-IN")
        assertEquals("one thousand five hundred", formattedEn)

        val formattedHi = IndianNumberFormatter.format(500.0, "hi-IN")
        assertEquals("500", formattedHi)
    }

    @Test
    fun testQrUrlGeneration() {
        val url = com.example.util.QrCodeGenerator.generateUpiUrl("merchant@upi", "Dasmo Store", 250.0)
        assertTrue(url.contains("pa=merchant@upi"))
        assertTrue(url.contains("pn=Dasmo%20Store"))
        assertTrue(url.contains("am=250.00"))
        assertTrue(url.contains("cu=INR"))
    }

    @Test
    fun testAppUpdateManagerVersionTagCleaning() {
        val manager = com.example.util.update.AppUpdateManager.getInstance()
        assertEquals("1.0.1", manager.cleanVersionTag("v1.0.1"))
        assertEquals("1.0.1", manager.cleanVersionTag("V1.0.1"))
        assertEquals("2.0.0", manager.cleanVersionTag("2.0.0"))
        assertEquals("1.2", manager.cleanVersionTag(" v1.2 "))
    }

    @Test
    fun testAppUpdateManagerVersionComparison() {
        val manager = com.example.util.update.AppUpdateManager.getInstance()
        // Newer semantic version
        assertTrue(manager.isNewerVersion(currentVer = "1.0", currentCode = 1, latestVer = "1.0.1", latestCode = 2))
        assertTrue(manager.isNewerVersion(currentVer = "1.0.0", currentCode = 1, latestVer = "1.1.0", latestCode = 1))
        assertTrue(manager.isNewerVersion(currentVer = "1.0", currentCode = 1, latestVer = "2.0", latestCode = 5))

        // Same version
        assertFalse(manager.isNewerVersion(currentVer = "1.0", currentCode = 1, latestVer = "1.0", latestCode = 1))
        assertFalse(manager.isNewerVersion(currentVer = "1.0.1", currentCode = 2, latestVer = "1.0.1", latestCode = 2))

        // Older version
        assertFalse(manager.isNewerVersion(currentVer = "1.1.0", currentCode = 3, latestVer = "1.0.5", latestCode = 2))
    }

    @Test
    fun testAppUpdateManagerExtractVersionCode() {
        val manager = com.example.util.update.AppUpdateManager.getInstance()
        val markdownBody = """
            ## Release v1.0.1
            - versionCode: 2
            - Fix notification listener
        """.trimIndent()
        assertEquals(2, manager.extractVersionCode(markdownBody, "1.0.1"))

        val markdownWithBackticks = """
            # DASMO Sound Box v1.0.2
            - **versionCode:** `3`
            - Performance updates
        """.trimIndent()
        assertEquals(3, manager.extractVersionCode(markdownWithBackticks, "1.0.2"))
    }

    @Test
    fun testGooglePayP2PAmountOnlyWithFallbackTitle() {
        val result = PaymentParser.parse(
            text = "₹ 500",
            fallbackTitle = "Rohit Sharma",
            packageName = "com.google.android.apps.nbu.paisa.user"
        )
        assertTrue(result.isCredit)
        assertEquals(500.0, result.amount!!, 0.01)
        assertEquals("Rohit Sharma", result.payerName)
    }

    @Test
    fun testGooglePayPaidYouWithSpace() {
        val result = PaymentParser.parse(
            text = "Paid you ₹ 500",
            fallbackTitle = "Rohit Sharma",
            packageName = "com.google.android.apps.nbu.paisa.user"
        )
        assertTrue(result.isCredit)
        assertEquals(500.0, result.amount!!, 0.01)
        assertEquals("Rohit Sharma", result.payerName)
    }

    @Test
    fun testGooglePayNonBreakingSpace() {
        val result = PaymentParser.parse(
            text = "₹\u00A0500.00 from Rohit Sharma"
        )
        assertTrue(result.isCredit)
        assertEquals(500.0, result.amount!!, 0.01)
        assertEquals("Rohit Sharma", result.payerName)
    }

    @Test
    fun testGooglePaySentPattern() {
        val result = PaymentParser.parse("Rohit Sharma sent ₹ 500")
        assertTrue(result.isCredit)
        assertEquals(500.0, result.amount!!, 0.01)
        assertEquals("Rohit Sharma", result.payerName)
    }

    @Test
    fun testGooglePaySentYouPattern() {
        val result = PaymentParser.parse("Rohit Sharma sent you ₹ 1,250")
        assertTrue(result.isCredit)
        assertEquals(1250.0, result.amount!!, 0.01)
        assertEquals("Rohit Sharma", result.payerName)
    }

    @Test
    fun testGooglePayBusinessNotification() {
        val result = PaymentParser.parse(
            text = "Payment received: ₹ 750",
            fallbackTitle = "Google Pay for Business",
            packageName = "com.google.android.apps.nbu.paisa.merchant"
        )
        assertTrue(result.isCredit)
        assertEquals(750.0, result.amount!!, 0.01)
    }

    @Test
    fun testGooglePayHindiNotification() {
        val result = PaymentParser.parse("रोहित शर्मा ने ₹ 500 भेजे")
        assertTrue(result.isCredit)
        assertEquals(500.0, result.amount!!, 0.01)
        assertEquals("रोहित शर्मा", result.payerName)
    }

    @Test
    fun testGooglePayPaymentRequestRejection() {
        val result = PaymentParser.parse(
            text = "Rohit Sharma requested ₹ 500",
            fallbackTitle = "Rohit Sharma",
            packageName = "com.google.android.apps.nbu.paisa.user"
        )
        assertFalse(result.isCredit)
        assertNull(result.amount)
    }

    @Test
    fun testUpdateNotificationNoFalsePositiveWhenAlreadyUpdated() {
        val manager = com.example.util.update.AppUpdateManager.getInstance()
        // App is already at 1.0.2 (code 3) and release is 1.0.2 (code 3) -> should be false!
        assertFalse(manager.isNewerVersion(currentVer = "1.0.2", currentCode = 3, latestVer = "1.0.2", latestCode = 3))
        // Release has no versionCode parsed (-1) but same semver -> false!
        assertFalse(manager.isNewerVersion(currentVer = "1.0.2", currentCode = 3, latestVer = "1.0.2", latestCode = -1))
        // Release has newer semver 1.0.3 -> true!
        assertTrue(manager.isNewerVersion(currentVer = "1.0.2", currentCode = 3, latestVer = "1.0.3", latestCode = 4))
    }

    @Test
    fun testRoutesIntegrity() {
        val home = com.example.presentation.navigation.Routes.HOME
        val history = com.example.presentation.navigation.Routes.HISTORY
        val analytics = com.example.presentation.navigation.Routes.ANALYTICS
        val ledger = com.example.presentation.navigation.Routes.LEDGER
        val settings = com.example.presentation.navigation.Routes.SETTINGS
        val customMsg = com.example.presentation.navigation.Routes.CUSTOM_MESSAGE
        val kiosk = com.example.presentation.navigation.Routes.KIOSK

        val routes = listOf(home, history, analytics, ledger, settings, customMsg, kiosk)
        // Ensure all routes are distinct and non-empty
        assertEquals(routes.size, routes.distinct().size)
        assertTrue(routes.none { it.isBlank() })
        assertEquals("home", home)
    }
}


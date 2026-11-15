package com.example.harness

import android.app.Notification
import android.os.Bundle
import android.os.Parcelable
import android.os.Process
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import java.util.concurrent.atomic.AtomicInteger

/**
 * Factory for creating simulated Android [StatusBarNotification] instances with
 * realistic bundles, extras, and messaging style structures.
 */
object MockStatusBarNotificationFactory {

    private val idCounter = AtomicInteger(100)

    fun createNotification(
        packageName: String,
        title: String,
        text: String,
        bigText: String? = null,
        messagingBundles: List<Bundle>? = null,
        postTime: Long = System.currentTimeMillis()
    ): StatusBarNotification {
        val id = idCounter.incrementAndGet()
        val extras = Bundle().apply {
            putString(Notification.EXTRA_TITLE, title)
            putString(Notification.EXTRA_TEXT, text)
            if (bigText != null) {
                putCharSequence(Notification.EXTRA_BIG_TEXT, bigText)
            }
            if (messagingBundles != null && messagingBundles.isNotEmpty()) {
                putParcelableArray("android.messages", messagingBundles.toTypedArray<Parcelable>())
            }
        }

        val notification = Notification().apply {
            this.extras = extras
            this.`when` = postTime
        }

        val userHandle: UserHandle = try {
            Process.myUserHandle()
        } catch (_: Throwable) {
            // Fallback for non-Robolectric plain JVM runs if needed
            val constructor = UserHandle::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
            constructor.isAccessible = true
            constructor.newInstance(0)
        }

        return StatusBarNotification(
            packageName,
            packageName,
            id,
            "soundbox_tag",
            10001,
            1001,
            0,
            notification,
            userHandle,
            postTime
        )
    }

    fun createPhonePeNotification(
        amount: Double,
        payer: String,
        refId: String? = null,
        postTime: Long = System.currentTimeMillis()
    ): StatusBarNotification {
        val formattedAmount = if (amount % 1.0 == 0.0) String.format("%.0f", amount) else String.format("%.2f", amount)
        val text = if (refId != null) {
            "Received ₹$formattedAmount from $payer via PhonePe. UTR: $refId"
        } else {
            "Received ₹$formattedAmount from $payer via PhonePe"
        }
        return createNotification(
            packageName = "com.phonepe.app",
            title = "PhonePe",
            text = text,
            postTime = postTime
        )
    }

    fun createGooglePayNotification(
        amount: Double,
        payer: String,
        postTime: Long = System.currentTimeMillis()
    ): StatusBarNotification {
        val formattedAmount = if (amount % 1.0 == 0.0) String.format("%.0f", amount) else String.format("%.2f", amount)
        val text = "₹ $formattedAmount received"

        val msgBundle = Bundle().apply {
            putCharSequence("text", text)
            putCharSequence("sender", payer)
            putLong("time", postTime)
        }

        return createNotification(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = payer,
            text = text,
            messagingBundles = listOf(msgBundle),
            postTime = postTime
        )
    }

    fun createPaytmNotification(
        amount: Double,
        payer: String,
        isQr: Boolean = true,
        postTime: Long = System.currentTimeMillis()
    ): StatusBarNotification {
        val formattedAmount = String.format("%.2f", amount)
        val text = if (isQr) {
            "Payment of Rs $formattedAmount received on Paytm QR from $payer"
        } else {
            "Received Rs $formattedAmount from $payer on Paytm"
        }
        return createNotification(
            packageName = "net.one97.paytm",
            title = "Paytm",
            text = text,
            postTime = postTime
        )
    }

    fun createBhimNotification(
        amount: Double,
        payer: String,
        refId: String? = null,
        postTime: Long = System.currentTimeMillis()
    ): StatusBarNotification {
        val formattedAmount = String.format("%.2f", amount)
        val text = if (refId != null) {
            "Payment received: Rs. $formattedAmount from $payer UPI Ref: $refId"
        } else {
            "Payment received: Rs. $formattedAmount from $payer"
        }
        return createNotification(
            packageName = "in.org.npci.upiapp",
            title = "BHIM UPI",
            text = text,
            postTime = postTime
        )
    }

    fun createHindiNotification(
        amount: Double,
        payer: String,
        postTime: Long = System.currentTimeMillis()
    ): StatusBarNotification {
        val formattedAmount = if (amount % 1.0 == 0.0) String.format("%.0f", amount) else String.format("%.2f", amount)
        val text = "$payer से ₹ $formattedAmount प्राप्त हुए"
        return createNotification(
            packageName = "com.phonepe.app",
            title = "PhonePe",
            text = text,
            postTime = postTime
        )
    }
}

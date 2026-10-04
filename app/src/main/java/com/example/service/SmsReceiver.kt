package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.di.AppModule
import com.example.domain.model.PaymentEvent
import com.example.domain.model.SourceType
import com.example.domain.parser.PaymentParser
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SoundBoxSmsReceiver"
    }

    private val BANK_SENDER_KEYWORDS = listOf(
        "HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "PNB",
        "CENT", "BOI", "UNION", "CANARA", "CANBNK", "INDUS", "YES",
        "IDFC", "RBL", "PAYTM", "PHONEPE", "GPAY", "BHIM", "BHARATPE",
        "BANK", "PAY", "UPI", "NEFT", "IMPS", "RTGS", "ALERTS", "TXN", "BNK",
        "BOB", "BARODA", "AUBANK", "AIRTEL", "JIO", "FEDERAL", "UCO", "IOB"
    )

    private val TRAI_HEADER_REGEX = Regex("""^[A-Za-z]{2}-[A-Za-z0-9]{4,9}$""")

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.firstOrNull()?.originatingAddress?.uppercase() ?: return
        
        val isBankSender = BANK_SENDER_KEYWORDS.any { sender.contains(it) } ||
                           TRAI_HEADER_REGEX.matches(sender)
        
        if (!isBankSender) return
        
        val fullText = messages.joinToString(" ") { it.messageBody }
        
        val pendingResult = goAsync()
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unhandled error in SmsReceiver coroutine: ${throwable.message}", throwable)
            pendingResult.finish()
        }

        CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler).launch {
            try {
                AppModule.getOrInit(context)
                val settings = AppModule.settingsRepository?.getSettings()?.first() ?: return@launch
                if (!settings.isEnabled || !settings.smsDetectionEnabled) return@launch
                
                val result = PaymentParser.parse(fullText)
                if (result.amount != null && result.isCredit) {
                    val bankName = when {
                        sender.contains("SBI") -> "SBI Bank SMS"
                        sender.contains("HDFC") -> "HDFC Bank SMS"
                        sender.contains("ICICI") -> "ICICI Bank SMS"
                        sender.contains("AXIS") -> "Axis Bank SMS"
                        sender.contains("KOTAK") -> "Kotak Bank SMS"
                        sender.contains("PNB") -> "PNB Bank SMS"
                        sender.contains("BOB") || sender.contains("BARODA") -> "Bank of Baroda SMS"
                        sender.contains("CANARA") || sender.contains("CANBNK") -> "Canara Bank SMS"
                        sender.contains("UNION") -> "Union Bank SMS"
                        sender.contains("IDFC") -> "IDFC FIRST Bank SMS"
                        sender.contains("PAYTM") -> "Paytm SMS"
                        sender.contains("PHONEPE") -> "PhonePe SMS"
                        else -> "Bank SMS ($sender)"
                    }

                    if (!SoundBoxForegroundService.isRunning && settings.isEnabled) {
                        try {
                            SoundBoxForegroundService.start(context)
                        } catch (e: Throwable) {
                            Log.w(TAG, "Could not start service: ${e.message}")
                        }
                    }

                    val event = PaymentEvent(
                        amount = result.amount,
                        sourceType = SourceType.SMS,
                        sourceApp = "SMS:$sender",
                        sourceAppName = bankName,
                        payerName = result.payerName,
                        refId = result.refId,
                        rawText = fullText
                    )
                    AppModule.paymentEventBus?.emit(event)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error processing incoming SMS payment: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

package com.example.domain.model

import java.util.UUID
import kotlinx.serialization.Serializable

data class PaymentEvent(
    val amount: Double,
    val currency: String = "INR",
    val sourceType: SourceType,
    val sourceApp: String,
    val sourceAppName: String,
    val payerName: String? = null,
    val refId: String? = null,
    val rawText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val dedupKey: String = ""
)

enum class SourceType { NOTIFICATION, SMS }

enum class AnnouncementStatus { ANNOUNCED, DUPLICATE_SKIPPED, PARSE_ERROR }

@Serializable
data class MessageTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val prefixEnabled: Boolean = false,
    val prefix: String = "",
    val suffixEnabled: Boolean = true,
    val suffix: String = "Thank you!"
)

data class ParseResult(
    val amount: Double?,
    val isCredit: Boolean,
    val payerName: String? = null,
    val refId: String? = null,
    val rawText: String
)

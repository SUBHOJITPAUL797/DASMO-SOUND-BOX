package com.example.domain.usecase

import com.example.data.database.entity.TransactionEntity
import com.example.domain.model.AnnouncementStatus
import com.example.domain.model.PaymentEvent
import com.example.domain.repository.TransactionRepository

class SaveTransactionUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke(event: PaymentEvent, status: AnnouncementStatus, announcementText: String?) {
        val entity = TransactionEntity(
            amount = event.amount,
            amountFormatted = "₹${String.format("%.2f", event.amount)}",
            sourceType = event.sourceType.name,
            sourceApp = event.sourceApp,
            sourceAppName = event.sourceAppName,
            payerName = event.payerName,
            rawText = event.rawText,
            timestamp = event.timestamp,
            status = status.name,
            announcementText = announcementText,
            dedupKey = event.dedupKey,
            refId = event.refId
        )
        repository.insertTransaction(entity)
    }
}

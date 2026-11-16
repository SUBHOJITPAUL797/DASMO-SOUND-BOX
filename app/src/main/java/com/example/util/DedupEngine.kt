package com.example.util

import com.example.data.database.DedupDao
import com.example.data.database.entity.DedupEntity
import com.example.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

class DedupEngine(
    private val dao: DedupDao,
    private val settingsRepository: SettingsRepository
) {
    private val mutex = Mutex()

    suspend fun generateKey(amount: Double, payerName: String? = null, refId: String? = null): String {
        val settings = settingsRepository.getSettings().first()
        val windowSeconds = settings.dedupWindowSeconds
        val now = System.currentTimeMillis()
        val amountRounded = String.format(Locale.US, "%.2f", amount)
        val extra = when {
            !refId.isNullOrBlank() -> "_${refId.trim()}"
            !payerName.isNullOrBlank() -> "_${payerName.trim().lowercase(Locale.US)}"
            else -> ""
        }
        if (windowSeconds <= 0) {
            return "${amountRounded}${extra}_$now"
        }
        val windowMs = windowSeconds * 1000L
        val bucket = (now / windowMs) * windowMs
        return "${amountRounded}${extra}_$bucket"
    }

    suspend fun isDuplicate(amount: Double, payerName: String? = null, refId: String? = null): Boolean = mutex.withLock {
        val settings = settingsRepository.getSettings().first()
        val windowSeconds = settings.dedupWindowSeconds
        if (windowSeconds <= 0) {
            return@withLock false
        }
        val windowMs = windowSeconds * 1000L
        val now = System.currentTimeMillis()
        val amountRounded = String.format(Locale.US, "%.2f", amount)

        dao.cleanupExpiredRecords(now)

        val activeRecords = dao.getValidRecordsByAmount(amount, now)
        val cleanNewRefId = refId?.trim()?.takeIf { it.isNotBlank() }
        val cleanNewPayer = payerName?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() }

        for (record in activeRecords) {
            val (savedRefId, savedPayer) = parseRecordMetadata(record.dedupKey)

            // 1. Direct refId match: If both have refId and they match -> 100% duplicate
            if (cleanNewRefId != null && savedRefId != null) {
                if (cleanNewRefId.equals(savedRefId, ignoreCase = true)) {
                    return@withLock true
                }
                // Different refIds: distinct transactions -> continue checking
                continue
            }

            // 2. Direct payerName match: If both have payerName and they match -> 100% duplicate
            if (cleanNewPayer != null && savedPayer != null) {
                if (cleanNewPayer.equals(savedPayer, ignoreCase = true)) {
                    return@withLock true
                }
                // Different payer names: different customers -> continue checking
                continue
            }

            // 3. Cross-channel match (SMS vs Notification / Unnamed vs Named):
            // One or both lacks refId/payer (e.g. Bank SMS has no payerName, or notification has no refId),
            // and neither contradicts the other. Since they share the same amount within the sliding window:
            return@withLock true
        }

        // Not a duplicate: register active record in Room with sliding expiration
        val recordKey = "KEY:${amountRounded}:${cleanNewRefId ?: ""}:${cleanNewPayer ?: ""}:$now"
        dao.insertRecord(
            DedupEntity(
                dedupKey = recordKey,
                timestamp = now,
                amount = amount,
                expiresAt = now + windowMs
            )
        )
        false
    }

    private fun parseRecordMetadata(key: String): Pair<String?, String?> {
        if (key.startsWith("KEY:")) {
            val parts = key.split(":")
            if (parts.size >= 5) {
                val savedRefId = parts[2].takeIf { it.isNotBlank() }
                val savedPayer = parts[3].takeIf { it.isNotBlank() }
                return Pair(savedRefId, savedPayer)
            }
        }
        // Legacy fallback: format was "${amountRounded}${extra}_$bucket"
        val extra = key.substringAfter('_', "").substringBeforeLast('_')
        return if (extra.matches(Regex("""^\d{6,}$"""))) {
            Pair(extra, null)
        } else {
            Pair(null, extra.takeIf { it.isNotBlank() })
        }
    }
}

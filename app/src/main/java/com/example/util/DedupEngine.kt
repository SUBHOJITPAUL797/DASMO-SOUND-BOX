package com.example.util

import com.example.data.database.DedupDao
import com.example.data.database.entity.DedupEntity
import com.example.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DedupEngine(
    private val dao: DedupDao,
    private val settingsRepository: SettingsRepository
) {
    private val mutex = Mutex()
    suspend fun generateKey(amount: Double, payerName: String? = null, refId: String? = null): String {
        val settings = settingsRepository.getSettings().first()
        val windowSeconds = settings.dedupWindowSeconds
        val now = System.currentTimeMillis()
        val amountRounded = String.format(java.util.Locale.US, "%.2f", amount)
        val extra = when {
            !refId.isNullOrBlank() -> "_${refId.trim()}"
            !payerName.isNullOrBlank() -> "_${payerName.trim().lowercase(java.util.Locale.US)}"
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
        val bucket = (now / windowMs) * windowMs
        val amountRounded = String.format(java.util.Locale.US, "%.2f", amount)
        val extra = when {
            !refId.isNullOrBlank() -> "_${refId.trim()}"
            !payerName.isNullOrBlank() -> "_${payerName.trim().lowercase(java.util.Locale.US)}"
            else -> ""
        }
        val dedupKey = "${amountRounded}${extra}_$bucket"

        val record = dao.getValidRecord(dedupKey, now)
        dao.cleanupExpiredRecords(now)
        
        if (record != null) {
            true
        } else {
            dao.insertRecord(
                DedupEntity(
                    dedupKey = dedupKey,
                    timestamp = now,
                    amount = amount,
                    expiresAt = now + windowMs
                )
            )
            false
        }
    }
}

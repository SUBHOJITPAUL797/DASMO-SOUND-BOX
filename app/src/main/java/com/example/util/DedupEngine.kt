package com.example.util

import com.example.data.database.DedupDao
import com.example.data.database.entity.DedupEntity
import com.example.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

class DedupEngine(
    private val dao: DedupDao,
    private val settingsRepository: SettingsRepository
) {
    suspend fun generateKey(amount: Double): String {
        val settings = settingsRepository.getSettings().first()
        val windowMs = settings.dedupWindowSeconds * 1000L
        val now = System.currentTimeMillis()
        val bucket = (now / windowMs) * windowMs
        val amountRounded = String.format("%.2f", amount)
        return "${amountRounded}_$bucket"
    }

    suspend fun isDuplicate(amount: Double): Boolean {
        val settings = settingsRepository.getSettings().first()
        val windowMs = settings.dedupWindowSeconds * 1000L
        val now = System.currentTimeMillis()
        val bucket = (now / windowMs) * windowMs
        val amountRounded = String.format("%.2f", amount)
        val dedupKey = "${amountRounded}_$bucket"

        val record = dao.getValidRecord(dedupKey, now)
        dao.cleanupExpiredRecords(now)
        
        if (record != null) {
            return true
        } else {
            dao.insertRecord(
                DedupEntity(
                    dedupKey = dedupKey,
                    timestamp = now,
                    amount = amount,
                    expiresAt = now + windowMs
                )
            )
            return false
        }
    }
}

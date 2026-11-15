package com.example.harness

import android.os.Bundle
import com.example.data.database.DedupDao
import com.example.data.database.entity.DedupEntity
import com.example.data.database.entity.TransactionEntity
import com.example.domain.model.AnnouncementStatus
import com.example.domain.model.AppSettings
import com.example.domain.model.PaymentEvent
import com.example.domain.model.SourceType
import com.example.domain.parser.PaymentParser
import com.example.domain.repository.SettingsRepository
import com.example.domain.repository.TransactionRepository
import com.example.domain.usecase.SaveTransactionUseCase
import com.example.service.PaymentEventBus
import com.example.util.DedupEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Production-grade mock payment injection harness.
 * Simulates end-to-end payment notification and SMS ingestion,
 * tracking pipeline stages with nanosecond precision telemetry.
 */
class MockPaymentInjectionHarness {

    val spyChime = SpyChimePlayer()
    val spyTts = SpyTtsEngine()

    val settingsRepository = InMemorySettingsRepository()
    val transactionRepository = InMemoryTransactionRepository()
    val dedupDao = InMemoryDedupDao()
    val dedupEngine = DedupEngine(dedupDao, settingsRepository)
    val saveTransactionUseCase = SaveTransactionUseCase(transactionRepository)
    val paymentEventBus = PaymentEventBus()

    private val recordedTelemetry = CopyOnWriteArrayList<PipelineTelemetry>()

    suspend fun injectNotification(
        packageName: String,
        title: String,
        text: String,
        extras: Bundle? = null
    ): Long {
        val injectionNano = System.nanoTime()
        val parseResult = PaymentParser.parse(text = text, fallbackTitle = title, packageName = packageName)
        val parseNano = System.nanoTime()

        if (parseResult.amount != null && parseResult.isCredit) {
            val appName = when {
                packageName.contains("phonepe") -> "PhonePe"
                packageName.contains("paisa") -> "Google Pay"
                packageName.contains("paytm") -> "Paytm"
                packageName.contains("bhim") -> "BHIM UPI"
                else -> packageName
            }

            val event = PaymentEvent(
                amount = parseResult.amount,
                sourceType = SourceType.NOTIFICATION,
                sourceApp = packageName,
                sourceAppName = appName,
                payerName = parseResult.payerName,
                refId = parseResult.refId,
                rawText = text,
                timestamp = System.currentTimeMillis()
            )

            // Incorporate PaymentEventBus into default notification ingestion flow
            paymentEventBus.emit(event)
            executePipeline(event, injectionNano, parseNano, parseResult.payerName, parseResult.refId)
        }
        return injectionNano
    }

    suspend fun injectSms(
        sender: String,
        messageBody: String
    ): Long {
        val injectionNano = System.nanoTime()
        val parseResult = PaymentParser.parse(text = messageBody)
        val parseNano = System.nanoTime()

        if (parseResult.amount != null && parseResult.isCredit) {
            val event = PaymentEvent(
                amount = parseResult.amount,
                sourceType = SourceType.SMS,
                sourceApp = sender,
                sourceAppName = "Bank SMS ($sender)",
                payerName = parseResult.payerName,
                refId = parseResult.refId,
                rawText = messageBody,
                timestamp = System.currentTimeMillis()
            )

            // Incorporate PaymentEventBus into default SMS ingestion flow
            paymentEventBus.emit(event)
            executePipeline(event, injectionNano, parseNano, parseResult.payerName, parseResult.refId)
        }
        return injectionNano
    }

    suspend fun injectEvent(event: PaymentEvent): Long {
        val injectionNano = System.nanoTime()
        paymentEventBus.emit(event)
        executePipeline(event, injectionNano, injectionNano, event.payerName, event.refId)
        return injectionNano
    }

    suspend fun executePipeline(
        event: PaymentEvent,
        injectionNano: Long,
        parseNano: Long,
        parsedPayer: String?,
        parsedRefId: String?
    ): PipelineTelemetry {
        val settings = settingsRepository.getLatestSettings()

        // Service guard: if SoundBox service is disabled, stop immediately
        if (!settings.isEnabled) {
            val telemetry = PipelineTelemetry(
                injectionNano = injectionNano,
                parserCompletedNano = parseNano,
                dedupCheckedNano = 0L,
                chimeTriggeredNano = 0L,
                ttsInvokedNano = 0L,
                transactionSavedNano = 0L,
                isDuplicate = false,
                status = "SERVICE_DISABLED",
                parsedAmount = event.amount,
                parsedPayer = parsedPayer,
                parsedRefId = parsedRefId
            )
            recordedTelemetry.add(telemetry)
            return telemetry
        }

        val dedupKey = dedupEngine.generateKey(event.amount, event.payerName, event.refId)
        val isDuplicate = dedupEngine.isDuplicate(event.amount, event.payerName, event.refId)
        val dedupNano = System.nanoTime()

        val enrichedEvent = event.copy(dedupKey = dedupKey)

        if (isDuplicate) {
            saveTransactionUseCase(enrichedEvent, AnnouncementStatus.DUPLICATE_SKIPPED, null)
            val savedNano = System.nanoTime()
            val telemetry = PipelineTelemetry(
                injectionNano = injectionNano,
                parserCompletedNano = parseNano,
                dedupCheckedNano = dedupNano,
                transactionSavedNano = savedNano,
                isDuplicate = true,
                status = "DUPLICATE_SKIPPED",
                parsedAmount = event.amount,
                parsedPayer = parsedPayer,
                parsedRefId = parsedRefId
            )
            recordedTelemetry.add(telemetry)
            return telemetry
        }

        if (settings.minAmountThreshold > 0 && event.amount < settings.minAmountThreshold) {
            saveTransactionUseCase(
                enrichedEvent,
                AnnouncementStatus.DUPLICATE_SKIPPED,
                "Below threshold ${settings.minAmountThreshold}"
            )
            val savedNano = System.nanoTime()
            val telemetry = PipelineTelemetry(
                injectionNano = injectionNano,
                parserCompletedNano = parseNano,
                dedupCheckedNano = dedupNano,
                transactionSavedNano = savedNano,
                isDuplicate = false,
                status = "BELOW_THRESHOLD",
                parsedAmount = event.amount,
                parsedPayer = parsedPayer,
                parsedRefId = parsedRefId
            )
            recordedTelemetry.add(telemetry)
            return telemetry
        }

        var chimeNano = 0L
        if (settings.chimeEnabled && !settings.chimeSound.equals("MUTE", ignoreCase = true)) {
            chimeNano = spyChime.recordPlay(settings.chimeSound, settings.announcementVolume)
        }

        val speechText = buildAnnouncementText(event.amount, parsedPayer, settings.language)
        val ttsNano = spyTts.recordSpeak(
            text = speechText,
            utteranceId = "utt_${System.currentTimeMillis()}",
            volume = settings.announcementVolume,
            language = settings.language,
            rate = settings.speechRate,
            pitch = settings.voicePitch
        )

        saveTransactionUseCase(enrichedEvent, AnnouncementStatus.ANNOUNCED, speechText)
        val savedNano = System.nanoTime()

        val telemetry = PipelineTelemetry(
            injectionNano = injectionNano,
            parserCompletedNano = parseNano,
            dedupCheckedNano = dedupNano,
            chimeTriggeredNano = chimeNano,
            ttsInvokedNano = ttsNano,
            transactionSavedNano = savedNano,
            isDuplicate = false,
            status = "ANNOUNCED",
            announcedText = speechText,
            parsedAmount = event.amount,
            parsedPayer = parsedPayer,
            parsedRefId = parsedRefId
        )
        recordedTelemetry.add(telemetry)
        return telemetry
    }

    private fun buildAnnouncementText(amount: Double, payer: String?, language: String): String {
        val formatted = if (amount % 1.0 == 0.0) String.format("%.0f", amount) else String.format("%.2f", amount)
        return if (payer != null) {
            "$payer से $formatted रुपये प्राप्त हुए"
        } else {
            "$formatted रुपये प्राप्त हुए"
        }
    }

    suspend fun awaitAnnouncement(timeoutMs: Long = 1000L): PipelineTelemetry {
        val result = withTimeoutOrNull(timeoutMs) {
            while (recordedTelemetry.isEmpty()) {
                kotlinx.coroutines.delay(5)
            }
            recordedTelemetry.last()
        }
        return result ?: throw AssertionError("Timed out waiting for announcement within ${timeoutMs}ms (current telemetry count: ${recordedTelemetry.size})")
    }

    suspend fun awaitAnnouncementCount(expectedCount: Int, timeoutMs: Long = 1000L): PipelineTelemetry {
        val result = withTimeoutOrNull(timeoutMs) {
            while (recordedTelemetry.size < expectedCount) {
                kotlinx.coroutines.delay(5)
            }
            recordedTelemetry[expectedCount - 1]
        }
        return result ?: throw AssertionError("Timed out waiting for announcement #$expectedCount within ${timeoutMs}ms (current: ${recordedTelemetry.size})")
    }

    fun getAllTelemetry(): List<PipelineTelemetry> = recordedTelemetry.toList()

    fun reset() {
        spyChime.reset()
        spyTts.reset()
        transactionRepository.clear()
        dedupDao.clear()
        recordedTelemetry.clear()
    }

    class InMemorySettingsRepository : SettingsRepository {
        private val _settings = MutableStateFlow(AppSettings())
        override fun getSettings(): Flow<AppSettings> = _settings.asStateFlow()
        override fun getLatestSettings(): AppSettings = _settings.value

        override suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings) {
            _settings.update { transform(it) }
        }

        override suspend fun updateIsEnabled(enabled: Boolean) {
            _settings.update { it.copy(isEnabled = enabled) }
        }

        override suspend fun updateHasCompletedOnboarding(completed: Boolean) {
            _settings.update { it.copy(hasCompletedOnboarding = completed) }
        }

        override suspend fun updateAppLockEnabled(enabled: Boolean) {
            _settings.update { it.copy(appLockEnabled = enabled) }
        }

        override suspend fun updateAppLockType(type: String) {
            _settings.update { it.copy(appLockType = type) }
        }

        override suspend fun updateAppLockPin(pin: String) {
            _settings.update { it.copy(appLockPin = pin) }
        }

        override suspend fun updateAppLockPattern(pattern: String) {
            _settings.update { it.copy(appLockPattern = pattern) }
        }

        override suspend fun updateLockedAppPackages(packages: Set<String>) {
            _settings.update { it.copy(lockedAppPackages = packages) }
        }

        override suspend fun updateIntruderSelfieEnabled(enabled: Boolean) {
            _settings.update { it.copy(intruderSelfieEnabled = enabled) }
        }

        fun updateDirectly(block: (AppSettings) -> AppSettings) {
            _settings.update(block)
        }
    }

    class InMemoryTransactionRepository : TransactionRepository {
        private val list = CopyOnWriteArrayList<TransactionEntity>()
        private val flow = MutableStateFlow<List<TransactionEntity>>(emptyList())

        override suspend fun insertTransaction(transaction: TransactionEntity) {
            val id = (list.size + 1).toLong()
            val entity = transaction.copy(id = id)
            list.add(entity)
            flow.value = list.toList()
        }

        override fun getAllTransactions(): Flow<List<TransactionEntity>> = flow.asStateFlow()

        override suspend fun deleteTransaction(id: Long) {
            list.removeAll { it.id == id }
            flow.value = list.toList()
        }

        override suspend fun clearAll() {
            list.clear()
            flow.value = emptyList()
        }

        suspend fun getTransactionById(id: Long): TransactionEntity? {
            return list.find { it.id == id }
        }

        suspend fun deleteOldTransactions(olderThan: Long) {
            list.removeAll { it.timestamp < olderThan }
            flow.value = list.toList()
        }

        suspend fun clearAllTransactions() {
            list.clear()
            flow.value = emptyList()
        }

        fun clear() = list.clear()
        fun getAll(): List<TransactionEntity> = list.toList()
    }

    class InMemoryDedupDao : DedupDao {
        private val records = ConcurrentHashMap<String, DedupEntity>()

        override suspend fun getValidRecord(key: String, now: Long): DedupEntity? {
            val record = records[key] ?: return null
            return if (record.expiresAt > now) record else null
        }

        override suspend fun insertRecord(record: DedupEntity) {
            records[record.dedupKey] = record
        }

        override suspend fun cleanupExpiredRecords(now: Long) {
            records.entries.removeIf { it.value.expiresAt < now }
        }

        fun clear() = records.clear()
        fun size() = records.size
    }
}

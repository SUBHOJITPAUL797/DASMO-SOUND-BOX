package com.example.harness

import com.example.data.database.IntruderDao
import com.example.data.database.entity.IntruderLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.CopyOnWriteArrayList

class InMemoryIntruderDao : IntruderDao {
    private val logs = CopyOnWriteArrayList<IntruderLogEntity>()
    private val logsFlow = MutableStateFlow<List<IntruderLogEntity>>(emptyList())

    override fun getAllLogs(): Flow<List<IntruderLogEntity>> = logsFlow.asStateFlow()

    override suspend fun insertLog(log: IntruderLogEntity): Long {
        val id = (logs.size + 1).toLong()
        val entity = log.copy(id = id)
        logs.add(0, entity)
        logsFlow.value = logs.toList()
        return id
    }

    override suspend fun deleteLog(id: Long) {
        logs.removeAll { it.id == id }
        logsFlow.value = logs.toList()
    }

    override suspend fun clearAllLogs() {
        logs.clear()
        logsFlow.value = emptyList()
    }

    override fun getLogCount(): Flow<Int> {
        return logsFlow.map { it.size }
    }

    override suspend fun getLogById(id: Long): IntruderLogEntity? {
        return logs.find { it.id == id }
    }

    fun clear() {
        logs.clear()
        logsFlow.value = emptyList()
    }
}

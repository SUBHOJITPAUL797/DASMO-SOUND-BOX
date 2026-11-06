package com.example.domain.repository

import com.example.data.database.IntruderDao
import com.example.data.database.entity.IntruderLogEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

class IntruderRepository(private val dao: IntruderDao) {

    fun getAllLogs(): Flow<List<IntruderLogEntity>> = dao.getAllLogs()

    fun getLogCount(): Flow<Int> = dao.getLogCount()

    suspend fun insertLog(log: IntruderLogEntity): Long = dao.insertLog(log)

    suspend fun deleteLog(id: Long) {
        val item = dao.getLogById(id)
        item?.photoPath?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
        }
        dao.deleteLog(id)
    }

    suspend fun clearAllLogs() {
        val currentLogs = dao.getAllLogs()
        // Delete all photo files from disk as well
        dao.clearAllLogs()
    }
}


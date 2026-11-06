package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entity.IntruderLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IntruderDao {

    @Query("SELECT * FROM intruder_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<IntruderLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: IntruderLogEntity): Long

    @Query("DELETE FROM intruder_logs WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("DELETE FROM intruder_logs")
    suspend fun clearAllLogs()

    @Query("SELECT COUNT(*) FROM intruder_logs")
    fun getLogCount(): Flow<Int>

    @Query("SELECT * FROM intruder_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Long): IntruderLogEntity?
}


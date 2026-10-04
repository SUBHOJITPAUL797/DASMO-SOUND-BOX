package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entity.DedupEntity

@Dao
interface DedupDao {
    @Query("SELECT * FROM dedup_records WHERE dedupKey = :key AND expiresAt > :now")
    suspend fun getValidRecord(key: String, now: Long): DedupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: DedupEntity)

    @Query("DELETE FROM dedup_records WHERE expiresAt < :now")
    suspend fun cleanupExpiredRecords(now: Long)
}

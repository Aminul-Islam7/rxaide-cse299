package com.example.rxaide.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.rxaide.data.entity.DoseHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doseHistory: DoseHistory): Long

    @Delete
    suspend fun delete(doseHistory: DoseHistory)

    @Query("SELECT * FROM dose_history WHERE medicationId = :medicationId ORDER BY scheduledTime DESC")
    fun getHistoryForMedication(medicationId: Long): Flow<List<DoseHistory>>

    @Query("SELECT * FROM dose_history ORDER BY scheduledTime DESC")
    fun getAllHistory(): Flow<List<DoseHistory>>

    @Query("SELECT * FROM dose_history WHERE scheduledTime BETWEEN :startTime AND :endTime ORDER BY scheduledTime DESC")
    fun getHistoryBetween(startTime: Long, endTime: Long): Flow<List<DoseHistory>>

    @Query("SELECT COUNT(*) FROM dose_history WHERE status = 'taken' AND medicationId = :medicationId")
    fun getTakenCountForMedication(medicationId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM dose_history WHERE status = 'missed' AND medicationId = :medicationId")
    fun getMissedCountForMedication(medicationId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM dose_history WHERE status = 'taken'")
    fun getTotalTakenCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM dose_history WHERE status = 'missed'")
    fun getTotalMissedCount(): Flow<Int>
}

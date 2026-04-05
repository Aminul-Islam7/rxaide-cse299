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

    @Query("UPDATE dose_history SET status = :newStatus, actionTime = :actionTime WHERE id = :doseId")
    suspend fun updateDoseStatus(doseId: Long, newStatus: String, actionTime: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE dose_history
        SET status = :newStatus, actionTime = :actionTime
        WHERE id = (
            SELECT id
            FROM dose_history
            WHERE medicationId = :medicationId
              AND (:scheduleId IS NULL OR scheduleId = :scheduleId)
              AND status = 'unmarked'
            ORDER BY scheduledTime DESC
            LIMIT 1
        )
        """
    )
    suspend fun updateLatestUnmarkedDoseStatus(
        medicationId: Long,
        scheduleId: Long?,
        newStatus: String,
        actionTime: Long = System.currentTimeMillis()
    ): Int

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

    @Query("SELECT COUNT(*) FROM dose_history WHERE status = 'unmarked' AND medicationId = :medicationId")
    fun getUnmarkedCountForMedication(medicationId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM dose_history WHERE status = 'taken'")
    fun getTotalTakenCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM dose_history WHERE status = 'missed'")
    fun getTotalMissedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM dose_history WHERE status = 'unmarked'")
    fun getTotalUnmarkedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM dose_history WHERE status = 'unmarked' AND scheduledTime BETWEEN :startOfDay AND :endOfDay")
    fun getUnmarkedCountForDay(startOfDay: Long, endOfDay: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM dose_history WHERE status = :status AND scheduledTime BETWEEN :start AND :end")
    fun getCountByStatusBetween(status: String, start: Long, end: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM dose_history WHERE status = :status")
    fun getCountByStatus(status: String): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM dose_history
        WHERE medicationId = :medicationId
          AND scheduleId = :scheduleId
          AND scheduledTime BETWEEN :dayStart AND :dayEnd
    """)
    suspend fun getDoseCountForScheduleOnDay(
        medicationId: Long,
        scheduleId: Long,
        dayStart: Long,
        dayEnd: Long
    ): Int
}

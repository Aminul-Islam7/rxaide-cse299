package com.example.rxaide.data.repository

import com.example.rxaide.data.dao.DoseHistoryDao
import com.example.rxaide.data.dao.MedicationDao
import com.example.rxaide.data.dao.ScheduleDao
import com.example.rxaide.data.entity.DoseHistory
import com.example.rxaide.data.entity.Medication
import com.example.rxaide.data.entity.Schedule
import kotlinx.coroutines.flow.Flow

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
    private val doseHistoryDao: DoseHistoryDao
) {
    // Medication operations
    val allMedications: Flow<List<Medication>> = medicationDao.getAllMedications()
    val activeMedications: Flow<List<Medication>> = medicationDao.getActiveMedications()
    val activeMedicationCount: Flow<Int> = medicationDao.getActiveMedicationCount()

    fun getMedicationById(id: Long): Flow<Medication?> = medicationDao.getMedicationById(id)

    suspend fun insertMedication(medication: Medication): Long = medicationDao.insert(medication)

    suspend fun updateMedication(medication: Medication) = medicationDao.update(medication)

    suspend fun deleteMedication(medication: Medication) = medicationDao.delete(medication)

    suspend fun deleteMedicationById(id: Long) = medicationDao.deleteById(id)

    // Schedule operations
    fun getSchedulesForMedication(medicationId: Long): Flow<List<Schedule>> =
        scheduleDao.getSchedulesForMedication(medicationId)

    val allEnabledSchedules: Flow<List<Schedule>> = scheduleDao.getAllEnabledSchedules()

    suspend fun insertSchedule(schedule: Schedule): Long = scheduleDao.insert(schedule)

    suspend fun insertSchedules(schedules: List<Schedule>) = scheduleDao.insertAll(schedules)

    suspend fun updateSchedule(schedule: Schedule) = scheduleDao.update(schedule)

    suspend fun deleteSchedule(schedule: Schedule) = scheduleDao.delete(schedule)

    suspend fun deleteSchedulesForMedication(medicationId: Long) =
        scheduleDao.deleteSchedulesForMedication(medicationId)

    // Dose History operations
    val allDoseHistory: Flow<List<DoseHistory>> = doseHistoryDao.getAllHistory()
    val totalTakenCount: Flow<Int> = doseHistoryDao.getTotalTakenCount()
    val totalMissedCount: Flow<Int> = doseHistoryDao.getTotalMissedCount()

    fun getHistoryForMedication(medicationId: Long): Flow<List<DoseHistory>> =
        doseHistoryDao.getHistoryForMedication(medicationId)

    fun getHistoryBetween(startTime: Long, endTime: Long): Flow<List<DoseHistory>> =
        doseHistoryDao.getHistoryBetween(startTime, endTime)

    suspend fun insertDoseHistory(doseHistory: DoseHistory): Long =
        doseHistoryDao.insert(doseHistory)

    fun getTakenCountForMedication(medicationId: Long): Flow<Int> =
        doseHistoryDao.getTakenCountForMedication(medicationId)

    fun getMissedCountForMedication(medicationId: Long): Flow<Int> =
        doseHistoryDao.getMissedCountForMedication(medicationId)
}

package com.example.rxaide.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rxaide.RxAideApplication
import com.example.rxaide.data.entity.DoseHistory
import com.example.rxaide.data.entity.Medication
import com.example.rxaide.data.entity.Schedule
import com.example.rxaide.notification.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MedicationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as RxAideApplication).repository

    // Medications
    val allMedications: StateFlow<List<Medication>> = repository.allMedications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeMedications: StateFlow<List<Medication>> = repository.activeMedications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeMedicationCount: StateFlow<Int> = repository.activeMedicationCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Dose History stats
    val totalTakenCount: StateFlow<Int> = repository.totalTakenCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalMissedCount: StateFlow<Int> = repository.totalMissedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Captured image path state
    private val _capturedImagePath = MutableStateFlow<String?>(null)
    val capturedImagePath: StateFlow<String?> = _capturedImagePath.asStateFlow()

    fun setCapturedImagePath(path: String?) {
        _capturedImagePath.value = path
    }

    fun getMedicationById(id: Long): Flow<Medication?> = repository.getMedicationById(id)

    fun getSchedulesForMedication(medicationId: Long): Flow<List<Schedule>> =
        repository.getSchedulesForMedication(medicationId)

    fun getHistoryForMedication(medicationId: Long): Flow<List<DoseHistory>> =
        repository.getHistoryForMedication(medicationId)

    fun insertMedication(medication: Medication, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertMedication(medication)
            onResult(id)
        }
    }

    /**
     * Inserts a medication and its schedules in one call,
     * then schedules WorkManager reminders for each schedule entry.
     *
     * @param soundUri Optional custom notification sound URI string.
     */
    fun addMedicationWithSchedules(
        medication: Medication,
        schedules: List<Schedule>,
        soundUri: String? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val savedMed = medication.copy(notificationSoundUri = soundUri)
            val medId = repository.insertMedication(savedMed)
            if (schedules.isNotEmpty()) {
                val linked = schedules.map { it.copy(medicationId = medId) }
                repository.insertSchedules(linked)

                // Schedule WorkManager reminders
                val savedSchedules = repository.getSchedulesForMedication(medId).first()
                val fullMed = savedMed.copy(id = medId)
                ReminderScheduler.scheduleAllReminders(
                    getApplication(),
                    fullMed,
                    savedSchedules,
                    soundUri
                )
            }
            onComplete()
        }
    }

    fun updateMedication(medication: Medication) {
        viewModelScope.launch {
            repository.updateMedication(medication)
        }
    }

    /**
     * Updates a medication and replaces all its schedules,
     * then reschedules WorkManager reminders.
     */
    fun updateMedicationWithSchedules(
        medication: Medication,
        schedules: List<Schedule>,
        soundUri: String? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val updatedMed = medication.copy(
                notificationSoundUri = soundUri,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateMedication(updatedMed)

            // Cancel old reminders
            ReminderScheduler.cancelRemindersForMedication(getApplication(), medication.id)

            // Delete old schedules and insert new ones
            repository.deleteSchedulesForMedication(medication.id)
            if (schedules.isNotEmpty()) {
                val linked = schedules.map { it.copy(medicationId = medication.id) }
                repository.insertSchedules(linked)

                // Reschedule reminders
                val savedSchedules = repository.getSchedulesForMedication(medication.id).first()
                ReminderScheduler.scheduleAllReminders(
                    getApplication(),
                    updatedMed,
                    savedSchedules,
                    soundUri
                )
            }
            onComplete()
        }
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            ReminderScheduler.cancelRemindersForMedication(getApplication(), medication.id)
            repository.deleteMedication(medication)
        }
    }

    fun deleteMedicationById(id: Long) {
        viewModelScope.launch {
            ReminderScheduler.cancelRemindersForMedication(getApplication(), id)
            repository.deleteMedicationById(id)
        }
    }

    fun insertSchedule(schedule: Schedule) {
        viewModelScope.launch {
            repository.insertSchedule(schedule)
        }
    }

    fun insertSchedules(schedules: List<Schedule>) {
        viewModelScope.launch {
            repository.insertSchedules(schedules)
        }
    }

    fun deleteSchedulesForMedication(medicationId: Long) {
        viewModelScope.launch {
            ReminderScheduler.cancelRemindersForMedication(getApplication(), medicationId)
            repository.deleteSchedulesForMedication(medicationId)
        }
    }

    fun insertDoseHistory(doseHistory: DoseHistory) {
        viewModelScope.launch {
            repository.insertDoseHistory(doseHistory)
        }
    }
}

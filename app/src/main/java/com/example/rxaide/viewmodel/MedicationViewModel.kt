package com.example.rxaide.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rxaide.RxAideApplication
import com.example.rxaide.data.entity.DoseHistory
import com.example.rxaide.data.entity.Medication
import com.example.rxaide.data.entity.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
     * Inserts a medication and its schedules in one call.
     * Calls [onComplete] on the main thread when finished.
     */
    fun addMedicationWithSchedules(
        medication: Medication,
        schedules: List<Schedule>,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val medId = repository.insertMedication(medication)
            if (schedules.isNotEmpty()) {
                val linked = schedules.map { it.copy(medicationId = medId) }
                repository.insertSchedules(linked)
            }
            onComplete()
        }
    }

    fun updateMedication(medication: Medication) {
        viewModelScope.launch {
            repository.updateMedication(medication)
        }
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            repository.deleteMedication(medication)
        }
    }

    fun deleteMedicationById(id: Long) {
        viewModelScope.launch {
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
            repository.deleteSchedulesForMedication(medicationId)
        }
    }

    fun insertDoseHistory(doseHistory: DoseHistory) {
        viewModelScope.launch {
            repository.insertDoseHistory(doseHistory)
        }
    }
}

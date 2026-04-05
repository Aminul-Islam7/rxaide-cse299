package com.example.rxaide.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rxaide.RxAideApplication
import com.example.rxaide.data.entity.DoseHistory
import com.example.rxaide.data.entity.Medication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class TimePeriod(val label: String) {
    ALL("All Time"),
    MONTH("This Month"),
    WEEK("This Week"),
    TODAY("Today")
}

data class MedicationAdherenceStat(
    val medication: Medication,
    val takenCount: Int,
    val missedCount: Int,
    val unmarkedCount: Int
) {
    val total get() = takenCount + missedCount + unmarkedCount
    val adherencePercent get() = if (total > 0) (takenCount.toFloat() / total * 100f) else 0f
}

data class DoseHistoryEntry(
    val doseHistory: DoseHistory,
    val medicationName: String
)

@OptIn(ExperimentalCoroutinesApi::class)
class AdherenceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as RxAideApplication).repository

    private val _selectedPeriod = MutableStateFlow(TimePeriod.ALL)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()

    fun selectPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
    }

    // Overall stats
    val totalTakenCount: StateFlow<Int> = repository.totalTakenCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalMissedCount: StateFlow<Int> = repository.totalMissedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalUnmarkedCount: StateFlow<Int> = repository.totalUnmarkedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Today's unmarked count — used for badge
    val todayUnmarkedCount: StateFlow<Int> = run {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        repository.getUnmarkedCountForDay(start, end)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    }

    // Period-scoped taken/missed counts
    val periodTakenCount: StateFlow<Int> = _selectedPeriod.flatMapLatest { period ->
        val (start, end) = periodRange(period)
        if (period == TimePeriod.ALL) {
            repository.getCountByStatus("taken")
        } else {
            repository.getCountByStatusBetween("taken", start, end)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val periodMissedCount: StateFlow<Int> = _selectedPeriod.flatMapLatest { period ->
        if (period == TimePeriod.ALL) {
            totalMissedCount
        } else {
            val (start, end) = periodRange(period)
            repository.getCountByStatusBetween("missed", start, end)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val periodUnmarkedCount: StateFlow<Int> = _selectedPeriod.flatMapLatest { period ->
        if (period == TimePeriod.ALL) {
            totalUnmarkedCount
        } else {
            val (start, end) = periodRange(period)
            repository.getCountByStatusBetween("unmarked", start, end)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val overallAdherencePercent: StateFlow<Float> = combine(
        repository.totalTakenCount,
        repository.totalMissedCount
    ) { taken, missed ->
        val total = taken + missed
        if (total > 0) (taken.toFloat() / total * 100f) else 0f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // Per-medication stats
    val perMedicationStats: StateFlow<List<MedicationAdherenceStat>> =
        repository.activeMedications.flatMapLatest { medications ->
            val takenFlows = medications.map { med ->
                repository.getTakenCountForMedication(med.id).map { count -> med.id to count }
            }
            val missedFlows = medications.map { med ->
                repository.getMissedCountForMedication(med.id).map { count -> med.id to count }
            }
            val unmarkedFlows = medications.map { med ->
                repository.getUnmarkedCountForMedication(med.id).map { count -> med.id to count }
            }
            val allFlows = takenFlows + missedFlows + unmarkedFlows
            if (allFlows.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                combine(allFlows) { results ->
                    val takenMap = mutableMapOf<Long, Int>()
                    val missedMap = mutableMapOf<Long, Int>()
                    val unmarkedMap = mutableMapOf<Long, Int>()
                    for (i in medications.indices) {
                        takenMap[results[i].first] = results[i].second
                    }
                    for (i in medications.indices) {
                        missedMap[results[medications.size + i].first] =
                            results[medications.size + i].second
                    }
                    for (i in medications.indices) {
                        unmarkedMap[results[medications.size * 2 + i].first] =
                            results[medications.size * 2 + i].second
                    }
                    medications.map { med ->
                        MedicationAdherenceStat(
                            medication = med,
                            takenCount = takenMap[med.id] ?: 0,
                            missedCount = missedMap[med.id] ?: 0,
                            unmarkedCount = unmarkedMap[med.id] ?: 0
                        )
                    }.filter { it.total > 0 }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dose history filtered by period, enriched with medication names
    val doseHistoryEntries: StateFlow<List<DoseHistoryEntry>> = combine(
        _selectedPeriod.flatMapLatest { period ->
            val (start, end) = periodRange(period)
            if (period == TimePeriod.ALL) {
                repository.allDoseHistory
            } else {
                repository.getHistoryBetween(start, end)
            }
        },
        repository.allMedications
    ) { history, medications ->
        val medMap = medications.associateBy { it.id }
        history.map { dose ->
            DoseHistoryEntry(
                doseHistory = dose,
                medicationName = dose.medicationId
                    ?.let { medMap[it]?.name }
                    ?: "Deleted medication"
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun periodRange(period: TimePeriod): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val end = cal.timeInMillis
        when (period) {
            TimePeriod.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            TimePeriod.WEEK -> cal.add(Calendar.DAY_OF_YEAR, -7)
            TimePeriod.MONTH -> cal.add(Calendar.MONTH, -1)
            TimePeriod.ALL -> cal.timeInMillis = 0
        }
        return cal.timeInMillis to end
    }

    // Update dose status (from adherence tracker)
    fun updateDoseStatus(doseId: Long, newStatus: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.updateDoseStatus(doseId, newStatus)
        }
    }
}

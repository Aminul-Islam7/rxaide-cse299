package com.example.rxaide.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.rxaide.RxAideApplication
import com.example.rxaide.data.entity.DoseHistory
import java.util.Calendar

/**
 * Worker that back-fills dose-history rows for every (medication, schedule, day)
 * combination from the medication's start-date through today.
 *
 * This ensures that even if the app was not running and missed a WorkManager
 * reminder, the dose records still exist so the user can mark them later.
 */
class DoseGenerationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DoseGenerationWorker"
        private const val UNIQUE_WORK_NAME = "dose_generation"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<DoseGenerationWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as RxAideApplication
            val repo = app.repository

            val medications = repo.getActiveMedicationsOnce()
            val today = Calendar.getInstance()
            // Set end of today
            val endOfToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            for (med in medications) {
                val schedules = repo.getSchedulesForMedicationOnce(med.id)
                if (schedules.isEmpty()) continue

                // Start from the medication's start date
                val startCal = Calendar.getInstance().apply {
                    timeInMillis = med.startDate
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // End date is min(endDate, today)
                val effectiveEnd = if (med.endDate != null && med.endDate < endOfToday) {
                    med.endDate
                } else {
                    endOfToday
                }

                val dayCal = startCal.clone() as Calendar

                while (dayCal.timeInMillis <= effectiveEnd) {
                    val dayStart = dayCal.timeInMillis
                    val dayEnd = Calendar.getInstance().apply {
                        timeInMillis = dayStart
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.timeInMillis

                    val dayOfWeek = dayCal.get(Calendar.DAY_OF_WEEK)
                    // Convert Calendar.DAY_OF_WEEK (1=Sun, 7=Sat) to our format (1=Mon, 7=Sun)
                    val ourDay = when (dayOfWeek) {
                        Calendar.MONDAY -> 1
                        Calendar.TUESDAY -> 2
                        Calendar.WEDNESDAY -> 3
                        Calendar.THURSDAY -> 4
                        Calendar.FRIDAY -> 5
                        Calendar.SATURDAY -> 6
                        Calendar.SUNDAY -> 7
                        else -> 0
                    }

                    for (schedule in schedules) {
                        if (!schedule.isEnabled) continue

                        // Check if this schedule applies to this day of week
                        val scheduleDays = schedule.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
                        if (ourDay !in scheduleDays) continue

                        // Check if a dose already exists for this schedule on this day
                        val existingCount = repo.getDoseCountForScheduleOnDay(
                            med.id, schedule.id, dayStart, dayEnd
                        )
                        if (existingCount > 0) continue

                        // Calculate the scheduled time for this dose
                        val scheduledTime = Calendar.getInstance().apply {
                            timeInMillis = dayStart
                            set(Calendar.HOUR_OF_DAY, schedule.timeHour)
                            set(Calendar.MINUTE, schedule.timeMinute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis

                        // Only create if the scheduled time is in the past or now
                        if (scheduledTime <= System.currentTimeMillis()) {
                            repo.insertDoseHistory(
                                DoseHistory(
                                    medicationId = med.id,
                                    scheduleId = schedule.id,
                                    status = "unmarked",
                                    scheduledTime = scheduledTime
                                )
                            )
                        }
                    }

                    dayCal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            Log.d(TAG, "Dose generation completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Dose generation failed", e)
            Result.retry()
        }
    }
}

package com.example.rxaide.notification

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.rxaide.data.entity.Medication
import com.example.rxaide.data.entity.Schedule
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules (and cancels) periodic WorkManager tasks for medication reminders.
 */
object ReminderScheduler {

    private const val WORK_TAG_PREFIX = "med_reminder_"

    /**
     * Schedules a daily repeating reminder for a single schedule entry.
     *
     * @param soundUri Optional custom notification sound URI string.
     */
    fun scheduleReminder(
        context: Context,
        medication: Medication,
        schedule: Schedule,
        soundUri: String? = null
    ) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, schedule.timeHour)
            set(Calendar.MINUTE, schedule.timeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If the target time already passed today, schedule for tomorrow
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelayMs = target.timeInMillis - now.timeInMillis

        val data = Data.Builder()
            .putLong(ReminderWorker.KEY_MEDICATION_ID, medication.id)
            .putString(ReminderWorker.KEY_MEDICATION_NAME, medication.name)
            .putString(ReminderWorker.KEY_DOSAGE, medication.dosage)
            .putString(ReminderWorker.KEY_DOSAGE_UNIT, medication.dosageUnit)
            .putLong(ReminderWorker.KEY_SCHEDULE_ID, schedule.id)
            .apply {
                if (!soundUri.isNullOrBlank()) {
                    putString(ReminderWorker.KEY_SOUND_URI, soundUri)
                }
            }
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(workTag(medication.id, schedule.id))
            .addTag(medicationTag(medication.id))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workTag(medication.id, schedule.id),
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * Schedules reminders for all schedules of a medication.
     */
    fun scheduleAllReminders(
        context: Context,
        medication: Medication,
        schedules: List<Schedule>,
        soundUri: String? = null
    ) {
        schedules.forEach { schedule ->
            scheduleReminder(context, medication, schedule, soundUri)
        }
    }

    /**
     * Cancels all reminders for a given medication.
     */
    fun cancelRemindersForMedication(context: Context, medicationId: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag(medicationTag(medicationId))
    }

    /**
     * Cancels a single schedule reminder.
     */
    fun cancelReminder(context: Context, medicationId: Long, scheduleId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workTag(medicationId, scheduleId))
    }

    private fun workTag(medicationId: Long, scheduleId: Long) =
        "${WORK_TAG_PREFIX}${medicationId}_$scheduleId"

    private fun medicationTag(medicationId: Long) =
        "${WORK_TAG_PREFIX}med_$medicationId"
}

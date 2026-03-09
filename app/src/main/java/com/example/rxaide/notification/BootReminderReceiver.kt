package com.example.rxaide.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.rxaide.RxAideApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-schedules all active medication reminders after the device reboots.
 * WorkManager persists its work across reboots, but this ensures
 * correct timing is re-calculated.
 */
class BootReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as RxAideApplication
        CoroutineScope(Dispatchers.IO).launch {
            val medications = app.repository.activeMedications.first()
            for (medication in medications) {
                val schedules = app.repository.getSchedulesForMedication(medication.id).first()
                ReminderScheduler.scheduleAllReminders(
                    context,
                    medication,
                    schedules,
                    medication.notificationSoundUri
                )
            }
        }
    }
}

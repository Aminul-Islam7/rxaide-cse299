package com.example.rxaide.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.rxaide.RxAideApplication
import com.example.rxaide.data.entity.DoseHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles notification action buttons ("Taken" / "Missed").
 * Updates the DoseHistory status from "unmarked" to "taken" or "missed".
 */
class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TAKEN = "com.example.rxaide.ACTION_DOSE_TAKEN"
        const val ACTION_MISSED = "com.example.rxaide.ACTION_DOSE_MISSED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(ReminderWorker.KEY_MEDICATION_ID, -1)
        val scheduleId = intent.getLongExtra(ReminderWorker.KEY_SCHEDULE_ID, -1)
        if (medicationId == -1L) return

        val status = when (intent.action) {
            ACTION_TAKEN -> "taken"
            ACTION_MISSED -> "missed"
            else -> return
        }

        // Stop any playing notification sound
        NotificationSoundPlayer.stop()

        // Dismiss the notification
        val notificationId = (medicationId * 100 + scheduleId).toInt()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)

        // Update the most recent unmarked dose for this medication to the new status.
        // If no unmarked row exists (e.g., legacy data), create a direct status record.
        val app = context.applicationContext as RxAideApplication
        CoroutineScope(Dispatchers.IO).launch {
            val updatedRows = app.repository.updateLatestUnmarkedDoseStatus(
                medicationId = medicationId,
                scheduleId = scheduleId.takeIf { it != -1L },
                newStatus = status
            )

            if (updatedRows == 0) {
                val now = System.currentTimeMillis()
                app.repository.insertDoseHistory(
                    DoseHistory(
                        medicationId = medicationId,
                        scheduleId = scheduleId.takeIf { it != -1L },
                        status = status,
                        scheduledTime = now,
                        actionTime = now
                    )
                )
            }
        }
    }
}

package com.example.rxaide.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * Creates the single notification channel used by medication reminders.
 * Safe to call multiple times—Android ignores duplicate channel creation.
 */
object RxAideNotificationChannel {

    const val CHANNEL_ID = "rxaide_medication_reminders"
    private const val CHANNEL_NAME = "Medication Reminders"
    private const val CHANNEL_DESC = "Notifications to remind you to take your medications on time"

    fun create(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // If the channel already exists with a non-silent sound (from a
        // previous app version), delete it so it gets recreated silently.
        manager.getNotificationChannel(CHANNEL_ID)?.let { existing ->
            if (existing.sound != null) {
                manager.deleteNotificationChannel(CHANNEL_ID)
            }
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESC
            enableVibration(true)
            // Channel is intentionally SILENT — custom sounds are played
            // programmatically by NotificationSoundPlayer so that each
            // medication can have its own ringtone.
            setSound(null, null)
        }

        manager.createNotificationChannel(channel)
    }
}

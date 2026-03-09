package com.example.rxaide.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rxaide.MainActivity
import com.example.rxaide.R

/**
 * WorkManager worker that fires a medication-reminder notification.
 * Supports a custom notification sound URI chosen by the user.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MEDICATION_ID = "medication_id"
        const val KEY_MEDICATION_NAME = "medication_name"
        const val KEY_DOSAGE = "dosage"
        const val KEY_DOSAGE_UNIT = "dosage_unit"
        const val KEY_SCHEDULE_ID = "schedule_id"
        const val KEY_SOUND_URI = "sound_uri"
    }

    override suspend fun doWork(): Result {
        val medicationId = inputData.getLong(KEY_MEDICATION_ID, -1)
        val name = inputData.getString(KEY_MEDICATION_NAME) ?: "Medication"
        val dosage = inputData.getString(KEY_DOSAGE) ?: ""
        val dosageUnit = inputData.getString(KEY_DOSAGE_UNIT) ?: ""
        val scheduleId = inputData.getLong(KEY_SCHEDULE_ID, -1)
        val soundUriString = inputData.getString(KEY_SOUND_URI)

        if (medicationId == -1L) return Result.failure()

        showNotification(medicationId, scheduleId, name, dosage, dosageUnit, soundUriString)
        return Result.success()
    }

    private fun showNotification(
        medicationId: Long,
        scheduleId: Long,
        name: String,
        dosage: String,
        dosageUnit: String,
        soundUriString: String?
    ) {
        // Check POST_NOTIFICATIONS permission (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val notificationId = (medicationId * 100 + scheduleId).toInt()

        // Tap → open app
        val tapIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(KEY_MEDICATION_ID, medicationId)
        }
        val tapPending = PendingIntent.getActivity(
            applicationContext, notificationId, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Taken" action
        val takenIntent = Intent(applicationContext, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_TAKEN
            putExtra(KEY_MEDICATION_ID, medicationId)
            putExtra(KEY_SCHEDULE_ID, scheduleId)
        }
        val takenPending = PendingIntent.getBroadcast(
            applicationContext, notificationId + 1000, takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Missed" action
        val missedIntent = Intent(applicationContext, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_MISSED
            putExtra(KEY_MEDICATION_ID, medicationId)
            putExtra(KEY_SCHEDULE_ID, scheduleId)
        }
        val missedPending = PendingIntent.getBroadcast(
            applicationContext, notificationId + 2000, missedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dosageText = if (dosage.isNotBlank()) " — $dosage $dosageUnit" else ""

        val builder = NotificationCompat.Builder(applicationContext, RxAideNotificationChannel.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("💊 Time to take $name")
            .setContentText("Take your dose$dosageText")
            .setStyle(NotificationCompat.BigTextStyle().bigText("It's time to take $name$dosageText. Tap to open the app or use the actions below."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .addAction(R.drawable.ic_launcher_foreground, "✅ Taken", takenPending)
            .addAction(R.drawable.ic_launcher_foreground, "❌ Missed", missedPending)
            // Channel is silent; sound is played programmatically below
            .setSilent(true)

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())

        // Play the custom (or default) notification sound via MediaPlayer
        // because Android 8+ ignores per-notification setSound() in favour
        // of the channel configuration.
        val soundUri = if (!soundUriString.isNullOrBlank()) Uri.parse(soundUriString) else null
        NotificationSoundPlayer.play(applicationContext, soundUri)
    }
}

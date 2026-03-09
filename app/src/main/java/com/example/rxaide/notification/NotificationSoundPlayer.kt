package com.example.rxaide.notification

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log

/**
 * Singleton that plays and stops custom notification sounds.
 *
 * On Android 8.0+ the notification channel controls the sound, so
 * per-notification `setSound()` is ignored.  Instead the channel is
 * kept silent and this player handles the audio programmatically.
 *
 * Call [play] from [ReminderWorker] after posting the notification,
 * and [stop] from [ReminderActionReceiver] when the user acts.
 */
object NotificationSoundPlayer {

    private const val TAG = "NotifSoundPlayer"
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Plays the given [soundUri].
     * If [soundUri] is null the system default notification sound is used.
     * Any previously playing sound is stopped first.
     */
    @Synchronized
    fun play(context: Context, soundUri: Uri?) {
        stop()                                           // release any active player

        val uri = soundUri
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = false
                setOnCompletionListener { stop() }       // auto-cleanup
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error: what=$what extra=$extra")
                    stop()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not play sound $uri", e)
            stop()                                       // clean up on failure
        }
    }

    /** Stops any currently playing notification sound. */
    @Synchronized
    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) { /* already released */ }
        mediaPlayer = null
    }
}

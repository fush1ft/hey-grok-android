package cz.fushift.heygrok

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService

object Notifications {
    const val LISTENING_ID = 1
    private const val WAKE_ID = 2
    private const val STARTUP_ID = 3

    private const val CHANNEL_LISTENING = "listening"
    private const val CHANNEL_WAKE = "wake_word"
    private const val CHANNEL_STARTUP = "startup"

    fun listening(context: Context, phrase: WakePhrase): Notification {
        val stop = PendingIntent.getService(
            context,
            0,
            Intent(context, WakeWordService::class.java).setAction(WakeWordService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return builder(context, CHANNEL_LISTENING)
            .setContentText("Listening for \"${phrase.label}\"")
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .addAction(0, "Stop", stop)
            .build()
    }

    fun updateListening(context: Context, phrase: WakePhrase) {
        post(context, LISTENING_ID, listening(context, phrase))
    }

    fun canUseFullScreenIntent(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 34 || context.getSystemService<NotificationManager>()!!.canUseFullScreenIntent())

    fun showOpenGrok(context: Context) {
        post(
            context,
            WAKE_ID,
            builder(context, CHANNEL_WAKE)
                .setContentText("Heard the wake word, tap to open Grok")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(unlock(context))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun showUnlockFullScreen(context: Context) {
        val unlock = unlock(context)
        post(
            context,
            WAKE_ID,
            builder(context, CHANNEL_WAKE)
                .setContentText("Heard the wake word, unlock to open Grok")
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(unlock, true)
                .setContentIntent(unlock)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun cancelWake(context: Context) {
        NotificationManagerCompat.from(context).cancel(WAKE_ID)
    }

    fun showGrokMissing(context: Context) {
        post(
            context,
            WAKE_ID,
            builder(context, CHANNEL_WAKE)
                .setContentText("Grok Bot is not installed")
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun showStartPrompt(context: Context) {
        // Services started from a notification tap are exempt from the while-in-use mic restriction.
        val start = PendingIntent.getForegroundService(
            context,
            0,
            Intent(context, WakeWordService::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            context,
            STARTUP_ID,
            builder(context, CHANNEL_STARTUP)
                .setContentText("Tap to start listening")
                .setContentIntent(start)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun builder(context: Context, channel: String): NotificationCompat.Builder {
        createChannels(context)
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
    }

    private fun createChannels(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(
            listOf(
                channel(CHANNEL_LISTENING, "Listening", NotificationManagerCompat.IMPORTANCE_LOW),
                channel(CHANNEL_WAKE, "Wake word", NotificationManagerCompat.IMPORTANCE_HIGH),
                channel(CHANNEL_STARTUP, "Startup", NotificationManagerCompat.IMPORTANCE_DEFAULT),
            ),
        )
    }

    private fun channel(id: String, name: String, importance: Int) =
        NotificationChannelCompat.Builder(id, importance).setName(name).build()

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun unlock(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        UnlockActivity.intent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // areNotificationsEnabled() is false when POST_NOTIFICATIONS is denied.
    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) manager.notify(id, notification)
    }
}

package com.soumya.voicepilot.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.soumya.voicepilot.R
import com.soumya.voicepilot.VoicePilotApp
import com.soumya.voicepilot.ui.MainActivity
import com.soumya.voicepilot.util.Prefs

/**
 * Restarts the listener after a reboot — when the system allows it.
 *
 * Android 14 onwards refuses to let BOOT_COMPLETED start a foreground service of
 * type microphone. That restriction is deliberate and there is no way around it,
 * so when the start is rejected we post a notification you can tap instead of
 * failing silently. On Infinix XOS you also need VoicePilot allowed in
 * auto-start management, or this receiver never runs at all.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        if (!Prefs(context).serviceEnabled) return

        runCatching { VoicePilotService.start(context) }
            .onFailure { failure ->
                Log.i(TAG, "Boot start refused by the platform", failure)
                postTapToStart(context)
            }
    }

    private fun postTapToStart(context: Context) {
        val pending = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, VoicePilotApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_voice)
            .setContentTitle(context.getString(R.string.boot_notification_title))
            .setContentText(context.getString(R.string.boot_notification_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.boot_notification_text)),
            )
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val TAG = "BootReceiver"
        const val NOTIFICATION_ID = 4202
    }
}

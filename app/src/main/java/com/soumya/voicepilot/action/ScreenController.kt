package com.soumya.voicepilot.action

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.soumya.voicepilot.R
import com.soumya.voicepilot.VoicePilotApp
import com.soumya.voicepilot.ui.WakeActivity

/**
 * Turns the display on and puts the wake screen in front of the keyguard.
 *
 * Android 10 onwards blocks background activity starts, and a microphone
 * foreground service does not earn an exemption. Two routes work, so we take
 * both: the "display over other apps" permission (which does grant background
 * starts) and a full-screen-intent notification as the backup. On Android 14+
 * the full-screen intent permission is itself gated, which is why the setup
 * screen asks for both.
 */
class ScreenController(context: Context) {

    private val appContext = context.applicationContext
    private val power = appContext.getSystemService(PowerManager::class.java)

    fun wakeAndShow() {
        acquireBriefWakeLock()

        val intent = Intent(appContext, WakeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        if (canStartActivityFromBackground()) {
            runCatching { appContext.startActivity(intent) }
                .onFailure {
                    Log.w(TAG, "Direct start refused, using full-screen intent", it)
                    postFullScreenIntent(intent)
                }
        } else {
            postFullScreenIntent(intent)
        }
    }

    /**
     * SCREEN_BRIGHT_WAKE_LOCK is deprecated but is still the only way to light up
     * a display that is already off before an activity exists. The timed acquire
     * means it releases itself even if the activity never comes up.
     */
    private fun acquireBriefWakeLock() {
        runCatching {
            @Suppress("DEPRECATION")
            val lock = power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "voicepilot:wake",
            )
            lock.acquire(WAKE_LOCK_MS)
        }.onFailure { Log.w(TAG, "Wake lock failed", it) }
    }

    private fun canStartActivityFromBackground(): Boolean =
        Settings.canDrawOverlays(appContext)

    private fun postFullScreenIntent(intent: Intent) {
        val pending = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(appContext, VoicePilotApp.CHANNEL_WAKE)
            .setSmallIcon(R.drawable.ic_stat_voice)
            .setContentTitle(appContext.getString(R.string.wake_notification_title))
            .setContentText(appContext.getString(R.string.wake_notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .build()

        val manager = NotificationManagerCompat.from(appContext)
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled; cannot raise the wake screen")
            return
        }
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { Log.w(TAG, "Full-screen intent refused", it) }
    }

    /** True when the screen can actually be raised; the setup screen surfaces this. */
    fun canRaiseScreen(): Boolean =
        Settings.canDrawOverlays(appContext) || hasFullScreenIntentPermission()

    private fun hasFullScreenIntentPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = appContext.getSystemService(android.app.NotificationManager::class.java)
        return manager.canUseFullScreenIntent()
    }

    private companion object {
        const val TAG = "ScreenController"
        const val WAKE_LOCK_MS = 15_000L
        const val NOTIFICATION_ID = 4201
        const val REQUEST_CODE = 42
    }
}

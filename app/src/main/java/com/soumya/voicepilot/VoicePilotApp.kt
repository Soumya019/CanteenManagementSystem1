package com.soumya.voicepilot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class VoicePilotApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                getString(R.string.channel_service),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.channel_service_description)
                setShowBadge(false)
            },
        )

        // High importance so the full-screen intent is honoured when the overlay
        // permission is missing and we still need to light up the display.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WAKE,
                getString(R.string.channel_wake),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.channel_wake_description)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    companion object {
        const val CHANNEL_SERVICE = "voicepilot.service"
        const val CHANNEL_WAKE = "voicepilot.wake"
    }
}

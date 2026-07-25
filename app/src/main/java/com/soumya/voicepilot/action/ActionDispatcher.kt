package com.soumya.voicepilot.action

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.view.KeyEvent
import com.soumya.voicepilot.R
import com.soumya.voicepilot.VoicePilotBus
import com.soumya.voicepilot.intent.CommandMatch
import com.soumya.voicepilot.intent.CommandRegistry
import com.soumya.voicepilot.intent.TextSimilarity
import java.util.Date

/**
 * Runs a matched command and returns a short line to speak and show.
 *
 * Adding a command means an entry in [CommandRegistry] and a branch here.
 */
class ActionDispatcher(
    context: Context,
    private val screen: ScreenController,
) {

    private val appContext = context.applicationContext

    fun dispatch(match: CommandMatch): String = when (match.id) {
        CommandRegistry.WAKE_SCREEN -> {
            screen.wakeAndShow()
            appContext.getString(R.string.result_awake)
        }

        CommandRegistry.UNLOCK -> {
            // The activity owns this; see VoicePilotBus.
            VoicePilotBus.emit(VoicePilotBus.Event.RequestUnlock)
            appContext.getString(R.string.result_unlocking)
        }

        CommandRegistry.TIME -> currentTime()
        CommandRegistry.TORCH_ON -> torch(on = true)
        CommandRegistry.TORCH_OFF -> torch(on = false)

        CommandRegistry.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE, R.string.result_louder)
        CommandRegistry.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER, R.string.result_quieter)
        CommandRegistry.MUTE -> adjustVolume(AudioManager.ADJUST_MUTE, R.string.result_muted)

        CommandRegistry.MEDIA_TOGGLE -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        CommandRegistry.MEDIA_NEXT -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        CommandRegistry.MEDIA_PREVIOUS -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

        CommandRegistry.WIFI_SETTINGS -> launch(Intent(Settings.Panel.ACTION_WIFI))
        CommandRegistry.BLUETOOTH_SETTINGS -> launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        CommandRegistry.SETTINGS -> launch(Intent(Settings.ACTION_SETTINGS))

        CommandRegistry.OPEN_APP -> openApp(match.argument)
        CommandRegistry.DIAL -> dial(match.argument)

        CommandRegistry.CANCEL -> appContext.getString(R.string.result_cancelled)

        else -> appContext.getString(R.string.result_unknown)
    }

    private fun currentTime(): String {
        val formatted = DateFormat.getTimeFormat(appContext).format(Date())
        return appContext.getString(R.string.result_time, formatted)
    }

    private fun torch(on: Boolean): String {
        val manager = appContext.getSystemService(CameraManager::class.java)
        return runCatching {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return appContext.getString(R.string.result_no_torch)

            manager.setTorchMode(cameraId, on)
            appContext.getString(if (on) R.string.result_torch_on else R.string.result_torch_off)
        }.getOrElse {
            Log.w(TAG, "Torch failed", it)
            appContext.getString(R.string.result_no_torch)
        }
    }

    private fun adjustVolume(direction: Int, messageRes: Int): String {
        val audio = appContext.getSystemService(AudioManager::class.java)
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return appContext.getString(messageRes)
    }

    private fun mediaKey(keyCode: Int): String {
        val audio = appContext.getSystemService(AudioManager::class.java)
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        return appContext.getString(R.string.result_done)
    }

    /**
     * Resolves a spoken label against installed apps by similarity, so "open
     * whatsapp" finds "WhatsApp" and "open you tube" finds "YouTube".
     */
    private fun openApp(spokenLabel: String?): String {
        if (spokenLabel.isNullOrBlank()) {
            return appContext.getString(R.string.result_which_app)
        }
        val packageManager = appContext.packageManager
        val target = packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .mapNotNull { info -> scoreApp(packageManager, info, spokenLabel) }
            .maxByOrNull { it.second }
            ?.first

        if (target == null) {
            return appContext.getString(R.string.result_app_not_found, spokenLabel)
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(target.packageName)
            ?: return appContext.getString(R.string.result_app_not_launchable, spokenLabel)

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(launchIntent)
        return appContext.getString(
            R.string.result_opening,
            packageManager.getApplicationLabel(target).toString(),
        )
    }

    private fun scoreApp(
        packageManager: PackageManager,
        info: ApplicationInfo,
        spokenLabel: String,
    ): Pair<ApplicationInfo, Double>? {
        val label = TextSimilarity.normalize(
            packageManager.getApplicationLabel(info).toString(),
        )
        if (label.isEmpty()) return null
        // Collapse spaces too: the recognizer writes "you tube", the label is "youtube".
        val score = maxOf(
            TextSimilarity.similarity(label, spokenLabel),
            TextSimilarity.similarity(label.replace(" ", ""), spokenLabel.replace(" ", "")),
        )
        return if (score >= APP_MATCH_THRESHOLD) info to score else null
    }

    /**
     * Digits only, and via ACTION_DIAL rather than ACTION_CALL — placing a call
     * outright would need the CALL_PHONE permission, and a voice trigger that can
     * dial without confirmation is not worth that.
     */
    private fun dial(spokenNumber: String?): String {
        val digits = spokenNumber?.filter { it.isDigit() || it == '+' }.orEmpty()
        if (digits.length < MIN_DIAL_DIGITS) {
            return appContext.getString(R.string.result_no_number)
        }
        return launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")))
    }

    private fun launch(intent: Intent): String {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            appContext.startActivity(intent)
            appContext.getString(R.string.result_done)
        }.getOrElse {
            Log.w(TAG, "Could not start $intent", it)
            appContext.getString(R.string.result_failed)
        }
    }

    private companion object {
        const val TAG = "ActionDispatcher"
        const val APP_MATCH_THRESHOLD = 0.6
        const val MIN_DIAL_DIGITS = 3
    }
}

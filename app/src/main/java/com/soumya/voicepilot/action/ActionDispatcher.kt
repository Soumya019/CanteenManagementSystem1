package com.soumya.voicepilot.action

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.view.KeyEvent
import com.soumya.voicepilot.R
import com.soumya.voicepilot.VoicePilotBus
import com.soumya.voicepilot.intent.CommandMatch
import com.soumya.voicepilot.intent.CommandRegistry
import com.soumya.voicepilot.intent.MediaQuery
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
        CommandRegistry.PLAY_MEDIA -> playMedia(match.argument)

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
        // The spoken name arrives with only its leading fillers stripped, since
        // it may be a search query; for an app name the rest can go too.
        val spoken = TextSimilarity.normalize(spokenLabel)
        if (label.isEmpty() || spoken.isEmpty()) return null

        // Collapse spaces too: the recognizer writes "you tube", the label is "youtube".
        val score = maxOf(
            TextSimilarity.similarity(label, spoken),
            TextSimilarity.similarity(label.replace(" ", ""), spoken.replace(" ", "")),
        )
        return if (score >= APP_MATCH_THRESHOLD) info to score else null
    }

    /**
     * "play <something> on <app>".
     *
     * Whether the app starts playing or merely lands on search results is the
     * app's choice, not ours. Spotify and YouTube Music honour play-from-search
     * and begin playback; the main YouTube app generally shows results and waits
     * for a tap. So we try the routes in descending order of how directly they
     * play, and stop at the first one that sticks.
     */
    private fun playMedia(spoken: String?): String {
        if (spoken.isNullOrBlank()) return appContext.getString(R.string.result_what_to_play)

        val request = MediaQuery.parse(spoken)
        val target = request.appHint?.let { hint ->
            MediaApps.match(hint).firstOrNull { isInstalled(it.packageName) }
                ?: installedAppByLabel(hint)?.let { MediaApp(it, listOf(hint), null) }
        }

        // The words after "on" were not an app after all, so they belong to the
        // query: "play turn it on on repeat" searches for the whole phrase.
        val query = if (request.appHint != null && target == null) spoken else request.query

        if (target != null) {
            val label = appLabel(target.packageName)

            if (playFromSearch(query, target.packageName)) {
                return appContext.getString(R.string.result_playing_on, query, label)
            }
            if (searchInside(query, target.packageName)) {
                return appContext.getString(R.string.result_searching_on, query, label)
            }
            target.searchUriTemplate?.let { template ->
                if (openSearchUri(template, query, target.packageName)) {
                    return appContext.getString(R.string.result_searching_on, query, label)
                }
            }
            // Nothing took the query; at least put the app in front of them.
            if (launchPackage(target.packageName)) {
                return appContext.getString(R.string.result_opening, label)
            }
            return appContext.getString(R.string.result_app_not_launchable, label)
        }

        // No app named: let the system route it to whatever handles media search.
        if (playFromSearch(query, null)) {
            return appContext.getString(R.string.result_playing, query)
        }
        return appContext.getString(R.string.result_no_media_app)
    }

    /** The framework's "play this" intent. The most direct route when honoured. */
    private fun playFromSearch(query: String, packageName: String?): Boolean {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MEDIA_FOCUS_ANY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (packageName != null) setPackage(packageName)
        }
        return start(intent)
    }

    /** In-app search, for apps that take a query but not play-from-search. */
    private fun searchInside(query: String, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage(packageName)
            putExtra(SearchManager.QUERY, query)
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(intent)
    }

    /** Deep link, e.g. spotify:search:… — the last route that carries the query. */
    private fun openSearchUri(template: String, query: String, packageName: String): Boolean {
        val uri = Uri.parse(template.format(Uri.encode(query)))
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(intent)
    }

    private fun launchPackage(packageName: String): Boolean {
        val intent = appContext.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(intent)
    }

    private fun start(intent: Intent): Boolean =
        runCatching {
            appContext.startActivity(intent)
            true
        }.getOrElse {
            Log.i(TAG, "No handler for $intent", it)
            false
        }

    private fun isInstalled(packageName: String): Boolean =
        appContext.packageManager.getLaunchIntentForPackage(packageName) != null

    private fun appLabel(packageName: String): String = runCatching {
        val packageManager = appContext.packageManager
        packageManager
            .getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
            .toString()
    }.getOrDefault(packageName)

    /** Falls back to any installed app whose label sounds like the spoken name. */
    private fun installedAppByLabel(spokenName: String): String? {
        val packageManager = appContext.packageManager
        return packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .mapNotNull { info -> scoreApp(packageManager, info, spokenName) }
            .maxByOrNull { it.second }
            ?.first
            ?.packageName
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

        /** "any media type" for EXTRA_MEDIA_FOCUS — track, album, artist alike. */
        const val MEDIA_FOCUS_ANY = "vnd.android.cursor.item/*"
    }
}

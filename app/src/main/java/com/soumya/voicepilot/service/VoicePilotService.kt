package com.soumya.voicepilot.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.soumya.voicepilot.BuildConfig
import com.soumya.voicepilot.R
import com.soumya.voicepilot.VoicePilotApp
import com.soumya.voicepilot.VoicePilotBus
import com.soumya.voicepilot.action.ActionDispatcher
import com.soumya.voicepilot.action.ScreenController
import com.soumya.voicepilot.intent.CommandMatch
import com.soumya.voicepilot.intent.CommandRegistry
import com.soumya.voicepilot.intent.IntentMatcher
import com.soumya.voicepilot.speech.CommandRecognizer
import com.soumya.voicepilot.speech.Speaker
import com.soumya.voicepilot.ui.MainActivity
import com.soumya.voicepilot.util.Prefs
import com.soumya.voicepilot.wake.PorcupineEngine
import com.soumya.voicepilot.wake.SpeechLoopEngine
import com.soumya.voicepilot.wake.WakeWordEngine

/**
 * The always-on listener.
 *
 * One turn looks like: wake word fires, we release the mic from the wake engine,
 * light up the screen, beep, dictate one command, run it, then hand the mic back.
 * Only one component may hold the microphone at a time, which is why the
 * handover is explicit at every step.
 */
class VoicePilotService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private val matcher = IntentMatcher()

    private lateinit var prefs: Prefs
    private lateinit var speaker: Speaker
    private lateinit var screen: ScreenController
    private lateinit var dispatcher: ActionDispatcher
    private lateinit var commandRecognizer: CommandRecognizer

    private var wakeEngine: WakeWordEngine? = null

    @Volatile
    private var inTurn = false

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        speaker = Speaker(this)
        screen = ScreenController(this)
        dispatcher = ActionDispatcher(this, screen)
        commandRecognizer = CommandRecognizer(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.serviceEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        if (!hasMicrophonePermission()) {
            // Starting a microphone foreground service without the permission is
            // a hard SecurityException on Android 14+, so bail out cleanly.
            Log.w(TAG, "RECORD_AUDIO not granted; not starting")
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundService(getString(R.string.notification_starting))
        prefs.serviceEnabled = true
        startWakeEngine()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        wakeEngine?.release()
        wakeEngine = null
        commandRecognizer.release()
        speaker.shutdown()
        super.onDestroy()
    }

    // --- wake engine ------------------------------------------------------

    private fun startWakeEngine() {
        stopWakeEngine()

        val engine = preferredEngine()
        wakeEngine = engine
        runCatching { engine.start() }
            .onFailure { failure ->
                Log.w(TAG, "${engine.displayName} failed to start; falling back", failure)
                engine.release()
                val fallback = speechEngine()
                wakeEngine = fallback
                runCatching { fallback.start() }.onFailure { fatal ->
                    Log.e(TAG, "No wake engine could start", fatal)
                    updateNotification(getString(R.string.notification_engine_failed))
                    return
                }
            }
        updateNotification(getString(R.string.notification_listening, wakeEngine?.displayName.orEmpty()))
    }

    private fun preferredEngine(): WakeWordEngine {
        val accessKey = BuildConfig.PICOVOICE_ACCESS_KEY
        val wantsPorcupine = when (prefs.engine) {
            Prefs.ENGINE_PORCUPINE -> true
            Prefs.ENGINE_SPEECH -> false
            else -> accessKey.isNotBlank()
        }
        return if (wantsPorcupine && accessKey.isNotBlank()) {
            PorcupineEngine(this, accessKey, ::onWake)
        } else {
            speechEngine()
        }
    }

    private fun speechEngine(): WakeWordEngine =
        SpeechLoopEngine(this, prefs.wakePhrases, ::onWake)

    private fun stopWakeEngine() {
        wakeEngine?.release()
        wakeEngine = null
    }

    // --- one turn ---------------------------------------------------------

    private fun onWake(label: String) {
        if (inTurn) return
        inTurn = true

        main.post {
            stopWakeEngine()
            screen.wakeAndShow()
            speaker.beep()
            VoicePilotBus.emit(VoicePilotBus.Event.Listening)
            updateNotification(getString(R.string.notification_heard, label))

            if (prefs.autoUnlockOnWake) {
                VoicePilotBus.emit(VoicePilotBus.Event.RequestUnlock)
            }

            // Let the beep finish before the recognizer opens the mic, or it
            // transcribes our own tone.
            main.postDelayed(::listenForCommand, LISTEN_DELAY_MS)
        }
    }

    private fun listenForCommand() {
        if (!commandRecognizer.isAvailable()) {
            finishTurn(null)
            return
        }
        commandRecognizer.listenOnce(
            onResult = { candidates -> finishTurn(bestMatch(candidates)) },
            onError = { finishTurn(null) },
        )
    }

    /** Scores every n-best alternative and keeps the strongest. */
    private fun bestMatch(candidates: List<String>): CommandMatch? =
        candidates.asSequence()
            .mapNotNull(matcher::match)
            .maxByOrNull { it.score }

    private fun finishTurn(match: CommandMatch?) {
        val message = if (match == null) {
            // The wake word alone is a valid request: screen on, time showing.
            getString(R.string.result_awake)
        } else {
            dispatcher.dispatch(match).also {
                if (match.id == CommandRegistry.CANCEL) {
                    VoicePilotBus.emit(VoicePilotBus.Event.Dismiss)
                }
            }
        }

        VoicePilotBus.emit(VoicePilotBus.Event.Status(message))
        if (prefs.speakConfirmations) speaker.say(message)

        // Resume after the confirmation has been spoken, otherwise the wake
        // engine hears our own voice and fires again.
        main.postDelayed({
            inTurn = false
            startWakeEngine()
        }, RESUME_DELAY_MS)
    }

    // --- foreground notification -----------------------------------------

    private fun startForegroundService(text: String) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(text),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification(text)) }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, VoicePilotService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, VoicePilotApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_voice)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.action_stop), stop)
            .build()
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "VoicePilotService"
        private const val NOTIFICATION_ID = 4200
        private const val LISTEN_DELAY_MS = 350L
        private const val RESUME_DELAY_MS = 1_800L

        const val ACTION_STOP = "com.soumya.voicepilot.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, VoicePilotService::class.java),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, VoicePilotService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}

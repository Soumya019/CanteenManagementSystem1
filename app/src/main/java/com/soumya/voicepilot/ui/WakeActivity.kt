package com.soumya.voicepilot.ui

import android.app.KeyguardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.soumya.voicepilot.R
import com.soumya.voicepilot.VoicePilotBus
import com.soumya.voicepilot.databinding.ActivityWakeBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What you see when the wake word fires: the clock, in front of the keyguard.
 *
 * The unlock lives here because KeyguardManager.requestDismissKeyguard needs a
 * visible activity to work against. What it can and cannot do is worth being
 * clear about: it never types your PIN. If Extend Unlock has the device in a
 * trusted state — a paired watch, a trusted place — it dismisses silently and
 * you are in. Otherwise the system shows you the credential prompt, which is
 * Android refusing to be voice-bypassed, not a bug here.
 */
class WakeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWakeBinding
    private val ticker = Handler(Looper.getMainLooper())
    private val idleTimer = Handler(Looper.getMainLooper())

    private val clockFormat = SimpleDateFormat("h:mm", Locale.getDefault())
    private val amPmFormat = SimpleDateFormat("a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())

    private val tick = object : Runnable {
        override fun run() {
            renderClock()
            ticker.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        binding = ActivityWakeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.dismissButton.setOnClickListener { finish() }
        binding.unlockButton.setOnClickListener { requestUnlock() }

        lifecycleScope.launch {
            VoicePilotBus.events.collect(::onEvent)
        }
    }

    override fun onResume() {
        super.onResume()
        renderClock()
        ticker.post(tick)
        restartIdleTimer()
    }

    override fun onPause() {
        ticker.removeCallbacks(tick)
        idleTimer.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun onEvent(event: VoicePilotBus.Event) {
        restartIdleTimer()
        when (event) {
            VoicePilotBus.Event.RequestUnlock -> requestUnlock()
            VoicePilotBus.Event.Listening -> setStatus(getString(R.string.wake_listening))
            VoicePilotBus.Event.Dismiss -> finish()
            is VoicePilotBus.Event.Status -> setStatus(event.text)
        }
    }

    private fun requestUnlock() {
        val keyguard = getSystemService(KeyguardManager::class.java)

        if (!keyguard.isKeyguardLocked) {
            setStatus(getString(R.string.wake_already_unlocked))
            finishAfterDelay(SHORT_DISMISS_MS)
            return
        }

        setStatus(getString(R.string.wake_unlocking))
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    setStatus(getString(R.string.wake_unlocked))
                    finishAfterDelay(SHORT_DISMISS_MS)
                }

                override fun onDismissCancelled() {
                    setStatus(getString(R.string.wake_unlock_cancelled))
                }

                override fun onDismissError() {
                    // Reached when no credential prompt could even be shown.
                    setStatus(getString(R.string.wake_unlock_error))
                }
            },
        )
    }

    private fun renderClock() {
        val now = Date()
        binding.clockText.text = clockFormat.format(now)
        binding.amPmText.text = amPmFormat.format(now)
        binding.dateText.text = dateFormat.format(now)
    }

    private fun setStatus(text: String) {
        binding.statusText.text = text
    }

    private fun restartIdleTimer() {
        idleTimer.removeCallbacksAndMessages(null)
        idleTimer.postDelayed({ finish() }, IDLE_TIMEOUT_MS)
    }

    private fun finishAfterDelay(delayMillis: Long) {
        idleTimer.removeCallbacksAndMessages(null)
        idleTimer.postDelayed({ finish() }, delayMillis)
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val IDLE_TIMEOUT_MS = 25_000L
        const val SHORT_DISMISS_MS = 1_200L
    }
}

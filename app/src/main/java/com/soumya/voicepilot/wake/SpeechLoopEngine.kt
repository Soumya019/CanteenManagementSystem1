package com.soumya.voicepilot.wake

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.soumya.voicepilot.intent.TextSimilarity
import java.util.Locale

/**
 * Zero-setup fallback: runs the platform recognizer in a loop and fires when a
 * transcript is close enough to one of the configured wake phrases.
 *
 * No Picovoice account needed, and it accepts variations the way you actually
 * speak ("please gemini wake up" still matches "gemini"). The cost is real
 * though — it holds the mic continuously and drains the battery noticeably
 * faster than Porcupine. Good for trying this tonight, not for leaving on.
 */
class SpeechLoopEngine(
    context: Context,
    private val phrases: List<String>,
    private val onWake: (String) -> Unit,
) : WakeWordEngine {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val normalizedPhrases = phrases.map { TextSimilarity.normalizeLight(it) }

    private var recognizer: SpeechRecognizer? = null

    @Volatile
    private var running = false

    override val displayName: String
        get() = "SpeechRecognizer (${phrases.joinToString(", ")})"

    override fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            error("No speech recognition service is installed on this device")
        }
        running = true
        main.post { listen() }
    }

    override fun stop() {
        running = false
        main.post {
            recognizer?.let { instance ->
                runCatching { instance.cancel() }
                runCatching { instance.destroy() }
            }
            recognizer = null
        }
    }

    override fun release() = stop()

    private fun listen() {
        if (!running) return

        val instance = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = instance
        instance.setRecognitionListener(object : RecognitionListener {
            private var handled = false

            override fun onResults(results: Bundle?) {
                if (handled) return
                handled = true
                val candidates = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    .orEmpty()
                val hit = candidates.firstNotNullOfOrNull { matchedPhrase(it) }
                cleanUp()
                if (hit != null) {
                    // Caller stops us before taking the mic, so no restart here.
                    onWake(hit)
                } else {
                    restart(QUICK_RETRY_MS)
                }
            }

            override fun onError(error: Int) {
                if (handled) return
                handled = true
                cleanUp()
                // BUSY and CLIENT mean another consumer has the mic; back off
                // harder so we are not spinning against the system recognizer.
                val delay = when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT,
                    -> SLOW_RETRY_MS

                    else -> QUICK_RETRY_MS
                }
                restart(delay)
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        runCatching { instance.startListening(buildIntent()) }
            .onFailure {
                Log.w(TAG, "startListening failed", it)
                restart(SLOW_RETRY_MS)
            }
    }

    private fun cleanUp() {
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
    }

    private fun restart(delayMillis: Long) {
        if (!running) return
        main.postDelayed({ listen() }, delayMillis)
    }

    /** Substring first (the phrase is usually buried in a longer transcript), then fuzzy. */
    private fun matchedPhrase(transcript: String): String? {
        val heard = TextSimilarity.normalizeLight(transcript)
        if (heard.isEmpty()) return null
        return normalizedPhrases.firstOrNull { phrase ->
            phrase.isNotEmpty() &&
                (heard.contains(phrase) || TextSimilarity.similarity(heard, phrase) >= THRESHOLD)
        }
    }

    private fun buildIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    private companion object {
        const val TAG = "SpeechLoopEngine"
        const val THRESHOLD = 0.75
        const val MAX_RESULTS = 5
        const val QUICK_RETRY_MS = 300L
        const val SLOW_RETRY_MS = 1500L
    }
}

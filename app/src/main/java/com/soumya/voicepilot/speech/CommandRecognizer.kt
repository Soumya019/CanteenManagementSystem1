package com.soumya.voicepilot.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * One-shot dictation for the command that follows the wake word.
 *
 * SpeechRecognizer is main-thread-only, so everything is posted there. Results
 * come back as the full n-best list — the intent matcher scores all of them and
 * keeps the best, which rescues a lot of near-misses.
 */
class CommandRecognizer(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun listenOnce(
        onResult: (List<String>) -> Unit,
        onError: (Int) -> Unit,
    ) {
        main.post {
            release()
            val instance = SpeechRecognizer.createSpeechRecognizer(appContext)
            recognizer = instance
            instance.setRecognitionListener(object : RecognitionListener {
                private var delivered = false

                override fun onResults(results: Bundle?) {
                    if (delivered) return
                    delivered = true
                    val candidates = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    release()
                    onResult(candidates)
                }

                override fun onError(error: Int) {
                    if (delivered) return
                    delivered = true
                    release()
                    onError(error)
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            instance.startListening(buildIntent())
        }
    }

    fun release() {
        main.post {
            recognizer?.let { instance ->
                runCatching { instance.cancel() }
                runCatching { instance.destroy() }
            }
            recognizer = null
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
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            // Offline keeps it working with no signal and cuts about a second of
            // latency. Requires the language pack under Settings > Google > Voice.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                SILENCE_MS,
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                SILENCE_MS,
            )
        }

    private companion object {
        const val MAX_RESULTS = 5
        const val SILENCE_MS = 1200L
    }
}

package com.soumya.voicepilot.speech

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/** Short audible feedback: a beep when we start listening, speech when we are done. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private val engine = TextToSpeech(context.applicationContext, this)
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)

    @Volatile
    private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TextToSpeech unavailable, status=$status")
            return
        }
        val result = engine.setLanguage(Locale.getDefault())
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }
        ready = true
    }

    fun beep() {
        runCatching { tone.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS) }
    }

    fun say(text: String) {
        if (!ready || text.isBlank()) return
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun shutdown() {
        runCatching { engine.stop() }
        runCatching { engine.shutdown() }
        runCatching { tone.release() }
    }

    private companion object {
        const val TAG = "Speaker"
        const val TONE_VOLUME = 80
        const val BEEP_MS = 120
        const val UTTERANCE_ID = "voicepilot"
    }
}

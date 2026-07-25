package com.soumya.voicepilot.util

import android.content.Context

class Prefs(context: Context) {

    private val store = context.applicationContext
        .getSharedPreferences("voicepilot", Context.MODE_PRIVATE)

    /** Which wake engine to use: [ENGINE_AUTO], [ENGINE_PORCUPINE] or [ENGINE_SPEECH]. */
    var engine: String
        get() = store.getString(KEY_ENGINE, ENGINE_AUTO) ?: ENGINE_AUTO
        set(value) = store.edit().putString(KEY_ENGINE, value).apply()

    /**
     * Phrases the SpeechRecognizer fallback listens for. Porcupine ignores this —
     * it matches the .ppn models you trained instead.
     */
    var wakePhrases: List<String>
        get() = store.getString(KEY_WAKE_PHRASES, null)
            ?.split('|')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_WAKE_PHRASES
        set(value) = store.edit()
            .putString(KEY_WAKE_PHRASES, value.joinToString("|"))
            .apply()

    /** Try to dismiss the keyguard as soon as the wake word fires, without waiting for "unlock". */
    var autoUnlockOnWake: Boolean
        get() = store.getBoolean(KEY_AUTO_UNLOCK, false)
        set(value) = store.edit().putBoolean(KEY_AUTO_UNLOCK, value).apply()

    /** Speak a short confirmation after each command. */
    var speakConfirmations: Boolean
        get() = store.getBoolean(KEY_SPEAK, true)
        set(value) = store.edit().putBoolean(KEY_SPEAK, value).apply()

    /** Whether the user wants the listener running, so boot/restart can honour it. */
    var serviceEnabled: Boolean
        get() = store.getBoolean(KEY_ENABLED, false)
        set(value) = store.edit().putBoolean(KEY_ENABLED, value).apply()

    companion object {
        const val ENGINE_AUTO = "auto"
        const val ENGINE_PORCUPINE = "porcupine"
        const val ENGINE_SPEECH = "speech"

        val DEFAULT_WAKE_PHRASES = listOf("gemini", "hey gemini", "wake up", "jarvis")

        private const val KEY_ENGINE = "engine"
        private const val KEY_WAKE_PHRASES = "wake_phrases"
        private const val KEY_AUTO_UNLOCK = "auto_unlock"
        private const val KEY_SPEAK = "speak"
        private const val KEY_ENABLED = "enabled"
    }
}

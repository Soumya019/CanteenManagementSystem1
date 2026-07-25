package com.soumya.voicepilot.wake

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import android.content.Context
import android.util.Log
import java.io.File

/**
 * On-device keyword spotting. This is the engine you want long term: it runs at a
 * couple of percent CPU with the screen off, where the SpeechRecognizer loop
 * would flatten the battery.
 *
 * Custom words are .ppn files trained at console.picovoice.ai and copied into
 * [keywordDirectory]. Train one per wording you actually use — "gemini",
 * "hey gemini", "wake up" — since keyword spotting matches fixed phrases, not
 * paraphrases. Until you do, this falls back to the built-in JARVIS keyword so
 * the app is testable straight after install.
 */
class PorcupineEngine(
    context: Context,
    private val accessKey: String,
    private val onWake: (String) -> Unit,
) : WakeWordEngine {

    private val appContext = context.applicationContext
    private var manager: PorcupineManager? = null
    private var labels: List<String> = emptyList()

    override val displayName: String
        get() = "Porcupine (${labels.joinToString(", ").ifEmpty { "no keywords" }})"

    override fun start() {
        val instance = manager ?: build().also { manager = it }
        instance.start()
    }

    override fun stop() {
        runCatching { manager?.stop() }
            .onFailure { Log.w(TAG, "stop failed", it) }
    }

    override fun release() {
        stop()
        runCatching { manager?.delete() }
        manager = null
    }

    private fun build(): PorcupineManager {
        val builder = PorcupineManager.Builder().setAccessKey(accessKey)
        val custom = customKeywords()

        if (custom.isNotEmpty()) {
            labels = custom.map { it.nameWithoutExtension.replace('_', ' ') }
            builder.setKeywordPaths(custom.map { it.absolutePath }.toTypedArray())
        } else {
            labels = listOf(FALLBACK_KEYWORD.name.lowercase())
            builder.setKeywords(arrayOf(FALLBACK_KEYWORD))
        }

        builder.setSensitivities(FloatArray(labels.size) { SENSITIVITY })

        return builder.build(appContext) { keywordIndex ->
            onWake(labels.getOrElse(keywordIndex) { "wake" })
        }
    }

    private fun customKeywords(): List<File> =
        keywordDirectory(appContext)
            .listFiles { file -> file.isFile && file.extension.equals("ppn", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()

    companion object {
        private const val TAG = "PorcupineEngine"
        private const val SENSITIVITY = 0.65f

        /** Higher catches more of your variations at the cost of more false fires. */
        private val FALLBACK_KEYWORD = Porcupine.BuiltInKeyword.JARVIS

        /** Copy trained .ppn files here (the setup screen shows the full path). */
        fun keywordDirectory(context: Context): File =
            File(context.filesDir, "keywords").apply { mkdirs() }
    }
}

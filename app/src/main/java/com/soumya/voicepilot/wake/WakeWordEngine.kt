package com.soumya.voicepilot.wake

/**
 * Something that listens continuously and reports when it hears a wake word.
 *
 * Implementations own the microphone while started, so the service always stops
 * the engine before handing the mic to the command recognizer.
 */
interface WakeWordEngine {

    val displayName: String

    /** @throws Exception when the engine cannot be started; the caller falls back. */
    fun start()

    fun stop()

    fun release()
}

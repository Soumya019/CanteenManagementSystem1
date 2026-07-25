package com.soumya.voicepilot

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Service to wake-screen channel.
 *
 * The unlock request has to travel this way: KeyguardManager.requestDismissKeyguard
 * needs a live Activity, so the service can only ask, and [com.soumya.voicepilot.ui.WakeActivity]
 * performs it on itself.
 */
object VoicePilotBus {

    private val _events = MutableSharedFlow<Event>(
        replay = 1,
        extraBufferCapacity = 8,
    )
    val events = _events.asSharedFlow()

    fun emit(event: Event) {
        _events.tryEmit(event)
    }

    sealed interface Event {
        data object RequestUnlock : Event
        data class Status(val text: String) : Event
        data object Listening : Event
        data object Dismiss : Event
    }
}

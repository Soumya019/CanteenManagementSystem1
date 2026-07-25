package com.soumya.voicepilot.intent

/** One thing you can ask for. Register new ones in [CommandRegistry]. */
sealed interface Command {

    val id: String

    /** Matched on overall similarity against any of [phrases]. */
    data class Phrase(
        override val id: String,
        val phrases: List<String>,
    ) : Command

    /**
     * Matched when the utterance starts with one of [verbs]; the rest becomes the
     * argument, so "open whatsapp" yields id=open_app, argument="whatsapp".
     */
    data class Prefix(
        override val id: String,
        val verbs: List<String>,
    ) : Command
}

data class CommandMatch(
    val id: String,
    val argument: String?,
    val score: Double,
)

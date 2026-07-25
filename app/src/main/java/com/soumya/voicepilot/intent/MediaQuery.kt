package com.soumya.voicepilot.intent

/**
 * Splits "shape of you on spotify" into what to play and where to play it.
 *
 * Kept free of Android types so the parsing rules can be tested directly.
 */
object MediaQuery {

    data class Request(
        val query: String,
        val appHint: String?,
    )

    private const val SEPARATOR = " on "

    /**
     * Splits on the *last* " on ", since the track name may well contain one —
     * "play turn it on on spotify" has to keep "turn it on" as the query.
     *
     * The hint is only a candidate. The caller decides whether it names an
     * installed app, and falls back to treating the whole utterance as the query
     * when it does not, so "play lofi beats on repeat" still searches for the
     * whole phrase rather than hunting for an app called "repeat".
     */
    fun parse(spoken: String): Request {
        val trimmed = spoken.trim()
        val separatorAt = trimmed.lastIndexOf(SEPARATOR)
        if (separatorAt <= 0) return Request(trimmed, null)

        val query = trimmed.substring(0, separatorAt).trim()
        val hint = trimmed.substring(separatorAt + SEPARATOR.length).trim()

        if (query.isEmpty() || hint.isEmpty()) return Request(trimmed, null)
        return Request(query, hint)
    }
}

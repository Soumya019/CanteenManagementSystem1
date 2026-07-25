package com.soumya.voicepilot.intent

/**
 * Turns a loose transcript into a [CommandMatch].
 *
 * Returning null is a meaningful answer, not a failure: it is what you get when
 * the user only said the wake word, and the caller treats that as "just wake the
 * screen and show me the time".
 */
class IntentMatcher(
    private val commands: List<Command> = CommandRegistry.commands,
    private val threshold: Double = 0.62,
) {

    fun match(utterance: String): CommandMatch? {
        val normalized = TextSimilarity.normalize(utterance)
        if (normalized.isEmpty()) return null

        matchPrefix(normalized)?.let { return it }

        var best: CommandMatch? = null
        for (command in commands) {
            if (command !is Command.Phrase) continue
            for (phrase in command.phrases) {
                val score = TextSimilarity.similarity(
                    normalized,
                    TextSimilarity.normalize(phrase),
                )
                if (best == null || score > best.score) {
                    best = CommandMatch(command.id, null, score)
                }
            }
        }
        return best?.takeIf { it.score >= threshold }
    }

    /** Picks the longest matching verb so "go to settings" beats "go". */
    private fun matchPrefix(normalized: String): CommandMatch? {
        var bestVerbLength = 0
        var result: CommandMatch? = null

        for (command in commands) {
            if (command !is Command.Prefix) continue
            for (verb in command.verbs) {
                val normalizedVerb = TextSimilarity.normalize(verb)
                if (normalizedVerb.isEmpty()) continue
                if (!normalized.startsWith("$normalizedVerb ")) continue

                val argument = normalized.removePrefix("$normalizedVerb ").trim()
                if (argument.isEmpty()) continue

                if (normalizedVerb.length > bestVerbLength) {
                    bestVerbLength = normalizedVerb.length
                    result = CommandMatch(command.id, argument, 1.0)
                }
            }
        }
        return result
    }
}

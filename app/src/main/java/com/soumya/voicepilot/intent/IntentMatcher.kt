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

        val phrase = bestPhrase(normalized)
        val leading = TextSimilarity.stripLeadingFillers(utterance)

        // A near-exact phrase outranks a prefix reading of the same words:
        // "play music" is the media toggle, not a request to go and find a song
        // called "music". Anything longer than the phrase falls through.
        if (phrase != null && phrase.score >= EXACT_ENOUGH) return phrase

        matchPrefix(leading)?.let { return it }

        return phrase?.takeIf { it.score >= threshold }
    }

    private fun bestPhrase(normalized: String): CommandMatch? {
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
        return best
    }

    /**
     * Picks the longest matching verb, so "go to settings" beats "go" and
     * "start playing x" beats "start x".
     */
    private fun matchPrefix(leading: String): CommandMatch? {
        var bestVerbLength = 0
        var result: CommandMatch? = null

        for (command in commands) {
            if (command !is Command.Prefix) continue
            for (verb in command.verbs) {
                val normalizedVerb = TextSimilarity.normalizeLight(verb)
                if (normalizedVerb.isEmpty()) continue
                if (!leading.startsWith("$normalizedVerb ")) continue

                val argument = leading.removePrefix("$normalizedVerb ").trim()
                if (argument.isEmpty()) continue

                if (normalizedVerb.length > bestVerbLength) {
                    bestVerbLength = normalizedVerb.length
                    result = CommandMatch(command.id, argument, 1.0)
                }
            }
        }
        return result
    }

    private companion object {
        const val EXACT_ENOUGH = 0.95
    }
}

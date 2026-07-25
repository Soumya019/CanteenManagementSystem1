package com.soumya.voicepilot.intent

import java.util.Locale
import kotlin.math.max

/**
 * Loose string comparison, so "please Gemini, turn the flashlight on" lands on the
 * same command as "flashlight on".
 */
object TextSimilarity {

    /**
     * Words that carry no intent. Stripping them is what lets the phrasing vary.
     * Wake words live here too — by the time we are matching a command, "gemini"
     * has already done its job.
     */
    private val FILLERS = setOf(
        "a", "an", "the", "to", "for", "please", "hey", "hi", "ok", "okay",
        "gemini", "jarvis", "assistant", "can", "could", "would", "will",
        "you", "your", "my", "me", "i", "now", "just", "kindly", "and", "so",
    )

    private val NON_ALPHANUMERIC = Regex("[^a-z0-9 ]")

    /** Lowercase and drop punctuation, keeping every word. Used for wake phrases. */
    fun normalizeLight(text: String): String =
        text.lowercase(Locale.ROOT)
            .replace(NON_ALPHANUMERIC, " ")
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ")

    /** [normalizeLight] plus filler removal. Used for scoring commands. */
    fun normalize(text: String): String =
        normalizeLight(text)
            .split(' ')
            .filter { it.isNotBlank() && it !in FILLERS }
            .joinToString(" ")

    /**
     * Drops only the fillers at the front, keeping the rest of the words intact.
     *
     * This is the form prefix commands are matched against, because a filler in
     * the middle of an utterance is usually content: "play shape of you on
     * spotify" must not lose its "you", even though [normalize] is right to drop
     * the same word when scoring "can you unlock my phone".
     */
    fun stripLeadingFillers(text: String): String =
        normalizeLight(text)
            .split(' ')
            .dropWhile { it.isBlank() || it in FILLERS }
            .joinToString(" ")

    /** 0.0 (nothing in common) to 1.0 (identical). */
    fun similarity(a: String, b: String): Double =
        max(diceOfTokens(a, b), editRatio(a, b))

    /**
     * Dice coefficient over tokens, where two tokens count as equal if they are
     * merely close — the recognizer mishears "flashlight" as "flash light" often
     * enough that exact token equality is too strict.
     */
    private fun diceOfTokens(a: String, b: String): Double {
        val left = a.split(' ').filter { it.isNotBlank() }.toSet()
        val right = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val shared = left.count { l -> right.any { r -> editRatio(l, r) >= 0.8 } }
        return (2.0 * shared) / (left.size + right.size)
    }

    private fun editRatio(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val longest = max(a.length, b.length)
        if (longest == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / longest
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}

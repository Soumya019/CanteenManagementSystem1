package com.soumya.voicepilot.action

import com.soumya.voicepilot.intent.TextSimilarity

/**
 * The media apps worth naming out loud, and how to hand each one a search.
 *
 * Anything not listed here still works — it falls through to matching the spoken
 * name against installed app labels — but the entries here get better routing,
 * because [searchUriTemplate] is a deep link the app is known to honour when the
 * standard play-from-search intent does not stick.
 */
data class MediaApp(
    val packageName: String,
    val aliases: List<String>,
    val searchUriTemplate: String?,
)

object MediaApps {

    val all: List<MediaApp> = listOf(
        MediaApp(
            packageName = "com.spotify.music",
            aliases = listOf("spotify"),
            searchUriTemplate = "spotify:search:%s",
        ),
        // Listed before plain YouTube so "youtube music" is not swallowed by it.
        MediaApp(
            packageName = "com.google.android.apps.youtube.music",
            aliases = listOf("youtube music", "yt music", "ytmusic"),
            searchUriTemplate = "https://music.youtube.com/search?q=%s",
        ),
        MediaApp(
            packageName = "com.google.android.youtube",
            aliases = listOf("youtube", "you tube", "yt"),
            searchUriTemplate = "https://www.youtube.com/results?search_query=%s",
        ),
        MediaApp(
            packageName = "com.audible.application",
            aliases = listOf("audible"),
            searchUriTemplate = null,
        ),
        MediaApp(
            packageName = "com.amazon.mp3",
            aliases = listOf("amazon music"),
            searchUriTemplate = null,
        ),
        MediaApp(
            packageName = "com.jio.media.jiobeats",
            aliases = listOf("jiosaavn", "jio saavn", "saavn"),
            searchUriTemplate = null,
        ),
        MediaApp(
            packageName = "com.gaana",
            aliases = listOf("gaana"),
            searchUriTemplate = null,
        ),
        MediaApp(
            packageName = "com.bsbportal.music",
            aliases = listOf("wynk", "wynk music"),
            searchUriTemplate = null,
        ),
        MediaApp(
            packageName = "com.soundcloud.android",
            aliases = listOf("soundcloud", "sound cloud"),
            searchUriTemplate = null,
        ),
    )

    /**
     * Candidates for a spoken app name, best first. Scoring rather than exact
     * matching is what makes "you tube" and "yt music" land correctly.
     */
    fun match(spokenName: String, threshold: Double = 0.7): List<MediaApp> {
        val hint = TextSimilarity.normalize(spokenName)
        if (hint.isEmpty()) return emptyList()

        return all
            .mapNotNull { app ->
                val score = app.aliases.maxOf { alias ->
                    TextSimilarity.similarity(hint, TextSimilarity.normalize(alias))
                }
                if (score >= threshold) app to score else null
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}

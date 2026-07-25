package com.soumya.voicepilot.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAppsTest {

    @Test
    fun `spoken names resolve to packages`() {
        assertEquals("com.spotify.music", MediaApps.match("spotify").first().packageName)
        assertEquals("com.audible.application", MediaApps.match("audible").first().packageName)
    }

    @Test
    fun `recognizer spacing does not break the match`() {
        // "youtube" is routinely transcribed as two words.
        assertEquals(
            "com.google.android.youtube",
            MediaApps.match("you tube").first().packageName,
        )
    }

    @Test
    fun `youtube music outranks youtube`() {
        assertEquals(
            "com.google.android.apps.youtube.music",
            MediaApps.match("youtube music").first().packageName,
        )
    }

    @Test
    fun `words that are not app names match nothing`() {
        assertTrue(MediaApps.match("repeat").isEmpty())
        assertTrue(MediaApps.match("").isEmpty())
    }
}

package com.soumya.voicepilot.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The matcher is the part that has to absorb natural variation, so these cases
 * are written the way the phrases are actually spoken rather than the way they
 * are stored in [CommandRegistry].
 */
class IntentMatcherTest {

    private val matcher = IntentMatcher()

    @Test
    fun `wake phrasings all reach the same command`() {
        val utterances = listOf(
            "wake up",
            "please Gemini, wake up",
            "hey Gemini wake up",
            "ok gemini, wake up now",
            "turn on screen",
        )
        utterances.forEach { utterance ->
            assertEquals(utterance, CommandRegistry.WAKE_SCREEN, matcher.match(utterance)?.id)
        }
    }

    @Test
    fun `bare wake word is not a command`() {
        // Null here is the signal for "screen on, show the time" — see
        // VoicePilotService.finishTurn.
        assertNull(matcher.match("Gemini"))
        assertNull(matcher.match("hey gemini"))
        assertNull(matcher.match("please gemini"))
    }

    @Test
    fun `unlock survives polite padding`() {
        listOf(
            "unlock",
            "unlock my phone",
            "can you unlock the phone",
            "please Gemini, unlock",
            "let me in",
        ).forEach { utterance ->
            assertEquals(utterance, CommandRegistry.UNLOCK, matcher.match(utterance)?.id)
        }
    }

    @Test
    fun `torch phrasings resolve in both directions`() {
        assertEquals(CommandRegistry.TORCH_ON, matcher.match("turn on the flashlight")?.id)
        assertEquals(CommandRegistry.TORCH_ON, matcher.match("flashlight on")?.id)
        assertEquals(CommandRegistry.TORCH_OFF, matcher.match("turn off the torch")?.id)
    }

    @Test
    fun `prefix commands capture their argument`() {
        val match = matcher.match("hey Gemini, please open WhatsApp")
        assertEquals(CommandRegistry.OPEN_APP, match?.id)
        assertEquals("whatsapp", match?.argument)
    }

    @Test
    fun `longest verb wins so go to settings is not go`() {
        val match = matcher.match("go to settings")
        assertEquals(CommandRegistry.OPEN_APP, match?.id)
        assertEquals("settings", match?.argument)
    }

    @Test
    fun `misheard words still match`() {
        // What the recognizer actually produces for these, often enough to matter.
        assertEquals(CommandRegistry.TIME, matcher.match("what's the time")?.id)
        assertEquals(CommandRegistry.MEDIA_NEXT, matcher.match("next song")?.id)
    }

    @Test
    fun `open phone is treated as the phone app not as unlock`() {
        val match = matcher.match("open phone")
        assertEquals(CommandRegistry.OPEN_APP, match?.id)
        assertEquals("phone", match?.argument)
    }

    @Test
    fun `bare transport words stay as media control, not a search`() {
        assertEquals(CommandRegistry.MEDIA_TOGGLE, matcher.match("play")?.id)
        assertEquals(CommandRegistry.MEDIA_TOGGLE, matcher.match("pause")?.id)
        // The exact-phrase rule: this must not become a hunt for a song called "music".
        assertEquals(CommandRegistry.MEDIA_TOGGLE, matcher.match("play music")?.id)
    }

    @Test
    fun `play with a query becomes a media search`() {
        val match = matcher.match("play despacito")
        assertEquals(CommandRegistry.PLAY_MEDIA, match?.id)
        assertEquals("despacito", match?.argument)
    }

    @Test
    fun `a query keeps words the command matcher would treat as filler`() {
        // "you" is a filler when scoring commands, but it is part of this title.
        val match = matcher.match("hey Gemini, please play Shape of You on Spotify")
        assertEquals(CommandRegistry.PLAY_MEDIA, match?.id)
        assertEquals("shape of you on spotify", match?.argument)
    }

    @Test
    fun `other ways of asking to play something`() {
        assertEquals("lofi beats", matcher.match("listen to lofi beats")?.argument)
        assertEquals("some jazz", matcher.match("put on some jazz")?.argument)
        assertEquals(
            CommandRegistry.PLAY_MEDIA,
            matcher.match("start playing the daily on audible")?.id,
        )
    }

    @Test
    fun `unrelated speech is rejected rather than guessed`() {
        assertNull(matcher.match("remind me to buy vegetables on the way home"))
        assertNull(matcher.match("asdfgh qwerty"))
    }
}

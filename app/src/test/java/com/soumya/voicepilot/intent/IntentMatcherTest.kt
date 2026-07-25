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
    fun `unrelated speech is rejected rather than guessed`() {
        assertNull(matcher.match("remind me to buy vegetables on the way home"))
        assertNull(matcher.match("asdfgh qwerty"))
    }
}

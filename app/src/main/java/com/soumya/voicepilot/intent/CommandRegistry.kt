package com.soumya.voicepilot.intent

/**
 * Every command the app understands.
 *
 * Adding one is two edits: an entry here, and a branch in
 * [com.soumya.voicepilot.action.ActionDispatcher]. Phrase lists are cheap — list
 * every wording you can imagine yourself using, the matcher handles the rest.
 */
object CommandRegistry {

    const val WAKE_SCREEN = "wake_screen"
    const val UNLOCK = "unlock"
    const val TIME = "time"
    const val TORCH_ON = "torch_on"
    const val TORCH_OFF = "torch_off"
    const val VOLUME_UP = "volume_up"
    const val VOLUME_DOWN = "volume_down"
    const val MUTE = "mute"
    const val MEDIA_TOGGLE = "media_toggle"
    const val MEDIA_NEXT = "media_next"
    const val MEDIA_PREVIOUS = "media_previous"
    const val WIFI_SETTINGS = "wifi_settings"
    const val BLUETOOTH_SETTINGS = "bluetooth_settings"
    const val SETTINGS = "settings"
    const val CANCEL = "cancel"
    const val OPEN_APP = "open_app"
    const val DIAL = "dial"

    val commands: List<Command> = listOf(
        // Prefix commands are checked first, so their argument text cannot drag
        // a phrase command's score down.
        Command.Prefix(OPEN_APP, listOf("open", "launch", "start", "run", "go to")),
        Command.Prefix(DIAL, listOf("call", "dial", "phone")),

        Command.Phrase(
            WAKE_SCREEN,
            listOf("wake up", "wake", "turn on screen", "screen on", "show me the screen"),
        ),
        // Deliberately no "open phone" here: the OPEN_APP prefix is checked first
        // and would read it as "launch the Phone app", which is also what a person
        // saying it most likely means.
        Command.Phrase(
            UNLOCK,
            listOf("unlock", "unlock phone", "unlock the phone", "unlock it", "let me in"),
        ),
        Command.Phrase(
            TIME,
            listOf("time", "what time is it", "whats the time", "tell me the time"),
        ),
        Command.Phrase(
            TORCH_ON,
            listOf("torch on", "flashlight on", "turn on flashlight", "turn on torch", "light on"),
        ),
        Command.Phrase(
            TORCH_OFF,
            listOf("torch off", "flashlight off", "turn off flashlight", "turn off torch", "light off"),
        ),
        Command.Phrase(
            VOLUME_UP,
            listOf("volume up", "louder", "turn it up", "increase volume"),
        ),
        Command.Phrase(
            VOLUME_DOWN,
            listOf("volume down", "quieter", "turn it down", "decrease volume"),
        ),
        Command.Phrase(
            MUTE,
            listOf("mute", "silence", "be quiet", "shut up"),
        ),
        Command.Phrase(
            MEDIA_TOGGLE,
            listOf("play", "pause", "play music", "pause music", "resume", "stop music"),
        ),
        Command.Phrase(
            MEDIA_NEXT,
            listOf("next", "next track", "next song", "skip"),
        ),
        Command.Phrase(
            MEDIA_PREVIOUS,
            listOf("previous", "previous track", "previous song", "go back a song"),
        ),
        Command.Phrase(
            WIFI_SETTINGS,
            listOf("wifi", "wi fi", "wifi settings", "show wifi"),
        ),
        Command.Phrase(
            BLUETOOTH_SETTINGS,
            listOf("bluetooth", "bluetooth settings", "show bluetooth"),
        ),
        Command.Phrase(
            SETTINGS,
            listOf("settings", "open settings", "system settings"),
        ),
        Command.Phrase(
            CANCEL,
            listOf("cancel", "never mind", "nothing", "forget it", "stop"),
        ),
    )
}

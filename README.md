# VoicePilot

An always-on voice controller for Android: say a wake word, the screen lights up
showing the time, then say a command. Built for an Infinix Note 40 5G (X6852) on
Android 15, but nothing in it is device-specific except the XOS auto-start
shortcut.

Wake words are yours to choose, and the command layer is deliberately loose about
phrasing — "unlock", "please Gemini, unlock my phone" and "can you unlock the
phone" all land on the same action.

## What it does, and what Android will not let it do

| | |
|---|---|
| Custom wake words | ✅ Porcupine, or the platform recognizer with no setup |
| Wake the screen while locked, show the clock | ✅ `setShowWhenLocked` + `setTurnScreenOn` |
| Loose command phrasing | ✅ filler-stripping + fuzzy matching, unit-tested |
| Commands: torch, volume, media, apps, settings, dial | ✅ |
| **Enter your PIN / pattern / fingerprint by voice** | ❌ **not possible** |

That last row is a platform decision, not a missing feature. `requestDismissKeyguard`
never types a credential. What it does do:

- **Extend Unlock active** (paired watch, earbuds, car, or a trusted place) — the
  keyguard dismisses silently and you are straight in. This is the setup worth
  having: Settings → Security & privacy → More security → Extend Unlock.
- **Extend Unlock not active** — Android shows you the credential prompt. Voice
  gets you to the prompt, your finger or PIN finishes it.

Anything that genuinely bypasses this needs root or Shizuku-level input
injection, means storing your PIN in plaintext, and hands your lock screen to
anyone who can say the phrase out loud. This app does not go there.

## Install on the phone, without a PC

Every push builds the APK in CI and attaches it to a rolling prerelease, so the
download link never changes:

**`https://github.com/Soumya019/CanteenManagementSystem1/releases/tag/latest-debug`**

1. Open that link in Chrome on the phone and tap **voicepilot-debug.apk**.
2. Chrome asks to allow installing unknown apps — allow it for Chrome, then tap
   the download again.
3. XOS scans the file and may warn that it came from an unknown source. Choose
   **Install anyway**. Play Protect may add its own prompt; **Install without
   scanning** is fine, this is your own debug build.

It is signed with the standard Android debug key, which is enough to install but
not to publish. Nothing else is needed — the Picovoice key and wake-word models
are both entered inside the app.

If the release is not there yet, the workflow may still be running: check the
**Actions** tab, or trigger *Build APK* → *Run workflow* manually.

## Build from a checkout

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest    # intent matcher tests
```

Requires JDK 17+, Android SDK 35. The APK lands in
`app/build/outputs/apk/debug/`. Optionally add `picovoice.accessKey=...` to
`local.properties` to bake the key in instead of typing it on the setup screen.

## First run

Open the app and work down the setup screen. Each row shows ✓ or ✗ so you can
see what is still missing.

1. **Microphone + notifications** — required; the service refuses to start
   without the mic permission.
2. **Display over other apps** — this is what lets the wake screen appear while
   the display is off. Without it the app falls back to a full-screen-intent
   notification, which Android 14+ gates separately (button 3).
3. **Battery optimisation** — must be disabled or the listener gets killed.
4. **XOS auto-start** — Infinix keeps this in Phone Master, not in Settings. The
   button tries the known component names and falls back to the app info page.
   Skip this and the service will not survive a reboot or a memory squeeze.
5. **Extend Unlock** — pair a Bluetooth watch or earbuds if you want silent
   unlocking. See above.

Then flip **Listening** on.

### A note on reboots

Android 14 and later refuse to let `BOOT_COMPLETED` start a microphone
foreground service. `BootReceiver` handles the rejection by posting a
tap-to-start notification rather than failing quietly. There is no workaround;
this is the restriction working as intended.

## Wake words

Two engines, switchable on the setup screen.

**SpeechRecognizer (default with no key).** Zero setup. Edit the phrase list in
the app — comma-separated, defaults to `gemini, hey gemini, wake up, jarvis`. It
matches loosely, so a phrase buried in a longer sentence still fires. The cost is
battery: it holds the mic continuously. Fine for trying this out, not for leaving
on all day.

**Porcupine (recommended).** On-device keyword spotting at a couple of percent
CPU. Needs a free AccessKey, and the whole setup works from the phone browser:

1. Sign up at [console.picovoice.ai](https://console.picovoice.ai) and copy your
   AccessKey into the field on the setup screen. (Or bake it in at build time via
   `local.properties` — the in-app key wins if both are set.)
2. In the console, train each wake phrase and download it for **Android**. Each
   one is a `.ppn` file that lands in Downloads.
3. Back in the app, tap **Import .ppn wake-word models** and pick them. They are
   copied into the app's private storage, which is why importing exists at all —
   nothing on the phone can write there directly.

Train **one model per wording you actually use** — `gemini`, `hey_gemini`,
`wake_up`. Keyword spotting matches fixed phrases; it does not paraphrase. The
flexibility lives in the command layer, after the wake word.

Models are platform-specific: a `.ppn` trained for another platform will fail to
load, and the app falls back to the SpeechRecognizer engine.

With a key but no `.ppn` files yet, Porcupine runs on the built-in `JARVIS`
keyword so you can test the whole pipeline immediately.

## Commands

Say the wake word, wait for the beep, then speak. The wake word on its own is a
valid request — it wakes the screen and shows the time.

```
unlock                    open <app name>          what's the time
wake up                   call <digits>            volume up / down / mute
flashlight on / off       wifi / bluetooth         play / pause / next / previous
settings                  cancel
```

### Playing something by name

```
play shape of you on spotify        listen to lofi beats
play <podcast> on youtube           put on some jazz
start playing <book> on audible     play despacito          (no app named)
```

Bare transport words stay transport words — "play", "pause" and "play music"
control whatever is already playing. Adding a query is what turns it into a
search.

Naming the app is optional. With one, the query is routed there; without one it
goes to whatever the system has registered for media search. Known apps —
Spotify, YouTube, YouTube Music, Audible, Amazon Music, JioSaavn, Gaana, Wynk,
SoundCloud — get better routing via `MediaApps`, and anything else still works by
matching the spoken name against installed app labels.

**Whether it plays or just searches is the app's decision, not this app's.**
Spotify and YouTube Music honour `INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` and start
playing. The main YouTube app generally lands on search results and waits for a
tap. Four routes are tried in descending order of directness — play-from-search,
in-app search, deep link, then just launching the app — and the spoken
confirmation tells you which one took it ("Playing…" vs "Searching…").

Playback started while the phone is locked puts the media app behind the
keyguard. Audio usually starts anyway, but if you want to see and touch the app,
unlock first — which is exactly what Extend Unlock makes painless.

Adding one is two edits — an entry in `CommandRegistry` and a branch in
`ActionDispatcher`:

```kotlin
// CommandRegistry.kt
const val SCREENSHOT = "screenshot"

Command.Phrase(SCREENSHOT, listOf("screenshot", "take a screenshot", "capture screen"))
```

List every wording you can imagine yourself using — phrase lists are cheap, and
the matcher covers the gaps between them.

## How a turn works

Only one component may hold the microphone at a time, so the handover is
explicit at every step:

```
wake engine hears the word
  → release the mic
  → wake lock + WakeActivity in front of the keyguard  (clock is now visible)
  → beep
  → SpeechRecognizer dictates one utterance
  → IntentMatcher scores every n-best alternative, keeps the strongest
  → ActionDispatcher runs it, Speaker confirms
  → mic back to the wake engine
```

`VoicePilotBus` exists for one reason: `requestDismissKeyguard` needs a live
Activity, so the service can only ask, and `WakeActivity` performs the unlock on
itself.

## Layout

```
app/src/main/java/com/soumya/voicepilot/
├── VoicePilotBus.kt          service → wake screen events
├── action/
│   ├── ActionDispatcher.kt   runs a matched command
│   └── ScreenController.kt   turning the display on past the keyguard
├── intent/
│   ├── CommandRegistry.kt    every command, and its phrasings
│   ├── IntentMatcher.kt      transcript → command
│   └── TextSimilarity.kt     filler stripping, Dice + Levenshtein
├── service/
│   ├── VoicePilotService.kt  the always-on listener
│   └── BootReceiver.kt
├── speech/                   dictation + TTS/beep
├── ui/                       setup screen, wake screen
└── wake/                     Porcupine and SpeechRecognizer engines
```

## Battery

Porcupine with the screen off is cheap. The SpeechRecognizer loop is not — expect
a real dent if you leave it running. Either way the app must be exempt from
battery optimisation and allowed in XOS auto-start, or the OS will stop the
service and the wake word will silently stop working.

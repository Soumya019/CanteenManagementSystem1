package com.soumya.voicepilot.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.soumya.voicepilot.BuildConfig
import com.soumya.voicepilot.R
import com.soumya.voicepilot.action.ScreenController
import com.soumya.voicepilot.databinding.ActivityMainBinding
import com.soumya.voicepilot.intent.Command
import com.soumya.voicepilot.intent.CommandRegistry
import com.soumya.voicepilot.service.VoicePilotService
import com.soumya.voicepilot.util.Prefs
import com.soumya.voicepilot.wake.PorcupineEngine

/** Setup and status. Everything that needs a human tap once lives here. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var screen: ScreenController

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        screen = ScreenController(this)

        binding.micButton.setOnClickListener { askForPermissions() }
        binding.overlayButton.setOnClickListener { openOverlaySettings() }
        binding.batteryButton.setOnClickListener { openBatterySettings() }
        binding.fullScreenIntentButton.setOnClickListener { openFullScreenIntentSettings() }
        binding.extendUnlockButton.setOnClickListener { openSecuritySettings() }
        binding.autostartButton.setOnClickListener { openAutoStartSettings() }

        binding.serviceSwitch.setOnCheckedChangeListener { button, isChecked ->
            if (!button.isPressed) return@setOnCheckedChangeListener
            if (isChecked) {
                if (hasMicrophonePermission()) {
                    VoicePilotService.start(this)
                } else {
                    button.isChecked = false
                    askForPermissions()
                }
            } else {
                VoicePilotService.stop(this)
            }
            render()
        }

        binding.speakSwitch.setOnCheckedChangeListener { button, isChecked ->
            if (button.isPressed) prefs.speakConfirmations = isChecked
        }

        binding.autoUnlockSwitch.setOnCheckedChangeListener { button, isChecked ->
            if (button.isPressed) prefs.autoUnlockOnWake = isChecked
        }

        binding.engineGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.engine = when (checkedId) {
                R.id.enginePorcupine -> Prefs.ENGINE_PORCUPINE
                R.id.engineSpeech -> Prefs.ENGINE_SPEECH
                else -> Prefs.ENGINE_AUTO
            }
            restartIfRunning()
        }

        binding.savePhrasesButton.setOnClickListener {
            val phrases = binding.wakePhrasesInput.text
                .toString()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (phrases.isEmpty()) {
                toast(getString(R.string.setup_phrases_empty))
                return@setOnClickListener
            }
            prefs.wakePhrases = phrases
            restartIfRunning()
            toast(getString(R.string.setup_phrases_saved))
        }

        binding.commandsText.text = commandCheatSheet()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        binding.serviceSwitch.isChecked = prefs.serviceEnabled
        binding.speakSwitch.isChecked = prefs.speakConfirmations
        binding.autoUnlockSwitch.isChecked = prefs.autoUnlockOnWake
        binding.wakePhrasesInput.setText(prefs.wakePhrases.joinToString(", "))

        binding.engineGroup.check(
            when (prefs.engine) {
                Prefs.ENGINE_PORCUPINE -> R.id.enginePorcupine
                Prefs.ENGINE_SPEECH -> R.id.engineSpeech
                else -> R.id.engineAuto
            },
        )

        binding.micStatus.text = statusLine(R.string.setup_mic, hasMicrophonePermission())
        binding.overlayStatus.text = statusLine(R.string.setup_overlay, Settings.canDrawOverlays(this))
        binding.batteryStatus.text = statusLine(R.string.setup_battery, isBatteryOptimizationIgnored())
        binding.fullScreenIntentStatus.text =
            statusLine(R.string.setup_full_screen_intent, screen.canRaiseScreen())

        binding.keyStatus.text = if (BuildConfig.PICOVOICE_ACCESS_KEY.isBlank()) {
            getString(R.string.setup_key_missing)
        } else {
            getString(R.string.setup_key_present)
        }

        binding.keywordPathText.text = getString(
            R.string.setup_keyword_path,
            PorcupineEngine.keywordDirectory(this).absolutePath,
        )
    }

    private fun statusLine(labelRes: Int, granted: Boolean): String {
        val mark = if (granted) "✓" else "✗"
        return "$mark  ${getString(labelRes)}"
    }

    private fun askForPermissions() {
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        requestPermissions.launch(wanted.toTypedArray())
    }

    private fun openOverlaySettings() = safeStart(
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        ),
    )

    private fun openBatterySettings() = safeStart(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            safeStart(
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:$packageName"),
                ),
            )
        } else {
            toast(getString(R.string.setup_not_needed_here))
        }
    }

    private fun openSecuritySettings() = safeStart(Intent(Settings.ACTION_SECURITY_SETTINGS))

    /**
     * XOS keeps auto-start in Phone Master rather than in Settings, and the
     * component has moved between releases — so try the known ones, then fall
     * back to this app's details page.
     */
    private fun openAutoStartSettings() {
        val candidates = listOf(
            ComponentName(
                "com.transsion.phonemaster",
                "com.cyin.himgr.autostart.AutoStartActivity",
            ),
            ComponentName(
                "com.transsion.phoenix",
                "com.cyin.himgr.autostart.AutoStartActivity",
            ),
            ComponentName(
                "com.transsion.phonemaster",
                "com.transsion.phonemaster.autostart.AutoStartActivity",
            ),
        )

        for (component in candidates) {
            val intent = Intent().setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(packageManager) != null) {
                safeStart(intent)
                return
            }
        }

        toast(getString(R.string.setup_autostart_manual))
        safeStart(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun safeStart(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure { toast(getString(R.string.setup_screen_unavailable)) }
    }

    private fun restartIfRunning() {
        if (!prefs.serviceEnabled) return
        VoicePilotService.stop(this)
        VoicePilotService.start(this)
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun isBatteryOptimizationIgnored(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun commandCheatSheet(): String =
        CommandRegistry.commands.joinToString("\n") { command ->
            when (command) {
                is Command.Phrase -> "• ${command.phrases.first()}"
                is Command.Prefix -> "• ${command.verbs.first()} …"
            }
        }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}

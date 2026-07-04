package com.example.btledcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.btledcontroller.databinding.ActivityMainBinding
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity(), BluetoothLedService.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var ledService: BluetoothLedService

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    /** Deferred action to run once permissions are granted. */
    private var pendingAction: (() -> Unit)? = null

    private var lastBrightnessSentAt = 0L

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val action = pendingAction
        pendingAction = null
        if (results.values.all { it }) {
            action?.invoke()
        } else {
            Toast.makeText(this, R.string.permissions_required, Toast.LENGTH_LONG).show()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            showDevicePicker()
        } else {
            Toast.makeText(this, R.string.bluetooth_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ledService = BluetoothLedService(this)

        binding.buttonConnect.setOnClickListener {
            if (ledService.state == BluetoothLedService.State.DISCONNECTED) {
                withBluetoothPermissions { openDevicePickerFlow() }
            } else {
                ledService.disconnect()
            }
        }

        setUpLedControls()
        updateConnectionUi(BluetoothLedService.State.DISCONNECTED, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        ledService.disconnect()
    }

    // ---------------------------------------------------------------- setup

    private fun setUpLedControls() {
        binding.switchPower.setOnCheckedChangeListener { _, isChecked ->
            if (binding.switchPower.isPressed) {
                sendCommand(if (isChecked) Commands.ON else Commands.OFF)
            }
        }

        binding.buttonRed.setOnClickListener { sendCommand(Commands.COLOR_RED) }
        binding.buttonGreen.setOnClickListener { sendCommand(Commands.COLOR_GREEN) }
        binding.buttonBlue.setOnClickListener { sendCommand(Commands.COLOR_BLUE) }
        binding.buttonWhite.setOnClickListener { sendCommand(Commands.COLOR_WHITE) }
        binding.buttonBlink.setOnClickListener { sendCommand(Commands.BLINK_TOGGLE) }

        binding.sliderBrightness.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            // Rate-limit so a slow 9600-baud link is not flooded mid-drag.
            val now = SystemClock.elapsedRealtime()
            if (now - lastBrightnessSentAt >= BRIGHTNESS_THROTTLE_MS) {
                lastBrightnessSentAt = now
                sendBrightness(value.toInt())
            }
        }
        binding.sliderBrightness.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                // Always send the final value.
                sendBrightness(slider.value.toInt())
            }
        })

        binding.buttonSendCustom.setOnClickListener {
            val text = binding.editCustomCommand.text?.toString().orEmpty().trim()
            if (text.isNotEmpty()) {
                sendCommand(text + "\n")
                binding.editCustomCommand.text?.clear()
            }
        }
    }

    // ----------------------------------------------------- connection flow

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun withBluetoothPermissions(action: () -> Unit) {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    private fun openDevicePickerFlow() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Toast.makeText(this, R.string.bluetooth_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        if (!adapter.isEnabled) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        showDevicePicker()
    }

    private fun showDevicePicker() {
        val adapter = bluetoothAdapter ?: return
        DevicePickerDialog(this, adapter) { device -> connectTo(device) }.show()
    }

    @SuppressLint("MissingPermission")
    private fun connectTo(device: BluetoothDevice) {
        // Discovery slows down connections drastically; make sure it is off.
        bluetoothAdapter?.cancelDiscovery()
        appendLog(getString(R.string.log_connecting, device.name ?: device.address))
        ledService.connect(device)
    }

    // -------------------------------------------------------------- sending

    private fun sendCommand(command: String) {
        if (ledService.send(command)) {
            appendLog(getString(R.string.log_sent, command.trim()))
        } else {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendBrightness(value: Int) {
        sendCommand("${Commands.BRIGHTNESS_PREFIX}$value\n")
    }

    // ------------------------------------------------------------ callbacks

    override fun onStateChanged(state: BluetoothLedService.State, deviceName: String?) {
        updateConnectionUi(state, deviceName)
        when (state) {
            BluetoothLedService.State.CONNECTED ->
                appendLog(getString(R.string.log_connected, deviceName ?: ""))
            BluetoothLedService.State.DISCONNECTED ->
                appendLog(getString(R.string.log_disconnected))
            BluetoothLedService.State.CONNECTING -> Unit
        }
    }

    override fun onDataReceived(line: String) {
        appendLog(getString(R.string.log_received, line))
    }

    override fun onError(message: String) {
        appendLog(message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------- ui

    private fun updateConnectionUi(state: BluetoothLedService.State, deviceName: String?) {
        val connected = state == BluetoothLedService.State.CONNECTED
        binding.textConnectionStatus.setText(
            when (state) {
                BluetoothLedService.State.DISCONNECTED -> R.string.status_disconnected
                BluetoothLedService.State.CONNECTING -> R.string.status_connecting
                BluetoothLedService.State.CONNECTED -> R.string.status_connected
            }
        )
        binding.textDeviceName.text =
            if (connected) deviceName else getString(R.string.no_device)
        binding.buttonConnect.setText(
            if (state == BluetoothLedService.State.DISCONNECTED) {
                R.string.connect
            } else {
                R.string.disconnect
            }
        )

        binding.switchPower.isEnabled = connected
        binding.sliderBrightness.isEnabled = connected
        binding.buttonRed.isEnabled = connected
        binding.buttonGreen.isEnabled = connected
        binding.buttonBlue.isEnabled = connected
        binding.buttonWhite.isEnabled = connected
        binding.buttonBlink.isEnabled = connected
        binding.buttonSendCustom.isEnabled = connected
        binding.editCustomCommand.isEnabled = connected

        if (!connected) {
            binding.switchPower.isChecked = false
        }
    }

    private fun appendLog(message: String) {
        val time = DateFormat.format("HH:mm:ss", System.currentTimeMillis())
        binding.textLog.append("[$time] $message\n")
        binding.scrollLog.post {
            binding.scrollLog.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    /**
     * Serial protocol understood by the companion Arduino sketch
     * (see arduino/BluetoothLED/BluetoothLED.ino).
     */
    private object Commands {
        const val ON = "1"
        const val OFF = "0"
        const val COLOR_RED = "r"
        const val COLOR_GREEN = "g"
        const val COLOR_BLUE = "b"
        const val COLOR_WHITE = "w"
        const val BLINK_TOGGLE = "f"
        const val BRIGHTNESS_PREFIX = "V"
    }

    companion object {
        private const val BRIGHTNESS_THROTTLE_MS = 120L
    }
}

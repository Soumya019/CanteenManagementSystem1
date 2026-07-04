package com.example.btledcontroller

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Manages a classic Bluetooth (RFCOMM/SPP) connection to an LED controller
 * such as an HC-05 or HC-06 module wired to an Arduino.
 *
 * All callbacks are delivered on the main thread.
 */
class BluetoothLedService(private val listener: Listener) {

    interface Listener {
        fun onStateChanged(state: State, deviceName: String?)
        fun onDataReceived(line: String)
        fun onError(message: String)
    }

    enum class State { DISCONNECTED, CONNECTING, CONNECTED }

    var state: State = State.DISCONNECTED
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    @SuppressLint("MissingPermission")
    @Synchronized
    fun connect(device: BluetoothDevice) {
        disconnectInternal()
        setState(State.CONNECTING, device.name ?: device.address)
        connectThread = ConnectThread(device).also { it.start() }
    }

    @Synchronized
    fun disconnect() {
        disconnectInternal()
        setState(State.DISCONNECTED, null)
    }

    /** Sends the given string to the connected device. Returns false when not connected. */
    fun send(data: String): Boolean {
        val thread = synchronized(this) {
            if (state != State.CONNECTED) return false
            connectedThread ?: return false
        }
        return thread.write(data.toByteArray())
    }

    private fun disconnectInternal() {
        connectThread?.cancel()
        connectThread = null
        connectedThread?.cancel()
        connectedThread = null
    }

    private fun setState(newState: State, deviceName: String?) {
        state = newState
        mainHandler.post { listener.onStateChanged(newState, deviceName) }
    }

    private fun postError(message: String) {
        mainHandler.post { listener.onError(message) }
    }

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread("bt-connect") {

        @SuppressLint("MissingPermission")
        private val socket: BluetoothSocket? = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e: IOException) {
            Log.e(TAG, "Socket creation failed", e)
            null
        }

        @Volatile
        private var cancelled = false

        @SuppressLint("MissingPermission")
        override fun run() {
            val sock = socket
            if (sock == null) {
                connectionFailed("Could not create Bluetooth socket")
                return
            }
            try {
                // Blocks until it succeeds or throws.
                sock.connect()
            } catch (e: IOException) {
                try {
                    sock.close()
                } catch (closeException: IOException) {
                    Log.e(TAG, "Could not close socket after failed connect", closeException)
                }
                if (!cancelled) {
                    connectionFailed("Connection failed: ${e.message ?: "device unreachable"}")
                }
                return
            }

            synchronized(this@BluetoothLedService) {
                if (cancelled) return
                connectThread = null
                connectedThread = ConnectedThread(sock).also { it.start() }
                // "State" alone would resolve to Thread.State inside this subclass.
                setState(BluetoothLedService.State.CONNECTED, device.name ?: device.address)
            }
        }

        private fun connectionFailed(message: String) {
            synchronized(this@BluetoothLedService) {
                if (this@BluetoothLedService.state == BluetoothLedService.State.CONNECTING) {
                    setState(BluetoothLedService.State.DISCONNECTED, null)
                }
            }
            postError(message)
        }

        fun cancel() {
            cancelled = true
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close connect socket", e)
            }
        }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread("bt-io") {

        private val input: InputStream = socket.inputStream
        private val output: OutputStream = socket.outputStream

        @Volatile
        private var cancelled = false

        override fun run() {
            val buffer = ByteArray(1024)
            val lineBuilder = StringBuilder()
            while (!cancelled) {
                val count = try {
                    input.read(buffer)
                } catch (e: IOException) {
                    if (!cancelled) connectionLost()
                    return
                }
                if (count < 0) {
                    if (!cancelled) connectionLost()
                    return
                }
                // Deliver complete lines so the UI log stays readable.
                for (i in 0 until count) {
                    val c = buffer[i].toInt().toChar()
                    if (c == '\n') {
                        val line = lineBuilder.toString().trim()
                        lineBuilder.setLength(0)
                        if (line.isNotEmpty()) {
                            mainHandler.post { listener.onDataReceived(line) }
                        }
                    } else {
                        lineBuilder.append(c)
                    }
                }
            }
        }

        fun write(bytes: ByteArray): Boolean {
            return try {
                output.write(bytes)
                output.flush()
                true
            } catch (e: IOException) {
                postError("Send failed: ${e.message}")
                connectionLost()
                false
            }
        }

        private fun connectionLost() {
            synchronized(this@BluetoothLedService) {
                if (connectedThread === this) {
                    connectedThread = null
                    setState(BluetoothLedService.State.DISCONNECTED, null)
                }
            }
        }

        fun cancel() {
            cancelled = true
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close connected socket", e)
            }
        }
    }

    companion object {
        private const val TAG = "BluetoothLedService"

        /** Standard Serial Port Profile UUID used by HC-05/HC-06 modules. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

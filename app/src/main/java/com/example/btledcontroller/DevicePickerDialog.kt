package com.example.btledcontroller

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.btledcontroller.databinding.DialogDevicePickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Bottom sheet listing paired devices and, on demand, scanning for new ones.
 *
 * The caller must ensure Bluetooth permissions are granted and the adapter is
 * enabled before showing this dialog.
 */
@SuppressLint("MissingPermission")
class DevicePickerDialog(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val onDeviceSelected: (BluetoothDevice) -> Unit
) {

    private val dialog = BottomSheetDialog(context)
    private val binding = DialogDevicePickerBinding.inflate(LayoutInflater.from(context))
    private val adapter = DeviceAdapter { device ->
        dismiss()
        onDeviceSelected(device)
    }

    private var receiverRegistered = false

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        adapter.addDevice(
                            DeviceAdapter.Entry(it, it.bondState == BluetoothDevice.BOND_BONDED)
                        )
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> setScanning(false)
            }
        }
    }

    init {
        binding.recyclerDevices.layoutManager = LinearLayoutManager(context)
        binding.recyclerDevices.adapter = adapter
        binding.buttonScan.setOnClickListener { toggleScan() }
        dialog.setContentView(binding.root)
        dialog.setOnDismissListener { cleanUp() }
    }

    fun show() {
        loadPairedDevices()
        dialog.show()
    }

    fun dismiss() {
        dialog.dismiss()
    }

    private fun loadPairedDevices() {
        val paired = bluetoothAdapter.bondedDevices
            .sortedBy { it.name ?: it.address }
            .map { DeviceAdapter.Entry(it, paired = true) }
        adapter.setDevices(paired)
        binding.textEmpty.visibility =
            if (paired.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun toggleScan() {
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
            setScanning(false)
            return
        }
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(discoveryReceiver, filter)
            receiverRegistered = true
        }
        binding.textEmpty.visibility = android.view.View.GONE
        setScanning(bluetoothAdapter.startDiscovery())
    }

    private fun setScanning(scanning: Boolean) {
        binding.progressScan.visibility =
            if (scanning) android.view.View.VISIBLE else android.view.View.INVISIBLE
        binding.buttonScan.setText(if (scanning) R.string.stop_scan else R.string.scan_for_devices)
    }

    private fun cleanUp() {
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        if (receiverRegistered) {
            context.unregisterReceiver(discoveryReceiver)
            receiverRegistered = false
        }
    }
}

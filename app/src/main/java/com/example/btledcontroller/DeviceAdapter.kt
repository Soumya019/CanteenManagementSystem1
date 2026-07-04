package com.example.btledcontroller

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.btledcontroller.databinding.ItemDeviceBinding

/** Simple list adapter for paired and discovered Bluetooth devices. */
class DeviceAdapter(
    private val onClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    data class Entry(val device: BluetoothDevice, val paired: Boolean)

    private val entries = mutableListOf<Entry>()

    @SuppressLint("NotifyDataSetChanged")
    fun setDevices(devices: List<Entry>) {
        entries.clear()
        entries.addAll(devices)
        notifyDataSetChanged()
    }

    fun addDevice(entry: Entry) {
        if (entries.none { it.device.address == entry.device.address }) {
            entries.add(entry)
            notifyItemInserted(entries.size - 1)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(entries[position])
    }

    override fun getItemCount(): Int = entries.size

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("MissingPermission")
        fun bind(entry: Entry) {
            val context = binding.root.context
            binding.textDeviceName.text =
                entry.device.name ?: context.getString(R.string.unknown_device)
            binding.textDeviceAddress.text = entry.device.address
            binding.textDeviceStatus.text = context.getString(
                if (entry.paired) R.string.device_paired else R.string.device_new
            )
            binding.root.setOnClickListener { onClick(entry.device) }
        }
    }
}

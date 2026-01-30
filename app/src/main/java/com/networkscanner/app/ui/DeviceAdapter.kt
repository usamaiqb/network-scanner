package com.networkscanner.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.networkscanner.app.R
import com.networkscanner.app.data.Device
import com.networkscanner.app.databinding.ItemDeviceBinding
import com.networkscanner.app.databinding.ItemSectionHeaderBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying devices in a RecyclerView.
 */
class DeviceAdapter(
    private val onDeviceClick: (Device) -> Unit = {}
) : ListAdapter<DeviceAdapter.ListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_DEVICE = 1
    }

    sealed class ListItem {
        data class Header(val title: String, val count: Int) : ListItem()
        data class DeviceItem(val device: Device) : ListItem()
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ListItem.Header -> VIEW_TYPE_HEADER
            is ListItem.DeviceItem -> VIEW_TYPE_DEVICE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemSectionHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemDeviceBinding.inflate(inflater, parent, false)
                DeviceViewHolder(binding, onDeviceClick)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ListItem.DeviceItem -> (holder as DeviceViewHolder).bind(item.device)
        }
    }

    /**
     * Submit devices with section headers.
     */
    fun submitDevices(online: List<Device>, offline: List<Device>) {
        val items = mutableListOf<ListItem>()

        if (online.isNotEmpty()) {
            items.add(ListItem.Header("Active Devices", online.size))
            items.addAll(online.map { ListItem.DeviceItem(it) })
        }

        if (offline.isNotEmpty()) {
            items.add(ListItem.Header("Offline Devices", offline.size))
            items.addAll(offline.map { ListItem.DeviceItem(it) })
        }

        submitList(items)
    }

    class HeaderViewHolder(
        private val binding: ItemSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(header: ListItem.Header) {
            binding.headerTitle.text = header.title
            binding.headerCount.text = header.count.toString()

            // Accessibility content description
            binding.root.contentDescription = binding.root.context.getString(
                R.string.cd_section_header,
                header.title,
                header.count
            )
        }
    }

    class DeviceViewHolder(
        private val binding: ItemDeviceBinding,
        private val onDeviceClick: (Device) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        fun bind(device: Device) {
            binding.apply {
                val context = root.context

                // Device icon
                deviceIcon.setImageResource(device.deviceType.iconRes)

                // Device name
                deviceName.text = device.displayName

                // This device badge
                thisDeviceBadge.visibility = if (device.isCurrentDevice) View.VISIBLE else View.GONE

                // IP and MAC
                deviceIp.text = device.ipAddress
                deviceMac.text = device.macAddress?.uppercase() ?: "Unknown MAC"
                deviceMac.visibility = if (device.macAddress != null) View.VISIBLE else View.GONE

                // Vendor
                if (device.vendor != null) {
                    deviceVendor.text = device.vendor
                    deviceVendor.visibility = View.VISIBLE
                } else {
                    deviceVendor.visibility = View.GONE
                }

                // Status indicator
                val statusColor = if (device.isOnline) {
                    ContextCompat.getColor(context, R.color.device_online)
                } else {
                    ContextCompat.getColor(context, R.color.device_offline)
                }
                statusIndicator.setColorFilter(statusColor)

                // Accessibility for status indicator
                val statusDesc = if (device.isOnline) {
                    context.getString(R.string.cd_device_online)
                } else {
                    context.getString(R.string.cd_device_offline)
                }
                statusIndicator.contentDescription = statusDesc

                // Latency
                if (device.latencyMs != null && device.isOnline) {
                    deviceLatency.text = "${device.latencyMs}ms"
                    deviceLatency.visibility = View.VISIBLE
                } else {
                    deviceLatency.visibility = View.GONE
                }

                // Accessibility content description for the whole card
                val deviceTypeDesc = device.deviceType.displayName
                val statusText = if (device.isOnline) {
                    context.getString(R.string.status_online)
                } else {
                    context.getString(R.string.status_offline)
                }
                root.contentDescription = context.getString(
                    R.string.cd_device_item,
                    device.displayName,
                    statusText,
                    device.ipAddress
                )

                // Add "this is your device" to accessibility if applicable
                if (device.isCurrentDevice) {
                    root.contentDescription = "${root.contentDescription}. ${context.getString(R.string.cd_device_current)}"
                }

                // Click listener
                root.setOnClickListener {
                    onDeviceClick(device)
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return when {
                oldItem is ListItem.Header && newItem is ListItem.Header ->
                    oldItem.title == newItem.title
                oldItem is ListItem.DeviceItem && newItem is ListItem.DeviceItem ->
                    oldItem.device.uniqueId == newItem.device.uniqueId
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return oldItem == newItem
        }
    }
}

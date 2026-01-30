package com.networkscanner.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.networkscanner.app.R
import com.networkscanner.app.data.PortInfo
import com.networkscanner.app.databinding.ItemPortBinding

class PortAdapter : ListAdapter<PortInfo, PortAdapter.PortViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PortViewHolder {
        val binding = ItemPortBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PortViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PortViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PortViewHolder(
        private val binding: ItemPortBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(port: PortInfo) {
            binding.apply {
                val context = root.context

                portNumber.text = port.port.toString()
                serviceName.text = port.displayName
                protocolBadge.text = port.protocol

                // Show version if available
                if (!port.version.isNullOrBlank()) {
                    serviceVersion.text = port.version
                    serviceVersion.visibility = View.VISIBLE
                } else if (!port.banner.isNullOrBlank()) {
                    // Show truncated banner if no version but banner exists
                    serviceVersion.text = port.banner.take(50).replace("\n", " ")
                    serviceVersion.visibility = View.VISIBLE
                } else {
                    serviceVersion.visibility = View.GONE
                }

                // Accessibility content description
                root.contentDescription = context.getString(
                    R.string.cd_port_status,
                    port.port,
                    "open, ${port.displayName}"
                )
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PortInfo>() {
        override fun areItemsTheSame(oldItem: PortInfo, newItem: PortInfo): Boolean {
            return oldItem.port == newItem.port && oldItem.protocol == newItem.protocol
        }

        override fun areContentsTheSame(oldItem: PortInfo, newItem: PortInfo): Boolean {
            return oldItem == newItem
        }
    }
}

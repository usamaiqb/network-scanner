package com.networkscanner.app.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.networkscanner.app.R
import com.networkscanner.app.data.DeepScanProgress
import com.networkscanner.app.data.Device
import com.networkscanner.app.databinding.ActivityDeviceDetailBinding
import com.networkscanner.app.theme.ThemeManager

class DeviceDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DEVICE = "extra_device"
    }

    private lateinit var binding: ActivityDeviceDetailBinding
    private val viewModel: DeviceDetailViewModel by viewModels()
    private lateinit var portAdapter: PortAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager.initialize(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityDeviceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupPortsRecyclerView()
        setupClickListeners()
        observeViewModel()

        // Get device from intent
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DEVICE, Device::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DEVICE)
        }

        if (device != null) {
            viewModel.setDevice(device)
            displayDeviceInfo(device)
        } else {
            finish()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.toolbar.navigationContentDescription = getString(R.string.cd_navigate_back)

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh -> {
                    viewModel.startDeepScan()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupPortsRecyclerView() {
        portAdapter = PortAdapter()
        binding.portsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DeviceDetailActivity)
            adapter = portAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        binding.startScanButton.apply {
            setOnClickListener { viewModel.startDeepScan() }
            contentDescription = getString(R.string.cd_start_deep_scan)
        }

        binding.refreshScanButton.apply {
            setOnClickListener { viewModel.startDeepScan() }
            contentDescription = getString(R.string.cd_start_deep_scan)
        }

        binding.cancelScanButton.apply {
            setOnClickListener { viewModel.cancelDeepScan() }
            contentDescription = getString(R.string.cd_cancel_scan)
        }
    }

    private fun observeViewModel() {
        viewModel.device.observe(this) { device ->
            displayDeviceInfo(device)
        }

        viewModel.deepScanState.observe(this) { state ->
            when (state) {
                is DeviceDetailViewModel.DeepScanState.Idle -> showIdleState()
                is DeviceDetailViewModel.DeepScanState.Scanning -> showScanningState()
                is DeviceDetailViewModel.DeepScanState.Completed -> showResultsState()
                is DeviceDetailViewModel.DeepScanState.Error -> showErrorState(state.message)
            }
        }

        viewModel.deepScanProgress.observe(this) { progress ->
            updateScanProgress(progress)
        }

        viewModel.deepScanResult.observe(this) { result ->
            if (result != null) {
                // Update ports list
                portAdapter.submitList(result.openPorts)

                // Show/hide no ports state
                binding.noPortsState.visibility = if (result.openPorts.isEmpty()) View.VISIBLE else View.GONE
                binding.portsRecyclerView.visibility = if (result.openPorts.isNotEmpty()) View.VISIBLE else View.GONE

                // OS Detection
                if (result.detectedOs != null) {
                    binding.osDetectionRow.visibility = View.VISIBLE
                    binding.detectedOs.text = result.detectedOs.name
                    binding.osConfidence.text = getString(R.string.confidence_percent, result.detectedOs.confidence)
                } else {
                    binding.osDetectionRow.visibility = View.GONE
                }

                // Scan duration
                val durationSec = result.scanDurationMs / 1000.0
                binding.scanDuration.text = getString(R.string.duration_seconds, durationSec)
            }
        }

        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun displayDeviceInfo(device: Device) {
        // Header
        binding.toolbar.title = device.displayName
        binding.deviceName.text = device.displayName
        binding.deviceType.text = device.deviceType.displayName
        binding.deviceIcon.setImageResource(device.deviceType.iconRes)

        // Status chip
        if (device.isOnline) {
            binding.statusChip.text = getString(R.string.status_online)
            binding.statusChip.setChipBackgroundColorResource(R.color.device_online)
            binding.statusChip.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        } else {
            binding.statusChip.text = getString(R.string.status_offline)
            binding.statusChip.setChipBackgroundColorResource(R.color.device_offline)
            binding.statusChip.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }

        // Basic info
        binding.ipAddress.text = device.ipAddress

        if (device.macAddress != null) {
            binding.macAddress.text = device.macAddress.uppercase()
            binding.macAddressRow.visibility = View.VISIBLE
        } else {
            binding.macAddressRow.visibility = View.GONE
        }

        if (device.vendor != null) {
            binding.vendor.text = device.vendor
            binding.vendorRow.visibility = View.VISIBLE
        } else {
            binding.vendorRow.visibility = View.GONE
        }

        if (device.latencyMs != null && device.isOnline) {
            binding.latency.text = getString(R.string.latency_ms, device.latencyMs)
            binding.latencyRow.visibility = View.VISIBLE
        } else {
            binding.latencyRow.visibility = View.GONE
        }

        // Network info
        if (device.hostname != null) {
            binding.hostname.text = device.hostname
            binding.hostnameRow.visibility = View.VISIBLE
        } else {
            binding.hostnameRow.visibility = View.GONE
        }

        // Services
        if (device.mdnsServices.isNotEmpty()) {
            binding.servicesRow.visibility = View.VISIBLE
            binding.servicesChipGroup.removeAllViews()
            device.mdnsServices.forEach { service ->
                val chip = Chip(this).apply {
                    text = service.removePrefix("_").removeSuffix("._tcp.").removeSuffix("._udp.")
                    isCheckable = false
                    isClickable = false
                }
                binding.servicesChipGroup.addView(chip)
            }
        } else {
            binding.servicesRow.visibility = View.GONE
        }

        // Discovery method
        binding.discoveredVia.text = device.discoveredVia.name.replace("_", " ")
            .lowercase()
            .replaceFirstChar { it.uppercase() }

        // Show offline message in deep scan if device is offline
        if (!device.isOnline) {
            binding.idleStateText.text = getString(R.string.device_offline)
            binding.startScanButton.isEnabled = false
            binding.refreshScanButton.isEnabled = false
        }
    }

    private fun showIdleState() {
        binding.scanProgress.visibility = View.GONE
        binding.scanningState.visibility = View.GONE
        binding.resultsState.visibility = View.GONE
        binding.idleState.visibility = View.VISIBLE
    }

    private fun showScanningState() {
        binding.scanProgress.visibility = View.VISIBLE
        binding.scanningState.visibility = View.VISIBLE
        binding.resultsState.visibility = View.GONE
        binding.idleState.visibility = View.GONE
    }

    private fun showResultsState() {
        binding.scanProgress.visibility = View.GONE
        binding.scanningState.visibility = View.GONE
        binding.resultsState.visibility = View.VISIBLE
        binding.idleState.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        binding.scanProgress.visibility = View.GONE
        binding.scanningState.visibility = View.GONE
        binding.resultsState.visibility = View.GONE
        binding.idleState.visibility = View.VISIBLE
        binding.idleStateText.text = message

        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_retry) {
                viewModel.startDeepScan()
            }
            .show()
    }

    private fun updateScanProgress(progress: DeepScanProgress) {
        binding.scanProgress.progress = (progress.progress * 100).toInt()
        binding.scanStatusText.text = progress.message

        val progressText = buildString {
            append("${progress.portsScanned}/${progress.portsTotal} ports")
            if (progress.openPortsFound > 0) {
                append(" \u2022 ${progress.openPortsFound} open")
            }
        }
        binding.scanProgressText.text = progressText
    }
}

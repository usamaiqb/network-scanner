package com.networkscanner.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.networkscanner.app.R
import com.networkscanner.app.data.Device
import com.networkscanner.app.databinding.ActivityMainBinding
import com.networkscanner.app.theme.ThemeManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceAdapter

    // Track if we've performed at least one scan
    private var hasScannedOnce = false

    companion object {
        private const val PREFS_NAME = "network_scanner_prefs"
        private const val KEY_PERMISSIONS_REQUESTED = "permissions_requested"
    }

    private val optionalPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions are optional - mark as requested and continue normally
        markPermissionsRequested()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply dynamic colors
        ThemeManager.initialize(this)

        // Edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()

        setupClickListeners()
        observeViewModel()

        // Request permissions on first launch (optional - won't block functionality)
        if (!hasRequestedPermissions()) {
            requestOptionalPermissions()
        }

        // Show initial state or start auto-scan
        if (isAutoScanEnabled()) {
            viewModel.startScan()
        } else {
            showInitialState()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    openSettings()
                    true
                }
                else -> false
            }
        }
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun isAutoScanEnabled(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getBoolean(SettingsActivity.KEY_AUTO_SCAN, true)
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { device ->
            showDeviceDetails(device)
        }

        binding.deviceList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
            setHasFixedSize(false)

            // Enable item change animations
            itemAnimator?.apply {
                addDuration = 200
                removeDuration = 200
                changeDuration = 200
            }
        }
    }

    private fun runLayoutAnimation() {
        val controller = AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down)
        binding.deviceList.layoutAnimation = controller
        binding.deviceList.scheduleLayoutAnimation()
    }



    private fun setupClickListeners() {
        // Initial scan button (centered, shown before first scan)
        binding.initialScanButton.setOnClickListener {
            requestScan()
        }

        // Extended FAB for refresh (shown after first scan)
        binding.scanFab.setOnClickListener {
            requestScan()
        }

        // Cancel button during scan
        binding.cancelScanButton.setOnClickListener {
            viewModel.cancelScan()
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is MainViewModel.UiState.Idle -> {
                    showIdleState()
                }
                is MainViewModel.UiState.Scanning -> {
                    showScanningState()
                }
                is MainViewModel.UiState.NoWifi -> {
                    showNoWifiState()
                }
                is MainViewModel.UiState.Empty -> {
                    showEmptyResultState()
                }
                is MainViewModel.UiState.Success -> {
                    showSuccessState(state.result.devices.size)
                }
                is MainViewModel.UiState.Error -> {
                    showErrorState(state.message)
                }
            }
        }

        viewModel.onlineDevices.observe(this) { online ->
            val offline = viewModel.offlineDevices.value ?: emptyList()
            val wasEmpty = deviceAdapter.itemCount == 0
            deviceAdapter.submitDevices(online, offline)
            if (wasEmpty && (online.isNotEmpty() || offline.isNotEmpty())) {
                runLayoutAnimation()
            }
        }

        viewModel.offlineDevices.observe(this) { offline ->
            val online = viewModel.onlineDevices.value ?: emptyList()
            val wasEmpty = deviceAdapter.itemCount == 0
            deviceAdapter.submitDevices(online, offline)
            if (wasEmpty && (online.isNotEmpty() || offline.isNotEmpty())) {
                runLayoutAnimation()
            }
        }

        viewModel.scanProgress.observe(this) { progress ->
            updateScanProgress(progress)
        }

        viewModel.networkInfo.observe(this) { info ->
            if (info != null) {
                binding.networkInfoBar.visibility = View.VISIBLE
                binding.networkSsid.text = info.ssid ?: "Unknown Network"
                binding.networkSubnet.text = info.cidrNotation
            } else {
                binding.networkInfoBar.visibility = View.GONE
            }
        }

        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun showInitialState() {

        binding.scanStatusCard.visibility = View.GONE
        binding.scanProgress.visibility = View.GONE
        binding.initialState.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        binding.scanFab.hide()

        // Animate the initial state
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        binding.initialState.startAnimation(fadeIn)

        val scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up)
        binding.initialIcon.startAnimation(scaleUp)
    }

    private fun showIdleState() {

        binding.scanStatusCard.visibility = View.GONE
        binding.scanProgress.visibility = View.GONE

        if (hasScannedOnce) {
            // Show the refresh FAB after first scan
            binding.initialState.visibility = View.GONE
            binding.scanFab.show()
        } else {
            // Show initial state if no scan performed yet
            showInitialState()
        }
    }

    private fun showScanningState() {
        hasScannedOnce = true

        binding.scanStatusCard.visibility = View.VISIBLE
        binding.scanProgress.visibility = View.VISIBLE
        binding.initialState.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.scanFab.hide()
    }

    private fun showNoWifiState() {

        binding.scanStatusCard.visibility = View.GONE
        binding.scanProgress.visibility = View.GONE
        binding.initialState.visibility = View.GONE
        binding.emptyIcon.setImageResource(R.drawable.ic_wifi_off)
        binding.emptyTitle.text = getString(R.string.no_wifi_title)
        binding.emptyMessage.text = getString(R.string.no_wifi_message)
        showEmptyStateWithAnimation()

        if (hasScannedOnce) {
            binding.scanFab.show()
        }
    }

    private fun showEmptyResultState() {

        binding.scanStatusCard.visibility = View.GONE
        binding.scanProgress.visibility = View.GONE
        binding.initialState.visibility = View.GONE
        binding.emptyIcon.setImageResource(R.drawable.ic_device_unknown)
        binding.emptyTitle.text = getString(R.string.no_devices_title)
        binding.emptyMessage.text = getString(R.string.no_devices_message)
        showEmptyStateWithAnimation()
        binding.scanFab.show()
    }

    private fun showEmptyStateWithAnimation() {
        if (binding.emptyState.visibility != View.VISIBLE) {
            binding.emptyState.visibility = View.VISIBLE
            val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
            binding.emptyState.startAnimation(fadeIn)

            // Add subtle scale animation to the icon
            val scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up)
            binding.emptyIcon.startAnimation(scaleUp)
        }
    }

    private fun showSuccessState(deviceCount: Int) {

        binding.scanStatusCard.visibility = View.GONE
        binding.scanProgress.visibility = View.GONE
        binding.initialState.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.scanFab.show()

        Snackbar.make(
            binding.root,
            getString(R.string.devices_found, deviceCount),
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun showErrorState(message: String) {

        binding.scanStatusCard.visibility = View.GONE
        binding.scanProgress.visibility = View.GONE

        if (hasScannedOnce) {
            binding.scanFab.show()
        } else {
            binding.initialState.visibility = View.VISIBLE
        }

        Snackbar.make(
            binding.root,
            getString(R.string.error_scan_failed, message),
            Snackbar.LENGTH_LONG
        ).setAction(R.string.action_retry) {
            requestScan()
        }.show()
    }

    private fun updateScanProgress(progress: com.networkscanner.app.data.ScanProgress) {
        binding.scanProgress.progress = (progress.progress * 100).toInt()
        binding.scanStatusText.text = progress.message
        binding.scanDeviceCount.text = getString(R.string.devices_found, progress.devicesFound)
    }

    private fun requestScan() {
        // Start scan directly - permissions are optional
        startScan()
    }

    private fun hasRequestedPermissions(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getBoolean(KEY_PERMISSIONS_REQUESTED, false)
    }

    private fun markPermissionsRequested() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PERMISSIONS_REQUESTED, true).apply()
    }

    private fun requestOptionalPermissions() {
        // Show rationale dialog explaining why permissions help, but don't block if denied
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_location_title)
            .setMessage(R.string.permission_optional_message)
            .setPositiveButton(R.string.grant_permission) { _, _ ->
                permissionLauncher.launch(optionalPermissions)
            }
            .setNegativeButton(R.string.skip) { _, _ ->
                markPermissionsRequested()
            }
            .setCancelable(false)
            .show()
    }

    private fun startScan() {
        viewModel.startScan()
    }

    private fun showDeviceDetails(device: Device) {
        val intent = Intent(this, DeviceDetailActivity::class.java).apply {
            putExtra(DeviceDetailActivity.EXTRA_DEVICE, device)
        }
        startActivity(intent)
    }
}

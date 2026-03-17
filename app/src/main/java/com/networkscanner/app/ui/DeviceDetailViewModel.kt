package com.networkscanner.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.networkscanner.app.NetworkScannerApp
import com.networkscanner.app.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DeviceDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = (application as NetworkScannerApp).scanner

    // Device info
    private val _device = MutableStateFlow<Device?>(null)
    val device: StateFlow<Device?> = _device.asStateFlow()

    // Deep scan state
    private val _deepScanState = MutableStateFlow<DeepScanState>(DeepScanState.Idle)
    val deepScanState: StateFlow<DeepScanState> = _deepScanState.asStateFlow()

    // Deep scan result
    private val _deepScanResult = MutableStateFlow<DeepScanResult?>(null)
    val deepScanResult: StateFlow<DeepScanResult?> = _deepScanResult.asStateFlow()

    // Deep scan progress
    private val _deepScanProgress = MutableStateFlow(DeepScanProgress())
    val deepScanProgress: StateFlow<DeepScanProgress> = _deepScanProgress.asStateFlow()

    private var deepScanJob: Job? = null
    private var progressJob: Job? = null
    private var hasScannedOnce = false

    sealed class DeepScanState {
        data object Idle : DeepScanState()
        data object Scanning : DeepScanState()
        data object Completed : DeepScanState()
        data class Error(val message: String) : DeepScanState()
    }

    fun loadDevice(deviceId: String, devices: List<Device>) {
        val device = devices.find { it.uniqueId == deviceId }
        _device.value = device

        if (device != null && !hasScannedOnce && device.isOnline) {
            startDeepScan()
        }
    }

    fun startDeepScan() {
        val currentDevice = _device.value ?: return

        deepScanJob?.cancel()
        progressJob?.cancel()

        _deepScanState.value = DeepScanState.Scanning
        _deepScanResult.value = null
        hasScannedOnce = true

        progressJob = viewModelScope.launch {
            scanner.deepScanProgress.collectLatest { progress ->
                _deepScanProgress.value = progress
            }
        }

        deepScanJob = viewModelScope.launch {
            try {
                val result = scanner.performDeepScan(currentDevice.ipAddress)

                when (result.status) {
                    DeepScanStatus.COMPLETED -> {
                        _deepScanResult.value = result
                        _deepScanState.value = DeepScanState.Completed
                    }
                    DeepScanStatus.CANCELLED -> {
                        _deepScanResult.value = result
                        _deepScanState.value = DeepScanState.Idle
                    }
                    DeepScanStatus.FAILED -> {
                        _deepScanResult.value = result
                        _deepScanState.value = DeepScanState.Error("Scan failed")
                    }
                    else -> {
                        _deepScanResult.value = result
                        _deepScanState.value = DeepScanState.Idle
                    }
                }
            } catch (e: Exception) {
                _deepScanState.value = DeepScanState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun cancelDeepScan() {
        deepScanJob?.cancel()
        progressJob?.cancel()
        scanner.cancel()
        _deepScanState.value = DeepScanState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        deepScanJob?.cancel()
        progressJob?.cancel()
    }
}

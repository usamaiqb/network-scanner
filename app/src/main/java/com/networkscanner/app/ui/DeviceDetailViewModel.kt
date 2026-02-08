package com.networkscanner.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.networkscanner.app.NetworkScannerApp
import com.networkscanner.app.data.*
import com.networkscanner.app.network.NetworkScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DeviceDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = (application as NetworkScannerApp).scanner

    // Device info
    private val _device = MutableLiveData<Device>()
    val device: LiveData<Device> = _device

    // Deep scan state
    private val _deepScanState = MutableLiveData<DeepScanState>(DeepScanState.Idle)
    val deepScanState: LiveData<DeepScanState> = _deepScanState

    // Deep scan result
    private val _deepScanResult = MutableLiveData<DeepScanResult?>()
    val deepScanResult: LiveData<DeepScanResult?> = _deepScanResult

    // Deep scan progress
    private val _deepScanProgress = MutableLiveData<DeepScanProgress>()
    val deepScanProgress: LiveData<DeepScanProgress> = _deepScanProgress

    // Error message
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var deepScanJob: Job? = null
    private var hasScannedOnce = false

    sealed class DeepScanState {
        object Idle : DeepScanState()
        object Scanning : DeepScanState()
        object Completed : DeepScanState()
        data class Error(val message: String) : DeepScanState()
    }

    fun setDevice(device: Device) {
        _device.value = device

        // Auto-trigger deep scan on first view
        if (!hasScannedOnce && device.isOnline) {
            startDeepScan()
        }
    }

    fun startDeepScan() {
        val currentDevice = _device.value ?: return

        // Cancel any existing scan
        deepScanJob?.cancel()

        _deepScanState.value = DeepScanState.Scanning
        _deepScanResult.value = null
        hasScannedOnce = true

        // Collect progress updates
        viewModelScope.launch {
            scanner.deepScanProgress.collectLatest { progress ->
                _deepScanProgress.postValue(progress)
            }
        }

        deepScanJob = viewModelScope.launch {
            try {
                val result = scanner.performDeepScan(currentDevice.ipAddress)

                when (result.status) {
                    DeepScanStatus.COMPLETED -> {
                        _deepScanResult.postValue(result)
                        _deepScanState.postValue(DeepScanState.Completed)
                    }
                    DeepScanStatus.CANCELLED -> {
                        _deepScanResult.postValue(result)
                        _deepScanState.postValue(DeepScanState.Idle)
                    }
                    DeepScanStatus.FAILED -> {
                        _deepScanResult.postValue(result)
                        _deepScanState.postValue(DeepScanState.Error("Scan failed"))
                    }
                    else -> {
                        _deepScanResult.postValue(result)
                        _deepScanState.postValue(DeepScanState.Idle)
                    }
                }
            } catch (e: Exception) {
                _deepScanState.postValue(DeepScanState.Error(e.message ?: "Unknown error"))
                _errorMessage.postValue(e.message)
            }
        }
    }

    fun cancelDeepScan() {
        deepScanJob?.cancel()
        scanner.cancel()
        _deepScanState.value = DeepScanState.Idle
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        deepScanJob?.cancel()
    }
}

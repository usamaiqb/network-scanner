package com.networkscanner.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.networkscanner.app.data.*
import com.networkscanner.app.network.NetworkScanner
import com.networkscanner.app.util.NetworkUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for the main device list screen.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = NetworkScanner(application)

    private val _uiState = MutableLiveData<UiState>(UiState.Idle)
    val uiState: LiveData<UiState> = _uiState

    private val _devices = MutableLiveData<List<Device>>(emptyList())
    val devices: LiveData<List<Device>> = _devices

    private val _onlineDevices = MutableLiveData<List<Device>>(emptyList())
    val onlineDevices: LiveData<List<Device>> = _onlineDevices

    private val _offlineDevices = MutableLiveData<List<Device>>(emptyList())
    val offlineDevices: LiveData<List<Device>> = _offlineDevices

    private val _scanProgress = MutableLiveData<ScanProgress>()
    val scanProgress: LiveData<ScanProgress> = _scanProgress

    private val _networkInfo = MutableLiveData<NetworkInfo?>()
    val networkInfo: LiveData<NetworkInfo?> = _networkInfo

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        // Collect device updates from scanner
        viewModelScope.launch {
            scanner.devices.collectLatest { deviceList ->
                updateDeviceLists(deviceList)
            }
        }

        viewModelScope.launch {
            scanner.scanProgress.collectLatest { progress ->
                _scanProgress.value = progress
            }
        }
    }

    /**
     * Check if WiFi is connected.
     */
    fun isWifiConnected(): Boolean {
        return NetworkUtils.isWifiConnected(getApplication())
    }

    /**
     * Start network scan.
     */
    fun startScan() {
        if (_uiState.value is UiState.Scanning) return

        viewModelScope.launch {
            if (!isWifiConnected()) {
                _uiState.value = UiState.NoWifi
                return@launch
            }

            _uiState.value = UiState.Scanning
            _errorMessage.value = null

            try {
                val result = scanner.scan()

                _networkInfo.value = result.networkInfo
                updateDeviceLists(result.devices)

                when (result.scanStatus) {
                    ScanStatus.COMPLETED -> {
                        if (result.devices.isEmpty()) {
                            _uiState.value = UiState.Empty
                        } else {
                            _uiState.value = UiState.Success(result)
                        }
                    }
                    ScanStatus.ERROR -> {
                        _uiState.value = UiState.Error(result.error ?: "Unknown error")
                        _errorMessage.value = result.error
                    }
                    else -> {
                        _uiState.value = UiState.Idle
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Scan failed")
                _errorMessage.value = e.message
            }
        }
    }

    /**
     * Cancel ongoing scan.
     */
    fun cancelScan() {
        scanner.cancel()
        _uiState.value = UiState.Idle
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Get device by IP address.
     */
    fun getDeviceByIp(ipAddress: String): Device? {
        return _devices.value?.find { it.ipAddress == ipAddress }
    }

    private fun updateDeviceLists(deviceList: List<Device>) {
        _devices.value = deviceList

        // Sort: current device first, then by IP
        val sorted = deviceList.sortedWith(
            compareBy({ !it.isCurrentDevice }, { !it.isOnline }, { it.ipAddress })
        )

        _onlineDevices.value = sorted.filter { it.isOnline }
        _offlineDevices.value = sorted.filter { !it.isOnline }
    }

    override fun onCleared() {
        super.onCleared()
        scanner.cleanup()
    }

    /**
     * UI state sealed class.
     */
    sealed class UiState {
        data object Idle : UiState()
        data object Scanning : UiState()
        data object NoWifi : UiState()
        data object Empty : UiState()
        data class Success(val result: ScanResult) : UiState()
        data class Error(val message: String) : UiState()
    }
}

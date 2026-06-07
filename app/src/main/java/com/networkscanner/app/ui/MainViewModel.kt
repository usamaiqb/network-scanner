package com.networkscanner.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.networkscanner.app.NetworkScannerApp
import com.networkscanner.app.data.*
import com.networkscanner.app.util.NetworkInterfaceOption
import com.networkscanner.app.util.NetworkUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the main device list screen.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = (application as NetworkScannerApp).scanner

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _onlineDevices = MutableStateFlow<List<Device>>(emptyList())
    val onlineDevices: StateFlow<List<Device>> = _onlineDevices.asStateFlow()

    private val _offlineDevices = MutableStateFlow<List<Device>>(emptyList())
    val offlineDevices: StateFlow<List<Device>> = _offlineDevices.asStateFlow()

    private val _scanProgress = MutableStateFlow(ScanProgress(ScanPhase.INITIALIZING, 0f, "", 0))
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val _networkInfo = MutableStateFlow<NetworkInfo?>(null)
    val networkInfo: StateFlow<NetworkInfo?> = _networkInfo.asStateFlow()

    private val _availableInterfaces = MutableStateFlow<List<NetworkInterfaceOption>>(emptyList())
    val availableInterfaces: StateFlow<List<NetworkInterfaceOption>> = _availableInterfaces.asStateFlow()

    private val _selectedInterfaceName = MutableStateFlow<String?>(null)
    val selectedInterfaceName: StateFlow<String?> = _selectedInterfaceName.asStateFlow()

    private val _errorMessage = Channel<String>(Channel.BUFFERED)
    val errorMessage = _errorMessage.receiveAsFlow()

    init {
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

        refreshInterfaces()
    }

    /**
     * Refresh active network interfaces and keep selection valid.
     */
    fun refreshInterfaces() {
        val interfaces = NetworkUtils.getAvailableInterfaces()
        _availableInterfaces.value = interfaces

        val current = _selectedInterfaceName.value
        _selectedInterfaceName.value = when {
            interfaces.isEmpty() -> null
            current != null && interfaces.any { it.name == current } -> current
            else -> interfaces.first().name
        }

        _networkInfo.value = _networkInfo.value
            ?: NetworkUtils.getNetworkInfo(getApplication(), _selectedInterfaceName.value)
    }

    fun onInterfaceSelected(interfaceName: String) {
        _selectedInterfaceName.value = interfaceName
        _networkInfo.value = NetworkUtils.getNetworkInfo(getApplication(), interfaceName)
    }

    /**
     * Start network scan.
     */
    fun startScan() {
        if (_uiState.value is UiState.Scanning) return

        viewModelScope.launch {
            refreshInterfaces()
            val interfaceName = _selectedInterfaceName.value
            if (interfaceName == null) {
                _uiState.value = UiState.NoWifi
                return@launch
            }

            _uiState.value = UiState.Scanning

            try {
                val result = scanner.scan(interfaceName)

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
                        _errorMessage.trySend(result.error ?: "Unknown error")
                    }
                    else -> {
                        _uiState.value = UiState.Idle
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Scan failed")
                _errorMessage.trySend(e.message ?: "Scan failed")
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
     * Get device by unique ID (MAC or IP).
     */
    fun getDeviceById(id: String): Device? {
        return _devices.value.find { it.uniqueId == id }
    }

    private fun updateDeviceLists(deviceList: List<Device>) {
        _devices.value = deviceList

        val sorted = deviceList.sortedWith(
           compareBy(
              { !it.isCurrentDevice },
              { !it.isOnline },
              { it.ipAddress.split('.').fold(0L) { acc, part -> (acc shl 8) or part.toLong() } }
           )
        )

        _onlineDevices.value = sorted.filter { it.isOnline }
        _offlineDevices.value = sorted.filter { !it.isOnline }
    }

    override fun onCleared() {
        super.onCleared()
        scanner.cancel()
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

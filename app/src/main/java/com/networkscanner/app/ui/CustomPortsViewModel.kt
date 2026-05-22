package com.networkscanner.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.networkscanner.app.NetworkScannerApp
import com.networkscanner.app.data.repository.CustomPortData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class CustomPortsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as NetworkScannerApp).customPortRepository

    val ports: StateFlow<List<CustomPortData>> = repository.ports
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addPort(port: Int, serviceName: String) {
        repository.addPort(port, serviceName)
    }

    fun deletePort(id: Int) {
        repository.deletePort(id)
    }

    fun togglePort(id: Int, enabled: Boolean) {
        repository.setEnabled(id, enabled)
    }
}

package com.networkscanner.app.data.repository

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CustomPortData(
    val id: Int,
    val port: Int,
    val serviceName: String,
    val isEnabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)

class CustomPortRepository(private val context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val json = Json { ignoreUnknownKeys = true }

    private val _ports = MutableStateFlow<List<CustomPortData>>(loadAll())
    val ports: Flow<List<CustomPortData>> = _ports.asStateFlow()
    val enabledPorts: Flow<List<CustomPortData>> = _ports.map { list -> list.filter { it.isEnabled } }

    private fun loadAll(): List<CustomPortData> {
        val raw = prefs.getString(KEY_PORTS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<CustomPortData>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAll(list: List<CustomPortData>) {
        prefs.edit { putString(KEY_PORTS, json.encodeToString(list)) }
        _ports.value = list
    }

    fun addPort(port: Int, serviceName: String) {
        val current = _ports.value.toMutableList()
        val existing = current.indexOfFirst { it.port == port }
        val newId = (current.maxOfOrNull { it.id } ?: 0) + 1
        val newPort = CustomPortData(id = newId, port = port, serviceName = serviceName)
        if (existing >= 0) {
            current[existing] = newPort.copy(id = current[existing].id)
        } else {
            current.add(newPort)
        }
        saveAll(current.sortedBy { it.port })
    }

    fun deletePort(id: Int) {
        saveAll(_ports.value.filter { it.id != id })
    }

    fun setEnabled(id: Int, enabled: Boolean) {
        saveAll(_ports.value.map { if (it.id == id) it.copy(isEnabled = enabled) else it })
    }

    fun getEnabledPorts(): List<CustomPortData> {
        return _ports.value.filter { it.isEnabled }
    }

    companion object {
        private const val KEY_PORTS = "custom_ports"
    }
}

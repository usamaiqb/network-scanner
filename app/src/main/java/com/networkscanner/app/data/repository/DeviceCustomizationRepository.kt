package com.networkscanner.app.data.repository

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DeviceCustomizationData(
    val deviceId: String,
    val customName: String? = null,
    val customIcon: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

class DeviceCustomizationRepository(private val context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val json = Json { ignoreUnknownKeys = true }

    private val _customizations = MutableStateFlow<Map<String, DeviceCustomizationData>>(loadAll())
    val customizations: Flow<Map<String, DeviceCustomizationData>> = _customizations.asStateFlow()

    private fun loadAll(): Map<String, DeviceCustomizationData> {
        val raw = prefs.getString(KEY_CUSTOMIZATIONS, null) ?: return emptyMap()
        return try {
            val list = json.decodeFromString<List<DeviceCustomizationData>>(raw)
            list.associateBy { it.deviceId }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveAll(map: Map<String, DeviceCustomizationData>) {
        val list = map.values.toList()
        prefs.edit { putString(KEY_CUSTOMIZATIONS, json.encodeToString(list)) }
        _customizations.value = map
    }

    fun getCustomization(deviceId: String): DeviceCustomizationData? {
        return _customizations.value[deviceId]
    }

    fun saveCustomization(deviceId: String, customName: String?, customIcon: String? = null) {
        val current = _customizations.value.toMutableMap()
        val existing = current[deviceId]
        val newName = customName?.takeIf { it.isNotBlank() }
        val newIcon = customIcon ?: existing?.customIcon // null arg = preserve existing icon
        if (newName == null && newIcon == null) {
            if (existing == null) return
            current.remove(deviceId)
        } else {
            current[deviceId] = DeviceCustomizationData(
                deviceId = deviceId,
                customName = newName,
                customIcon = newIcon,
                lastUpdated = System.currentTimeMillis()
            )
        }
        saveAll(current)
    }

    fun saveCustomIcon(deviceId: String, iconKey: String?) {
        val current = _customizations.value.toMutableMap()
        val existing = current[deviceId]
        if (existing != null) {
            current[deviceId] = existing.copy(customIcon = iconKey, lastUpdated = System.currentTimeMillis())
        } else if (iconKey != null) {
            current[deviceId] = DeviceCustomizationData(deviceId = deviceId, customIcon = iconKey)
        }
        saveAll(current)
    }

    fun deleteCustomization(deviceId: String) {
        val current = _customizations.value.toMutableMap()
        current.remove(deviceId)
        saveAll(current)
    }

    companion object {
        private const val KEY_CUSTOMIZATIONS = "device_customizations"
    }
}

package com.networkscanner.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.networkscanner.app.theme.ThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val KEY_AUTO_SCAN = "auto_scan_on_start"
    }

    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)

    val themeMode: StateFlow<ThemeManager.ThemeMode> = ThemeManager.themeModeFlow

    val dynamicColors: StateFlow<Boolean> = ThemeManager.dynamicColorsFlow

    private val _autoScan = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SCAN, true))
    val autoScan: StateFlow<Boolean> = _autoScan.asStateFlow()

    fun setThemeMode(mode: ThemeManager.ThemeMode) {
        ThemeManager.setThemeMode(getApplication(), mode)
    }

    fun setDynamicColors(enabled: Boolean) {
        ThemeManager.setDynamicColorsEnabled(getApplication(), enabled)
    }

    fun setAutoScan(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SCAN, enabled).apply()
        _autoScan.value = enabled
    }

    fun supportsDynamicColors(): Boolean = ThemeManager.supportsDynamicColors()
}

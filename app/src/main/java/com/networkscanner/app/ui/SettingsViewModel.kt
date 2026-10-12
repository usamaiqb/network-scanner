package com.networkscanner.app.ui

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.networkscanner.app.theme.ThemeManager
import com.networkscanner.app.util.PrivilegedNeighborSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val KEY_AUTO_SCAN = "auto_scan_on_start"

        /**
         * Current in-app language: "system" to follow the device, or a language
         * tag such as "en" / "ru". Backed by AppCompat's per-app locales, which
         * (with autoStoreLocales) persists and restores the selection across all
         * supported API levels.
         */
        fun getCurrentLanguage(): String {
            val locales = AppCompatDelegate.getApplicationLocales()
            return if (locales.isEmpty) "system" else locales[0]?.language ?: "system"
        }

        /**
         * Apply a language. AppCompat reloads resources and recreates the active
         * Activity once (the standard, animated configuration change) — callers
         * must not call Activity.recreate() themselves.
         */
        fun applyLanguage(languageCode: String) {
            val localeList = if (languageCode == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageCode)
            }
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }

    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)

    val themeMode: StateFlow<ThemeManager.ThemeMode> = ThemeManager.themeModeFlow

    val dynamicColors: StateFlow<Boolean> = ThemeManager.dynamicColorsFlow

    private val _autoScan = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SCAN, true))
    val autoScan: StateFlow<Boolean> = _autoScan.asStateFlow()

    private val _language = MutableStateFlow(getCurrentLanguage())
    val language: StateFlow<String> = _language.asStateFlow()

    private val _neighborSource = MutableStateFlow(PrivilegedNeighborSource.mode(application))
    val neighborSource: StateFlow<PrivilegedNeighborSource.Mode> = _neighborSource.asStateFlow()
    private val _neighborAvailability =
        MutableStateFlow<Map<PrivilegedNeighborSource.Mode, PrivilegedNeighborSource.Availability>>(emptyMap())
    val neighborAvailability: StateFlow<Map<PrivilegedNeighborSource.Mode, PrivilegedNeighborSource.Availability>> =
        _neighborAvailability.asStateFlow()
    private val _neighborProbe = MutableStateFlow<NeighborProbe>(NeighborProbe.Idle)
    val neighborProbe: StateFlow<NeighborProbe> = _neighborProbe.asStateFlow()
    private var probeJob: Job? = null

    fun setThemeMode(mode: ThemeManager.ThemeMode) {
        ThemeManager.setThemeMode(getApplication(), mode)
    }

    fun setDynamicColors(enabled: Boolean) {
        ThemeManager.setDynamicColorsEnabled(getApplication(), enabled)
    }

    fun setAutoScan(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_SCAN, enabled) }
        _autoScan.value = enabled
    }

    fun setLanguage(languageCode: String) {
        _language.value = languageCode
        applyLanguage(languageCode)
    }

    /** Clears a stale failure and re-reads each backend's status, without prompting. */
    fun onNeighborPickerOpened() {
        if (_neighborProbe.value is NeighborProbe.Failed) _neighborProbe.value = NeighborProbe.Idle
        refreshNeighborAvailability()
    }

    private fun refreshNeighborAvailability() {
        viewModelScope.launch {
            _neighborAvailability.value = PrivilegedNeighborSource.Mode.entries.associateWith {
                PrivilegedNeighborSource.availability(it)
            }
        }
    }

    /** Persists [mode] only once its backend has granted access. */
    fun setNeighborSource(mode: PrivilegedNeighborSource.Mode) {
        probeJob?.cancel()
        _neighborProbe.value = NeighborProbe.Running(mode)
        probeJob = viewModelScope.launch {
            val granted = PrivilegedNeighborSource.probe(mode)
            refreshNeighborAvailability()
            if (!granted) {
                _neighborProbe.value = NeighborProbe.Failed(mode)
                return@launch
            }
            prefs.edit { putString(PrivilegedNeighborSource.PREF_KEY, mode.name) }
            _neighborSource.value = mode
            _neighborProbe.value = NeighborProbe.Idle
        }
    }

    /** Abandons a pending access check, e.g. when the user keeps the current source instead. */
    fun cancelNeighborProbe() {
        probeJob?.cancel()
        _neighborProbe.value = NeighborProbe.Idle
    }

    fun supportsDynamicColors(): Boolean = ThemeManager.supportsDynamicColors()
}

/** Progress of the access check run when a MAC address source is chosen. */
sealed interface NeighborProbe {
    data object Idle : NeighborProbe
    data class Running(val mode: PrivilegedNeighborSource.Mode) : NeighborProbe
    data class Failed(val mode: PrivilegedNeighborSource.Mode) : NeighborProbe
}

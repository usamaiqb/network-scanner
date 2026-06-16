package com.networkscanner.app.ui

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.networkscanner.app.theme.ThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    fun supportsDynamicColors(): Boolean = ThemeManager.supportsDynamicColors()
}

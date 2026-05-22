package com.networkscanner.app.ui

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.networkscanner.app.theme.ThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val KEY_AUTO_SCAN = "auto_scan_on_start"
        const val KEY_LANGUAGE = "app_language"

        fun applyLanguage(context: Context, languageCode: String) {
            // Save preference
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
            
            // Apply using AppCompatDelegate for Android 13+ (API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val localeList = when (languageCode) {
                    "system" -> LocaleListCompat.getEmptyLocaleList()
                    else -> LocaleListCompat.forLanguageTags(languageCode)
                }
                AppCompatDelegate.setApplicationLocales(localeList)
            } else {
                // For older Android versions, use Configuration
                val locale = when (languageCode) {
                    "system" -> Locale.getDefault()
                    else -> Locale(languageCode)
                }
                Locale.setDefault(locale)
                
                val config = Configuration(context.resources.configuration)
                config.setLocale(locale)
                context.resources.updateConfiguration(config, context.resources.displayMetrics)
            }
        }

        fun getCurrentLanguage(context: Context): String {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getString(KEY_LANGUAGE, "system") ?: "system"
        }
    }

    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)

    val themeMode: StateFlow<ThemeManager.ThemeMode> = ThemeManager.themeModeFlow

    val dynamicColors: StateFlow<Boolean> = ThemeManager.dynamicColorsFlow

    private val _autoScan = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SCAN, true))
    val autoScan: StateFlow<Boolean> = _autoScan.asStateFlow()

    private val _language = MutableStateFlow(prefs.getString(KEY_LANGUAGE, "system") ?: "system")
    val language: StateFlow<String> = _language.asStateFlow()

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

    fun setLanguage(languageCode: String) {
        _language.value = languageCode
        applyLanguage(getApplication(), languageCode)
    }

    fun supportsDynamicColors(): Boolean = ThemeManager.supportsDynamicColors()
}

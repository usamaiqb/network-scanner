package com.networkscanner.app.theme

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages app theme settings including light/dark mode and dynamic colors.
 */
object ThemeManager {

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLORS = "dynamic_colors"

    enum class ThemeMode(val nightMode: Int) {
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES),
        SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        companion object {
            fun fromName(name: String?): ThemeMode {
                return entries.find { it.name == name } ?: SYSTEM
            }
        }
    }

    private val _themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    private val _dynamicColorsFlow = MutableStateFlow(supportsDynamicColors())
    val dynamicColorsFlow: StateFlow<Boolean> = _dynamicColorsFlow.asStateFlow()

    private fun getPrefs(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context)

    /**
     * Initialize theme settings on app startup.
     */
    fun initialize(context: Context) {
        val mode = getThemeMode(context)
        _themeModeFlow.value = mode
        _dynamicColorsFlow.value = isDynamicColorsEnabled(context)
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    /**
     * Get current theme mode.
     */
    fun getThemeMode(context: Context): ThemeMode {
        val name = getPrefs(context).getString(KEY_THEME_MODE, null)
        return ThemeMode.fromName(name)
    }

    /**
     * Set theme mode and apply it.
     */
    fun setThemeMode(context: Context, mode: ThemeMode) {
        getPrefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeModeFlow.value = mode
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    /**
     * Check if dynamic colors are enabled.
     */
    fun isDynamicColorsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DYNAMIC_COLORS, true)
    }

    /**
     * Enable or disable dynamic colors (Android 12+).
     */
    fun setDynamicColorsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DYNAMIC_COLORS, enabled).apply()
        _dynamicColorsFlow.value = enabled
    }

    /**
     * Check if device supports dynamic colors.
     */
    fun supportsDynamicColors(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}

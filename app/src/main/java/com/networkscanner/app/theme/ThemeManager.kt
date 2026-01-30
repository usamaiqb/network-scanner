package com.networkscanner.app.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors

/**
 * Manages app theme settings including light/dark mode and dynamic colors.
 */
object ThemeManager {

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLORS = "dynamic_colors"

    enum class ThemeMode(val value: Int) {
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES),
        SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        companion object {
            fun fromValue(value: Int): ThemeMode {
                return entries.find { it.value == value } ?: SYSTEM
            }
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    }

    /**
     * Initialize theme settings on app startup.
     */
    fun initialize(context: Context) {
        applyTheme(context)
        applyDynamicColors(context)
    }

    /**
     * Get current theme mode.
     */
    fun getThemeMode(context: Context): ThemeMode {
        val valueStr = getPrefs(context).getString(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.toString())
        val value = valueStr?.toIntOrNull() ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        return ThemeMode.fromValue(value)
    }

    /**
     * Set theme mode and apply it.
     */
    fun setThemeMode(context: Context, mode: ThemeMode) {
        getPrefs(context).edit().putString(KEY_THEME_MODE, mode.value.toString()).apply()
        AppCompatDelegate.setDefaultNightMode(mode.value)
    }

    /**
     * Toggle between light and dark themes.
     * If currently in system mode, switches to opposite of current system theme.
     */
    fun toggleTheme(context: Context) {
        val currentMode = getThemeMode(context)
        val newMode = when (currentMode) {
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.SYSTEM -> {
                // Toggle based on current actual state
                val isNightMode = context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                if (isNightMode) ThemeMode.LIGHT else ThemeMode.DARK
            }
        }
        setThemeMode(context, newMode)
    }

    /**
     * Cycle through themes: System -> Light -> Dark -> System
     */
    fun cycleTheme(context: Context) {
        val currentMode = getThemeMode(context)
        val newMode = when (currentMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        setThemeMode(context, newMode)
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
    }

    /**
     * Check if device supports dynamic colors.
     */
    fun supportsDynamicColors(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    private fun applyTheme(context: Context) {
        val mode = getThemeMode(context)
        AppCompatDelegate.setDefaultNightMode(mode.value)
    }

    private fun applyDynamicColors(context: Context) {
        if (supportsDynamicColors() && isDynamicColorsEnabled(context)) {
            DynamicColors.applyToActivitiesIfAvailable(context.applicationContext as android.app.Application)
        }
    }
}

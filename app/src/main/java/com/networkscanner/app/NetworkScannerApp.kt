package com.networkscanner.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.preference.PreferenceManager
import com.networkscanner.app.data.repository.CustomPortRepository
import com.networkscanner.app.data.repository.DeviceCustomizationRepository
import com.networkscanner.app.network.NetworkScanner
import com.networkscanner.app.theme.ThemeManager
import com.networkscanner.app.ui.SettingsViewModel
import java.util.Locale

/**
 * Application class for NetworkScanner app.
 */
class NetworkScannerApp : Application() {

    /** Shared NetworkScanner instance to avoid duplicate state across ViewModels. */
    val scanner: NetworkScanner by lazy { NetworkScanner(this) }

    /** Repositories backed by SharedPreferences */
    val deviceCustomizationRepository: DeviceCustomizationRepository by lazy {
        DeviceCustomizationRepository(this)
    }

    val customPortRepository: CustomPortRepository by lazy {
        CustomPortRepository(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize theme manager
        ThemeManager.initialize(this)
        
        // Apply saved language
        applySavedLanguage()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Reapply language when configuration changes
        applySavedLanguage()
    }

    private fun applySavedLanguage() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val languageCode = prefs.getString(SettingsViewModel.KEY_LANGUAGE, "system") ?: "system"
        
        if (languageCode != "system" && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // For older Android versions, manually set locale
            val locale = Locale(languageCode)
            Locale.setDefault(locale)
            val config = Configuration(resources.configuration)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(updateBaseContextLocale(base))
    }

    private fun updateBaseContextLocale(context: Context): Context {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val languageCode = prefs.getString(SettingsViewModel.KEY_LANGUAGE, "system") ?: "system"
        
        if (languageCode == "system") {
            return context
        }

        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }
}
